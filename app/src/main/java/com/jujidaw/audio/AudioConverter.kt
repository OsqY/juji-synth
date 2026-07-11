package com.jujidaw.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts any audio URI (MP3, AAC, FLAC, WAV, etc.) to a 16-bit PCM WAV file
 * that the C++ engine's [SampleBuffer.loadFromWav] can read.
 *
 * Uses [MediaExtractor] + [MediaCodec] to decode the source to PCM, then
 * writes a standard RIFF/WAVE header.
 */
object AudioConverter {
    /**
     * Decode [uri] to a 16-bit mono WAV file at [targetPath].
     * Returns true on success, false with logged reason on failure.
     */
    fun convertToWav(
        context: Context,
        uri: Uri,
        targetPath: String,
    ): Boolean {
        return try {
            val extractor =
                MediaExtractor().apply {
                    setDataSource(context, uri, null)
                }

            // Find the first audio track
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    break
                }
            }
            if (trackIndex < 0) {
                extractor.release()
                android.util.Log.e("JujiDaw", "AudioConverter: no audio track in URI")
                return false
            }

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/raw"

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val pcmData = mutableListOf<Byte>()
            var isInputEOS = false

            while (true) {
                if (!isInputEOS) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuf = decoder.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isInputEOS = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val outputBuf = decoder.getOutputBuffer(outputIndex)!!
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuf.position(bufferInfo.offset)
                    outputBuf.get(chunk)
                    pcmData.addAll(chunk.toList())

                    decoder.releaseOutputBuffer(outputIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            // Write WAV: 16-bit mono at whatever sample rate the decoder gave
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
            writeWavFile(pcmData.toByteArray(), sampleRate, channelCount, targetPath)
            android.util.Log.i(
                "JujiDaw",
                "AudioConverter: converted URI -> WAV ($sampleRate Hz, $channelCount ch, ${pcmData.size} bytes)",
            )

            true
        } catch (e: Exception) {
            android.util.Log.e("JujiDaw", "AudioConverter: error converting $uri", e)
            false
        }
    }

    /**
     * Write a 16-bit mono WAV. For stereo input, downmix to mono FIRST
     * so the RIFF/WAVE header fields match the actual data on disk.
     * Visible as [internal] so JVM unit tests can exercise it directly.
     */
    internal fun writeWavFile(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        path: String,
    ) {
        // ── Downmix to mono before writing the header ──
        val monoData: ByteArray =
            if (channels == 1) {
                pcmData
            } else {
                val frameSize = channels * 2 // 16-bit per channel
                val frameCount = pcmData.size / frameSize
                val downmixed = ByteArray(frameCount * 2)
                for (f in 0 until frameCount) {
                    val base = f * frameSize
                    var sum = 0L
                    for (ch in 0 until channels) {
                        val hi = pcmData[base + ch * 2 + 1].toInt()
                        val lo = pcmData[base + ch * 2].toInt() and 0xFF
                        sum += (hi shl 8) or lo
                    }
                    val mono =
                        (sum / channels)
                            .toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    downmixed[f * 2] = (mono and 0xFF).toByte()
                    downmixed[f * 2 + 1] = ((mono shr 8) and 0xFF).toByte()
                }
                downmixed
            }

        // ── Peak normalize to -1 dBFS (prevents imported samples sounding too quiet) ──
        val normalizedData = normalizeMonoInt16(monoData)

        // ── Write header (mono fields) then data ──
        FileOutputStream(path).use { out ->
            val dataSize = normalizedData.size
            val fileSize = 36 + dataSize
            val byteRate = sampleRate * 1 * 2 // 16-bit mono
            val blockAlign = 1 * 2 // mono

            val header =
                ByteBuffer.allocate(44).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    put("RIFF".toByteArray())
                    putInt(fileSize)
                    put("WAVE".toByteArray())
                    put("fmt ".toByteArray())
                    putInt(16) // subchunk1 size (PCM)
                    putShort(1) // audio format (PCM)
                    putShort(1) // always mono after downmix
                    putInt(sampleRate)
                    putInt(byteRate)
                    putShort(blockAlign.toShort())
                    putShort(16) // bits per sample
                    put("data".toByteArray())
                    putInt(dataSize)
                }
            out.write(header.array())
            out.write(normalizedData)
        }
    }

    /**
     * Scan mono 16-bit PCM data for peak amplitude and normalize to -1 dBFS.
     * Leaves silence (peak == 0) untouched.
     */
    private fun normalizeMonoInt16(monoData: ByteArray): ByteArray {
        val sampleCount = monoData.size / 2
        if (sampleCount == 0) return monoData

        // Find peak absolute int16 value
        var peakAbs = 0
        for (i in 0 until sampleCount) {
            val lo = monoData[i * 2].toInt() and 0xFF
            val hi = monoData[i * 2 + 1].toInt()
            val value = (hi shl 8) or lo
            val absValue = if (value >= 0) value else -value
            if (absValue > peakAbs) peakAbs = absValue
        }

        if (peakAbs == 0 || peakAbs >= 29127) return monoData // silence or already hot enough

        val gain = 29127f / peakAbs.toFloat() // target -1 dBFS
        val out = ByteArray(monoData.size)
        for (i in 0 until sampleCount) {
            val lo = monoData[i * 2].toInt() and 0xFF
            val hi = monoData[i * 2 + 1].toInt()
            val value = (hi shl 8) or lo
            val normalized = (value.toFloat() * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i * 2] = (normalized and 0xFF).toByte()
            out[i * 2 + 1] = ((normalized shr 8) and 0xFF).toByte()
        }
        return out
    }
}
