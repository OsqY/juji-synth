#include "SampleBuffer.h"
#include <cstring>
#include <fstream>
#include <algorithm>
#include <cmath>
#include <android/log.h>
#include "SoundTouch.h"

#define LOG_TAG "JujiSampleBuffer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

SampleBuffer::SampleBuffer(std::vector<float> samples, int sampleRate, int channels)
    : samples_(std::move(samples)), sampleRate_(sampleRate), channels_(channels) {}

std::shared_ptr<SampleBuffer> SampleBuffer::createTimeStretched(
    double tempoChangePercent,
    double pitchSemiTones,
    double rateChangePercent) const {

    if (!isLoaded() || channels_ <= 0 || channels_ > 2) {
        return nullptr;
    }

    soundtouch::SoundTouch st;
    st.setSampleRate(static_cast<uint>(sampleRate_));
    st.setChannels(static_cast<uint>(channels_));
    st.setTempoChange(tempoChangePercent);
    st.setPitchSemiTones(pitchSemiTones);
    st.setRateChange(rateChangePercent);

    const size_t inputFrames = getNumFrames();

    // Feed input in chunks to avoid large temporary allocations.
    constexpr size_t chunkFrames = 4096;
    size_t frameOffset = 0;
    while (frameOffset < inputFrames) {
        size_t frames = std::min(chunkFrames, inputFrames - frameOffset);
        st.putSamples(samples_.data() + frameOffset * channels_, static_cast<uint>(frames));
        frameOffset += frames;
    }

    st.flush();

    // Estimate output size. SoundTouch ratio gives input/output duration ratio.
    double ratio = st.getInputOutputSampleRatio();
    if (ratio <= 0.0) ratio = 1.0;
    size_t estimatedOutputFrames = static_cast<size_t>(static_cast<double>(inputFrames) / ratio + 0.5);
    estimatedOutputFrames = std::max(estimatedOutputFrames, inputFrames); // safety

    std::vector<float> output;
    output.reserve(estimatedOutputFrames * channels_);

    std::vector<float> receiveBuffer(chunkFrames * channels_);
    uint received = 0;
    do {
        received = st.receiveSamples(receiveBuffer.data(), static_cast<uint>(chunkFrames));
        if (received > 0) {
            output.insert(output.end(), receiveBuffer.begin(), receiveBuffer.begin() + received * channels_);
        }
    } while (received > 0);

    if (output.empty()) {
        return nullptr;
    }

    return std::make_shared<SampleBuffer>(std::move(output), sampleRate_, channels_);
}

bool SampleBuffer::loadFromWav(const char* path) {
    std::ifstream file(path, std::ios::binary);
    if (!file.is_open()) {
        LOGE("Failed to open WAV file: %s", path);
        return false;
    }

    struct WavHeader {
        char riff[4];
        uint32_t fileSize;
        char wave[4];
    } header;

    file.read(reinterpret_cast<char*>(&header), sizeof(header));
    if (std::strncmp(header.riff, "RIFF", 4) != 0 || std::strncmp(header.wave, "WAVE", 4) != 0) {
        LOGE("Invalid WAV header for: %s", path);
        return false;
    }

    int formatChannels = 1;
    int formatSampleRate = 44100;
    int bitsPerSample = 16;
    int audioFormat = 1; // 1 = PCM
    bool isFloat = false;

    while (!file.eof()) {
        char chunkId[4];
        uint32_t chunkSize;
        file.read(chunkId, 4);
        file.read(reinterpret_cast<char*>(&chunkSize), 4);
        if (file.eof()) break;

        if (std::strncmp(chunkId, "fmt ", 4) == 0) {
            uint16_t af;
            file.read(reinterpret_cast<char*>(&af), 2);
            audioFormat = af;

            if (audioFormat != 1 && audioFormat != 3 && audioFormat != 0xFFFE) {
                LOGE("Unsupported WAV format tag %d in: %s", audioFormat, path);
                return false;
            }

            uint16_t numChannels;
            file.read(reinterpret_cast<char*>(&numChannels), 2);
            formatChannels = numChannels;

            uint32_t sampleRate;
            file.read(reinterpret_cast<char*>(&sampleRate), 4);
            formatSampleRate = static_cast<int>(sampleRate);

            uint32_t byteRate;
            file.read(reinterpret_cast<char*>(&byteRate), 4);

            uint16_t blockAlign;
            file.read(reinterpret_cast<char*>(&blockAlign), 2);

            uint16_t bps;
            file.read(reinterpret_cast<char*>(&bps), 2);
            bitsPerSample = bps;
            isFloat = (audioFormat == 3) || (bps == 32 && audioFormat == 0xFFFE);

            // WAVEFORMATEXTENSIBLE: read sub-format GUID to determine float vs PCM.
            if (audioFormat == 0xFFFE && chunkSize >= 40) {
                uint16_t validBitsPerSample;
                uint32_t channelMask;
                char subFormat[16];
                file.read(reinterpret_cast<char*>(&validBitsPerSample), 2);
                file.read(reinterpret_cast<char*>(&channelMask), 4);
                file.read(subFormat, 16);
                // KSDATAFORMAT_SUBTYPE_IEEE_FLOAT: 00000003-0000-0010-8000-00AA00389B71
                // KSDATAFORMAT_SUBTYPE_PCM:       00000001-0000-0010-8000-00AA00389B71
                if (subFormat[0] == (char)0x03 && subFormat[1] == (char)0x00 && subFormat[2] == (char)0x00 && subFormat[3] == (char)0x00 &&
                    subFormat[4] == (char)0x00 && subFormat[5] == (char)0x00 && subFormat[6] == (char)0x10 && subFormat[7] == (char)0x00 &&
                    subFormat[8] == (char)0x80 && subFormat[9] == (char)0x00 && subFormat[10] == (char)0x00 && subFormat[11] == (char)0xAA &&
                    subFormat[12] == (char)0x00 && subFormat[13] == (char)0x38 && subFormat[14] == (char)0x9B && subFormat[15] == (char)0x71) {
                    isFloat = true;
                } else {
                    isFloat = false;
                }
            }

            if (chunkSize > 16) {
                file.seekg(chunkSize - 16, std::ios::cur);
            }
        } else if (std::strncmp(chunkId, "data", 4) == 0) {
            sampleRate_ = formatSampleRate;
            channels_ = formatChannels;

            int numBytes = static_cast<int>(chunkSize);
            int numSamples = numBytes / (bitsPerSample / 8);
            samples_.clear();
            samples_.reserve(numSamples);

            if (isFloat && bitsPerSample == 32) {
                std::vector<float> raw(numSamples);
                file.read(reinterpret_cast<char*>(raw.data()), numBytes);
                size_t read = file.gcount();
                size_t frames = read / (sizeof(float) * channels_);
                samples_.resize(frames * channels_);
                for (size_t i = 0; i < frames * channels_; i++) {
                    samples_[i] = std::clamp(raw[i], -1.0f, 1.0f);
                }
                return true;
                } else if (bitsPerSample == 16) {
                    std::vector<int16_t> raw(numSamples);
                    file.read(reinterpret_cast<char*>(raw.data()), numBytes);
                    size_t read = file.gcount();
                    if (read != numBytes) {
                        LOGE("WAV data chunk size mismatch: header says %d bytes, "
                             "but only %zu bytes on disk — audio may be truncated. "
                             "Source: %s", numBytes, read, path);
                    }
                    size_t frames = read / (sizeof(int16_t) * channels_);
                    samples_.resize(frames * channels_);
                    for (size_t i = 0; i < frames * channels_; i++) {
                        samples_[i] = raw[i] / 32768.0f;
                    }
                    return true;
            } else if (bitsPerSample == 24) {
                std::vector<uint8_t> raw(numBytes);
                file.read(reinterpret_cast<char*>(raw.data()), numBytes);
                size_t read = file.gcount();
                size_t frames = read / (3 * channels_);
                samples_.resize(frames * channels_);
                for (size_t i = 0; i < frames * channels_; i++) {
                    int32_t value = (raw[i * 3] | (raw[i * 3 + 1] << 8) | (raw[i * 3 + 2] << 16));
                    if (value & 0x800000) value |= 0xFF000000;
                    samples_[i] = value / 8388608.0f;
                }
                return true;
            } else if (bitsPerSample == 32) {
                std::vector<int32_t> raw(numSamples);
                file.read(reinterpret_cast<char*>(raw.data()), numBytes);
                size_t read = file.gcount();
                size_t frames = read / (sizeof(int32_t) * channels_);
                samples_.resize(frames * channels_);
                for (size_t i = 0; i < frames * channels_; i++) {
                    samples_[i] = raw[i] / 2147483648.0f;
                }
                return true;
            } else {
                LOGE("Unsupported bits per sample %d in WAV: %s", bitsPerSample, path);
                return false;
            }
        } else {
            file.seekg(chunkSize, std::ios::cur);
        }
    }

    LOGE("Missing 'data' chunk in WAV: %s", path);
    return false;
}

int SampleBuffer::getNumFrames() const {
    return channels_ > 0 ? static_cast<int>(samples_.size()) / channels_ : 0;
}

float SampleBuffer::getSample(int frame, int channel) const {
    if (frame < 0 || frame >= getNumFrames() || channel < 0 || channel >= channels_) {
        return 0.0f;
    }
    return samples_[frame * channels_ + channel];
}

float SampleBuffer::getSampleInterpolated(float frame, int channel) const {
    int frameInt = static_cast<int>(frame);
    float frac = frame - frameInt;
    float s0 = getSample(frameInt, channel);
    float s1 = getSample(frameInt + 1, channel);
    return s0 + frac * (s1 - s0);
}
