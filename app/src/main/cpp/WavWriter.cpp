#include "WavWriter.h"
#include "SampleBuffer.h"
#include <fstream>
#include <cstring>
#include <cstdint>
#include <algorithm>

namespace {

inline void writeUint16LE(std::ostream& os, uint16_t v) {
    char b[2] = { static_cast<char>(v & 0xFF), static_cast<char>((v >> 8) & 0xFF) };
    os.write(b, 2);
}

inline void writeUint32LE(std::ostream& os, uint32_t v) {
    char b[4] = {
        static_cast<char>(v & 0xFF),
        static_cast<char>((v >> 8) & 0xFF),
        static_cast<char>((v >> 16) & 0xFF),
        static_cast<char>((v >> 24) & 0xFF)
    };
    os.write(b, 4);
}

inline void writeFourCC(std::ostream& os, const char (&four)[5]) {
    os.write(four, 4);
}

inline float clampSample(float v) {
    if (v > 1.0f) return 1.0f;
    if (v < -1.0f) return -1.0f;
    return v;
}

} // namespace

bool WavWriter::write(const SampleBuffer& buffer, const char* path) {
    if (path == nullptr || !buffer.isLoaded()) return false;

    std::ofstream file(path, std::ios::binary | std::ios::trunc);
    if (!file.is_open()) return false;

    const int sampleRate = buffer.getSampleRate();
    const int channels = buffer.getChannels();
    const int numFrames = buffer.getNumFrames();
    if (channels <= 0 || channels > 2) return false;

    const uint16_t bitsPerSample = 16;
    const uint16_t blockAlign = static_cast<uint16_t>(channels * bitsPerSample / 8);
    const uint32_t byteRate = static_cast<uint32_t>(sampleRate) * blockAlign;
    const uint32_t dataBytes = static_cast<uint32_t>(numFrames) * blockAlign;
    const uint32_t riffSize = 36 + dataBytes;

    writeFourCC(file, "RIFF");
    writeUint32LE(file, riffSize);
    writeFourCC(file, "WAVE");

    writeFourCC(file, "fmt ");
    writeUint32LE(file, 16);              // fmt chunk size (PCM)
    writeUint16LE(file, 1);               // PCM format
    writeUint16LE(file, static_cast<uint16_t>(channels));
    writeUint32LE(file, static_cast<uint32_t>(sampleRate));
    writeUint32LE(file, byteRate);
    writeUint16LE(file, blockAlign);
    writeUint16LE(file, bitsPerSample);

    writeFourCC(file, "data");
    writeUint32LE(file, dataBytes);

    // Write interleaved samples
    const auto& samples = buffer.getSamples();
    const size_t totalSamples = static_cast<size_t>(numFrames) * channels;
    for (size_t i = 0; i < totalSamples; i++) {
        float v = clampSample(samples[i]);
        int16_t s = static_cast<int16_t>(v * 32767.0f);
        writeUint16LE(file, static_cast<uint16_t>(s));
    }

    file.flush();
    return file.good();
}
