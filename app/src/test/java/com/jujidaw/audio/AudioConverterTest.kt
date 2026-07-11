package com.jujidaw.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JVM unit tests for [AudioConverter.writeWavFile].
 *
 * Verifies the WAV header fields match the actual data on disk,
 * which is the root cause of the imported-samples-sound-wrong bug
 * (stereo header written before downmix).
 */
class AudioConverterTest {
    private val tmpDir = File(System.getProperty("java.io.tmpdir"), "wav-test")

    /** Build a synthetic 16-bit stereo PCM byte array (left/right interleaved). */
    private fun stereoPcm(frameCount: Int): ByteArray {
        val buf = ByteArray(frameCount * 4) // 2 channels * 2 bytes
        for (f in 0 until frameCount) {
            val base = f * 4
            // Left channel = 0x4000
            buf[base] = 0x00.toByte()
            buf[base + 1] = 0x40.toByte()
            // Right channel = 0xC000 (-16384)
            buf[base + 2] = 0x00.toByte()
            buf[base + 3] = 0xC0.toByte()
        }
        return buf
    }

    @Test
    fun stereoInput_wavHeaderMatchesMonoData() {
        tmpDir.mkdirs()
        val outFile = File(tmpDir, "stereo-test.wav")
        val pcmData = stereoPcm(1000) // 1000 frames * 4 bytes = 4000 bytes stereo

        AudioConverter.writeWavFile(pcmData, sampleRate = 44100, channels = 2, path = outFile.absolutePath)

        assertTrue("WAV file should exist", outFile.exists())
        assertTrue("WAV file should be non-empty", outFile.length() > 44)

        // Read entire file and parse with little-endian ByteBuffer
        val bytes = outFile.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(0x46464952, buf.int) // "RIFF"
        val fileSize = buf.int
        assertEquals(0x45564157, buf.int) // "WAVE"

        assertEquals(0x20746D66, buf.int) // "fmt "
        val fmtSize = buf.int
        assertEquals(16, fmtSize)

        val audioFormat = buf.short.toInt() and 0xFFFF
        assertEquals("PCM format", 1, audioFormat)

        val channels = buf.short.toInt() and 0xFFFF
        assertEquals("Channel count should be 1 (downmixed from stereo)", 1, channels)

        val sampleRate = buf.int
        assertEquals("Sample rate should be preserved", 44100, sampleRate)

        val byteRate = buf.int
        assertEquals("byteRate should be sampleRate * 1 * 2", 44100 * 2, byteRate)

        val blockAlign = buf.short.toInt() and 0xFFFF
        assertEquals("blockAlign should be 1 * 2 (mono)", 2, blockAlign)

        val bitsPerSample = buf.short.toInt() and 0xFFFF
        assertEquals(16, bitsPerSample)

        assertEquals(0x61746164, buf.int) // "data"

        val dataSize = buf.int
        val expectedMonoSize = 1000 * 2 // 1000 frames * 2 bytes mono
        assertEquals("dataSize should match mono byte count", expectedMonoSize, dataSize)

        // Actual data on disk should match dataSize
        val actualDataBytes = bytes.size - buf.position()
        assertEquals("Actual bytes on disk should match dataSize", dataSize, actualDataBytes)

        outFile.delete()
    }

    @Test
    fun monoInput_unchanged() {
        tmpDir.mkdirs()
        val outFile = File(tmpDir, "mono-test.wav")
        val pcmData = ByteArray(200) // 100 mono frames * 2 bytes

        AudioConverter.writeWavFile(pcmData, sampleRate = 48000, channels = 1, path = outFile.absolutePath)

        assertTrue("WAV file should exist", outFile.exists())

        val bytes = outFile.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        buf.int
        buf.int
        buf.int // RIFF + fileSize + WAVE
        buf.int
        buf.int // "fmt " + fmtSize
        buf.short // audioFormat (skip)

        val channels = buf.short.toInt() and 0xFFFF
        assertEquals("Mono stays mono", 1, channels)

        val sampleRate = buf.int
        assertEquals(48000, sampleRate)

        // Skip byteRate(4) + blockAlign(2) + bitsPerSample(2) + "data"(4) = 12 bytes
        buf.position(buf.position() + 12)
        val dataSize = buf.int
        assertEquals("Mono data size unchanged", 200, dataSize)

        outFile.delete()
    }

    @Test
    fun stereoDownmix_averagesLAndR() {
        tmpDir.mkdirs()
        val outFile = File(tmpDir, "downmix-test.wav")
        // 2 frames of stereo: frame0 L=0x4000 R=0x4000, frame1 L=-0x4000 R=-0x4000
        val pcmData = ByteArray(8)
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x4000)
            putShort(0x4000) // frame 0
            putShort((-0x4000).toShort())
            putShort((-0x4000).toShort()) // frame 1
        }

        AudioConverter.writeWavFile(pcmData, sampleRate = 44100, channels = 2, path = outFile.absolutePath)

        // Read data portion (skip 44-byte header)
        val data = ByteArray(outFile.length().toInt() - 44)
        outFile.inputStream().use { input ->
            input.skip(44)
            input.read(data, 0, data.size)
        }

        // After downmix + peak normalization to -1 dBFS (target peak = 29127):
        // Frame 0: avg(0x4000,0x4000)=0x4000 → normalized to near 29127
        // Frame 1: avg(-0x4000,-0x4000)=-0x4000 → normalized to near -29127
        val frame0 =
            ByteBuffer
                .wrap(data, 0, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .short
                .toInt()
        val frame1 =
            ByteBuffer
                .wrap(data, 2, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .short
                .toInt()
        assertTrue("Frame 0 should be normalized near -1 dBFS ($frame0)", frame0 >= 29000 && frame0 <= 30000)
        assertTrue("Frame 1 should be normalized near -1 dBFS ($frame1)", frame1 <= -29000 && frame1 >= -30000)

        outFile.delete()
    }
}
