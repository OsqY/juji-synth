#ifndef JUJIDAW_WAV_WRITER_H
#define JUJIDAW_WAV_WRITER_H

class SampleBuffer;

/**
 * Minimal 16-bit PCM WAV writer.
 *
 * Used to export pad samples and recordings to disk. Writes either mono
 * (single channel) or interleaved stereo (two channels) WAV files. Sample
 * rate is taken from the buffer.
 */
class WavWriter {
public:
    /**
     * Write the given buffer to a 16-bit PCM WAV file.
     * @return true on success.
     */
    static bool write(const SampleBuffer& buffer, const char* path);
};

#endif // JUJIDAW_WAV_WRITER_H
