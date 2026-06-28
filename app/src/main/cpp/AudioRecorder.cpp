#include "AudioRecorder.h"
#include "SampleBuffer.h"
#include "WavWriter.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG_REC "JujiDawRec"
#define LOGI_REC(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG_REC, __VA_ARGS__)
#define LOGE_REC(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG_REC, __VA_ARGS__)

AudioRecorder::AudioRecorder() = default;

AudioRecorder::~AudioRecorder() {
    stop();
}

bool AudioRecorder::start(int sampleRate) {
    if (recording_.load(std::memory_order_acquire)) {
        LOGE_REC("start() called while already recording");
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(bufferMutex_);
        samples_.clear();
    }
    sampleRate_ = sampleRate > 0 ? sampleRate : 48000;
    stopRequested_.store(false, std::memory_order_release);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(1); // Mono input
    builder.setSampleRate(sampleRate_);
    builder.setFramesPerDataCallback(256);
    builder.setCallback(this);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        LOGE_REC("Failed to open input stream: %s", oboe::convertToText(result));
        stream_.reset();
        return false;
    }

    int actualChannels = stream_->getChannelCount();
    channels_ = actualChannels > 0 ? actualChannels : 1;
    int actualRate = stream_->getSampleRate();
    if (actualRate > 0) sampleRate_ = actualRate;

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE_REC("Failed to start input stream: %s", oboe::convertToText(result));
        stream_->close();
        stream_.reset();
        return false;
    }

    recording_.store(true, std::memory_order_release);
    samplesRecorded_.store(0, std::memory_order_release);
    LOGI_REC("Recording started at %d Hz, %d channels", sampleRate_, channels_);
    return true;
}

void AudioRecorder::stop() {
    if (!recording_.load(std::memory_order_acquire)) return;
    stopRequested_.store(true, std::memory_order_release);

    if (stream_) {
        stream_->requestStop();
        // Drain pending callbacks
        stream_->close();
        stream_.reset();
    }

    recording_.store(false, std::memory_order_release);
    LOGI_REC("Recording stopped, %zu samples captured", samples_.size());
}

void AudioRecorder::clear() {
    std::lock_guard<std::mutex> lock(bufferMutex_);
    samples_.clear();
}

void AudioRecorder::setPunchRange(int64_t inSample, int64_t outSample) {
    punchInSample_.store(inSample, std::memory_order_release);
    punchOutSample_.store(outSample, std::memory_order_release);
}

bool AudioRecorder::writeToWav(const std::string& path) {
    std::lock_guard<std::mutex> lock(bufferMutex_);
    if (samples_.empty()) return false;
    auto buffer = std::make_shared<SampleBuffer>(
        std::vector<float>(samples_), sampleRate_, channels_);
    return WavWriter::write(*buffer, path.c_str());
}

std::shared_ptr<SampleBuffer> AudioRecorder::takeBuffer() {
    std::vector<float> copy;
    int rate;
    {
        std::lock_guard<std::mutex> lock(bufferMutex_);
        copy = std::move(samples_);
        samples_.clear();
        rate = sampleRate_;
    }
    if (copy.empty()) {
        return nullptr;
    }
    return std::make_shared<SampleBuffer>(std::move(copy), rate, 1);
}

oboe::DataCallbackResult AudioRecorder::onAudioReady(
    oboe::AudioStream* /*stream*/,
    void* audioData,
    int32_t numFrames) {

    if (stopRequested_.load(std::memory_order_acquire)) {
        return oboe::DataCallbackResult::Stop;
    }

    const float* input = static_cast<const float*>(audioData);
    std::lock_guard<std::mutex> lock(bufferMutex_);

    bool punchEnabled = punchEnabled_.load(std::memory_order_acquire);
    int64_t punchIn = punchInSample_.load(std::memory_order_acquire);
    int64_t punchOut = punchOutSample_.load(std::memory_order_acquire);

    size_t oldSize = samples_.size();
    samples_.resize(oldSize + static_cast<size_t>(numFrames));

    for (int i = 0; i < numFrames; i++) {
        int64_t globalSample = samplesRecorded_.fetch_add(1, std::memory_order_relaxed);
        float sample = input[i * channels_];
        bool insidePunch = !punchEnabled || (globalSample >= punchIn && globalSample < punchOut);
        samples_[oldSize + i] = insidePunch ? sample : 0.0f;
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioRecorder::onErrorAfterClose(oboe::AudioStream* /*stream*/,
                                      oboe::Result error) {
    LOGE_REC("Input stream error: %s", oboe::convertToText(error));
    recording_.store(false, std::memory_order_release);
}
