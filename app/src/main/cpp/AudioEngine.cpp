#include "AudioEngine.h"
#include "SynthEngine.h"
#include "WavWriter.h"
#include "SampleBuffer.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <vector>

#define LOG_TAG "JujiDaw"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {
    for (int i = 0; i < SCOPE_SIZE; i++) {
        scopeBuffer_[i] = 0.0f;
    }
}

void AudioEngine::init(double sampleRate) {
    sampleRate_ = sampleRate;

    // Initialize mixer graph
    for (auto& ch : channels_) {
        ch.init(sampleRate);
        ch.setClipPlayer(nullptr);
    }
    for (auto& bus : buses_) {
        bus.init(sampleRate);
    }
    masterBus_.init(sampleRate);

    // Instruments render into AudioEngine's fixed source buses. Keeping them
    // detached from individual channels lets a timeline event route to any
    // mixer row without processing the instrument more than once per sample.
    synthInstrument_ = std::make_unique<SynthInstrument>();
    synthInstrument_->init(sampleRate);

    // Channel 1 hosts the sampler; pair it with the AudioEngine so
    // synth-pad mode can route to per-pad synths via the pool.
    sampler_ = std::make_unique<SamplerInstrument>();
    sampler_->init(sampleRate);
    sampler_->setAudioEngine(this);

    // Clear the per-pad synth pool; instances are created only when a pad
    // enters synth mode or receives a pad-synth parameter update.
    for (auto& synth : synthForPad_) {
        synth.reset();
    }
    for (auto& synth : publishedPadSynths_) {
        synth.store(nullptr, std::memory_order_relaxed);
    }

    // Start background time-stretch worker
    timeStretchWorker_.setSampler(sampler_.get());
    timeStretchWorker_.start();

    // Initialize the DAW transport/sample clock.
    transport_.init(static_cast<int>(sampleRate));

    for (auto& player : audioClipPlayers_) {
        player = std::make_unique<AudioClipPlayer>();
        player->init(sampleRate_);
    }

    // Precompute master peak decay factor for ~10ms falloff
    masterPeakDecayFactor_ = std::exp(-1.0f / static_cast<float>(sampleRate * 0.01));

    LOGI("AudioEngine initialized at %f Hz", sampleRate);
}

void AudioEngine::panicAllAudio() {
    if (synthInstrument_) synthInstrument_->panic();
    if (sampler_) sampler_->panic();
    for (auto& synth : synthForPad_) {
        if (synth) synth->panic();
    }
    for (int track = 0; track < MAX_TRACKS; ++track) {
        if (audioClipPlayers_[track]) audioClipPlayers_[track]->stop();
        channels_[track].setClipPlayer(nullptr);
    }
}

int AudioEngine::processAudio(float* outputBuffer, int numFrames) {
    processMixerQueue();

    // Consume perform FX trigger
    int fxType = performFxTrigger_.exchange(0, std::memory_order_acq_rel);
    if (fxType != 0) {
        switch (fxType) {
            case 1: // Tape stop
                tapeStopActive_ = true;
                tapeStopRamp_ = 1.0f;
                break;
            case 2: // Bitcrush toggle
                bitcrushEnabled_ = !bitcrushEnabled_;
                break;
            case 3: // Filter sweep
                filterSweepActive_ = true;
                filterSweepCutoff_ = 1.0f;
                filterSweepTarget_ = 0.02f;
                filterSweepCounter_ = 0;
                performFilter_.init(sampleRate_);
                performFilter_.setResonance(0.7);
                break;
        }
    }

    // Advance transport clock. If a loop wrap occurred, discard stale events
    // that were scheduled before the wrap.
    int64_t sampleBeforeAdvance = transport_.getCurrentSample();
    if (transport_.advance(numFrames)) {
        eventQueue_.clear();
        transport_.clearPendingEvents();
    }
    transport_.drainEvents(eventQueue_);
    transport_.firePendingEvents(*this, sampleBeforeAdvance, numFrames);

    // Events can activate a previously unused row during this callback.
    int activeTracks = activeTrackCount_.load(std::memory_order_acquire);

    // Apply per-block automation on the synth once per buffer.
    if (synthInstrument_) {
        synthInstrument_->processBlockAutomation();
    }

    // Apply per-block automation on active per-pad synths.
    for (int p = 0; p < PAD_SYNTH_COUNT; p++) {
        if (auto* synth = getExistingPadSynth(p)) {
            synth->processBlockAutomation();
        }
    }

    for (int i = 0; i < numFrames; i++) {
        instrumentSources_.fill(0.0f);
        if (synthInstrument_) {
            synthInstrument_->processToTracks(instrumentSources_.data(), activeTracks);
        }
        if (sampler_) {
            sampler_->processToTracks(instrumentSources_.data(), activeTracks);
        }

        // Determine if any channel is soloed
        bool anySolo = false;
        for (int t = 0; t < activeTracks; t++) {
            if (channels_[t].isSolo()) {
                anySolo = true;
                break;
            }
        }

        // Sum channel outputs and sends
        float mainSum = 0.0f;
        float busASum = 0.0f;
        float busBSum = 0.0f;

        for (int t = 0; t < activeTracks; t++) {
            float sendA = 0.0f;
            float sendB = 0.0f;
            float out = channels_[t].processInput(instrumentSources_[t], sendA, sendB);

            bool soloed = channels_[t].isSolo();
            bool muted = channels_[t].isMute();

            if (anySolo && !soloed) {
                out = 0.0f;
                sendA = 0.0f;
                sendB = 0.0f;
            } else if (muted) {
                out = 0.0f;
            }

            mainSum += out;
            busASum += sendA;
            busBSum += sendB;
        }

        float busAOut = buses_[0].process(busASum);
        float busBOut = buses_[1].process(busBSum);

        float masterInput = mainSum + busAOut + busBOut;
        float sample = masterBus_.process(masterInput);

        sample = std::clamp(sample, -1.0f, 1.0f);

        // ---- Perform FX processing ----
        if (tapeStopActive_) {
            tapeStopRamp_ *= 0.995f; // rapid fade over ~200ms at 48kHz
            sample *= tapeStopRamp_;
            if (tapeStopRamp_ < 0.001f) {
                sample = 0.0f;
                tapeStopActive_ = false;
                tapeStopRamp_ = 1.0f;
            }
        }
        if (bitcrushEnabled_) {
            // Simple sample-and-hold bitcrush
            bitcrushSampleHold_++;
            if (bitcrushSampleHold_ >= 8) {
                bitcrushSampleHold_ = 0;
                bitcrushPhase_ = static_cast<int>(sample * 127.0f);
            }
            sample = static_cast<float>(bitcrushPhase_) / 127.0f;
        }
        if (filterSweepActive_) {
            filterSweepCounter_++;
            // Sweep cutoff from 1.0 to 0.02 over ~500ms
            filterSweepCutoff_ += (filterSweepTarget_ - filterSweepCutoff_) * 0.01f;
            performFilter_.setCutoff(filterSweepCutoff_);
            sample = performFilter_.process(sample);
            if (filterSweepCounter_ > 8000) { // ~166ms at 48kHz
                filterSweepActive_ = false;
                filterSweepCutoff_ = 1.0f;
            }
        }

        outputBuffer[i] = sample;

        // Capture for offline render (lock-free single-producer via atomic guard)
        if (offlineRenderActive_.load(std::memory_order_acquire)) {
            // Only the audio thread pushes; stopOfflineRender is called after
            // the stream is stopped, so no concurrent pop/write occurs.
            offlineRenderBuffer_.push_back(sample);
        }

        // Master peak with immediate attack / ~10ms exponential decay
        float absSample = std::abs(sample);
        float decay = masterPeak_.load(std::memory_order_relaxed) * masterPeakDecayFactor_;
        masterPeak_.store(std::max(absSample, decay), std::memory_order_relaxed);

        scopeBuffer_[scopeWriteIndex_] = sample;
        scopeWriteIndex_ = (scopeWriteIndex_ + 1) % SCOPE_SIZE;
    }

    // Sync voice bookkeeping on the synth instrument.
    if (synthInstrument_) {
        synthInstrument_->syncVoiceActiveStates();
    }

    // Sync per-pad synth voice states.
    for (int p = 0; p < PAD_SYNTH_COUNT; p++) {
        if (auto* synth = getExistingPadSynth(p)) {
            synth->syncVoiceActiveStates();
        }
    }

    return numFrames;
}

void AudioEngine::pushMixerCommand(const MixerCommand& cmd) {
    int tail = mixerQueueTail_.load(std::memory_order_relaxed);
    int nextTail = (tail + 1) % MIXER_QUEUE_SIZE;
    if (nextTail != mixerQueueHead_.load(std::memory_order_acquire)) {
        mixerQueue_[tail] = cmd;
        mixerQueueTail_.store(nextTail, std::memory_order_release);
    }
}

void AudioEngine::ensureTrackActive(int trackIndex) {
    if (trackIndex < 0 || trackIndex >= MAX_TRACKS) return;
    int requiredCount = trackIndex + 1;
    int current = activeTrackCount_.load(std::memory_order_relaxed);
    while (current < requiredCount &&
           !activeTrackCount_.compare_exchange_weak(
               current, requiredCount,
               std::memory_order_release,
               std::memory_order_relaxed)) {
    }
}

void AudioEngine::processMixerQueue() {
    while (true) {
        int head = mixerQueueHead_.load(std::memory_order_acquire);
        int tail = mixerQueueTail_.load(std::memory_order_acquire);
        if (head == tail) break;

        const auto& cmd = mixerQueue_[head];
        if (cmd.type == MixerCommandType::SetTrackCount) {
            int v = static_cast<int>(cmd.value);
            if (v < 1) v = 1;
            if (v > MAX_TRACKS) v = MAX_TRACKS;
            activeTrackCount_.store(v, std::memory_order_release);
        } else if (cmd.type == MixerCommandType::SetBusFader) {
            if (cmd.track < NUM_BUSES) {
                buses_[cmd.track].applyCommand(cmd);
            }
        } else if (cmd.type == MixerCommandType::SetBusInsertBypass ||
                   cmd.type == MixerCommandType::AddBusInsertEffect ||
                   cmd.type == MixerCommandType::RemoveBusInsertEffect ||
                   cmd.type == MixerCommandType::SetBusInsertParam) {
            if (cmd.track < NUM_BUSES) {
                buses_[cmd.track].applyCommand(cmd);
            }
        } else if (cmd.type == MixerCommandType::SetMasterFader) {
            masterBus_.applyCommand(cmd);
        } else if (cmd.type == MixerCommandType::SetInsertBypass ||
                   cmd.type == MixerCommandType::AddInsertEffect ||
                   cmd.type == MixerCommandType::RemoveInsertEffect ||
                   cmd.type == MixerCommandType::SetInsertParam) {
            // Route insert commands to channels when track is valid,
            // otherwise fall back to master bus.
            if (cmd.track < MAX_TRACKS) {
                channels_[cmd.track].applyCommand(cmd);
            } else {
                masterBus_.applyCommand(cmd);
            }
        } else {
            if (cmd.track < MAX_TRACKS) {
                channels_[cmd.track].applyCommand(cmd);
            }
        }
        mixerQueueHead_.store((head + 1) % MIXER_QUEUE_SIZE, std::memory_order_release);
    }
}

void AudioEngine::getWaveform(float* out, int maxSize) const {
    int n = std::min(maxSize, SCOPE_SIZE);
    int start = scopeWriteIndex_;
    for (int i = 0; i < n; i++) {
        out[i] = scopeBuffer_[(start + i) % SCOPE_SIZE];
    }
}

bool AudioEngine::loadAudioClip(const std::string& clipId, const std::string& path) {
    auto buffer = std::make_shared<SampleBuffer>();
    if (!buffer->loadFromWav(path.c_str())) {
        LOGE("Failed to load audio clip %s from %s", clipId.c_str(), path.c_str());
        return false;
    }
    std::lock_guard<std::mutex> lock(audioClipsMutex_);
    audioClips_[clipId] = std::move(buffer);
    return true;
}

void AudioEngine::unloadAudioClip(const std::string& clipId) {
    std::lock_guard<std::mutex> lock(audioClipsMutex_);
    audioClips_.erase(clipId);
}

bool AudioEngine::startAudioClip(const std::string& clipId, int trackIndex, int startOffsetInBuffer) {
    if (trackIndex < 0 || trackIndex >= MAX_TRACKS) return false;
    std::shared_ptr<SampleBuffer> buffer;
    {
        std::lock_guard<std::mutex> lock(audioClipsMutex_);
        auto it = audioClips_.find(clipId);
        if (it == audioClips_.end() || !it->second || !it->second->isLoaded()) return false;
        buffer = it->second;
    }

    auto* player = audioClipPlayers_[trackIndex].get();
    player->setBuffer(std::move(buffer));
    player->start(startOffsetInBuffer);
    channels_[trackIndex].setClipPlayer(player);

    // Update active track count atomically so the audio thread sees the
    // new value on its next acquire-load. A mixer command could also be
    // used for consistency with other mixer state, but a direct atomic
    // store is simpler and avoids multi-producer concerns on the queue.
    ensureTrackActive(trackIndex);
    return true;
}

void AudioEngine::stopAudioClip(int trackIndex) {
    if (trackIndex < 0 || trackIndex >= MAX_TRACKS) return;
    if (audioClipPlayers_[trackIndex]) {
        audioClipPlayers_[trackIndex]->stop();
    }
    channels_[trackIndex].setClipPlayer(nullptr);
}

// ========== Mixer state getters ==========

float AudioEngine::getChannelFaderDb(int track) const {
    if (track < 0 || track >= MAX_TRACKS) return 0.0f;
    return channels_[track].getFaderDb();
}

float AudioEngine::getChannelPan(int track) const {
    if (track < 0 || track >= MAX_TRACKS) return 0.0f;
    return channels_[track].getPan();
}

bool AudioEngine::isChannelMute(int track) const {
    if (track < 0 || track >= MAX_TRACKS) return false;
    return channels_[track].isMute();
}

bool AudioEngine::isChannelSolo(int track) const {
    if (track < 0 || track >= MAX_TRACKS) return false;
    return channels_[track].isSolo();
}

bool AudioEngine::isChannelArm(int track) const {
    if (track < 0 || track >= MAX_TRACKS) return false;
    return channels_[track].isArm();
}

float AudioEngine::getSendLevel(int track, int bus) const {
    if (track < 0 || track >= MAX_TRACKS) return 0.0f;
    return (bus == 0) ? channels_[track].getSendALevel() : channels_[track].getSendBLevel();
}

float AudioEngine::getChannelLevel(int track) const {
    if (track < 0 || track >= MAX_TRACKS) return 0.0f;
    return channels_[track].getLevel();
}

float AudioEngine::getBusFaderDb(int bus) const {
    if (bus < 0 || bus >= NUM_BUSES) return 0.0f;
    return buses_[bus].getFaderDb();
}

float AudioEngine::getMasterFaderDb() const {
    return masterBus_.getFaderDb();
}

// ========== Insert reorder ==========

void AudioEngine::reorderChannelInserts(int track, int fromSlot, int toSlot) {
    if (track < 0 || track >= MAX_TRACKS) return;
    channels_[track].reorderInserts(fromSlot, toSlot);
}

// ========== Offline render ==========

bool AudioEngine::startOfflineRender(const std::string& outputPath, int64_t totalSamples) {
    if (offlineRenderActive_.load(std::memory_order_relaxed)) {
        LOGE("Offline render already in progress");
        return false;
    }
    offlineRenderBuffer_.clear();
    offlineRenderBuffer_.reserve(static_cast<size_t>(totalSamples));
    offlineRenderPath_ = outputPath;
    offlineRenderTrack_ = -1;
    offlineRenderCancel_.store(false, std::memory_order_release);
    offlineRenderActive_.store(true, std::memory_order_release);
    LOGI("Offline render started: %lld samples -> %s",
         static_cast<long long>(totalSamples), outputPath.c_str());
    return true;
}

bool AudioEngine::startOfflineRenderForTrack(const std::string& outputPath, int trackIndex,
                                              int64_t totalSamples) {
    if (trackIndex < 0 || trackIndex >= MAX_TRACKS) return false;
    // Save and set solo states: only the target track plays.
    for (int t = 0; t < MAX_TRACKS; t++) {
        savedSoloStates_[t] = channels_[t].isSolo();
        channels_[t].setSolo(t == trackIndex);
    }
    offlineRenderTrack_ = trackIndex;
    LOGI("Offline render for track %d", trackIndex);
    return startOfflineRender(outputPath, totalSamples);
}

void AudioEngine::stopOfflineRender() {
    if (!offlineRenderActive_.load(std::memory_order_relaxed)) return;

    offlineRenderActive_.store(false, std::memory_order_release);

    // Restore solo states if this was a track render
    if (offlineRenderTrack_ >= 0) {
        for (int t = 0; t < MAX_TRACKS; t++) {
            channels_[t].setSolo(savedSoloStates_[t]);
        }
        offlineRenderTrack_ = -1;
    }

    // Write captured buffer to WAV
    if (!offlineRenderBuffer_.empty() && !offlineRenderPath_.empty()) {
        int rate = static_cast<int>(sampleRate_);
        auto buffer = std::make_shared<SampleBuffer>(
            std::move(offlineRenderBuffer_), rate, 1);
        bool ok = WavWriter::write(*buffer, offlineRenderPath_.c_str());
        LOGI("Offline render %s: %d frames to %s",
             ok ? "succeeded" : "FAILED",
             buffer->getNumFrames(),
             offlineRenderPath_.c_str());
    } else {
        LOGE("Offline render: no data captured or no output path");
    }
    offlineRenderBuffer_.clear();
    offlineRenderPath_.clear();
}

// ========== Perform FX ==========

void AudioEngine::triggerPerformFx(int type) {
    performFxTrigger_.store(type, std::memory_order_release);
}

// ========== Per-pad synth pool ==========

SynthInstrument* AudioEngine::getPadSynth(int padIndex) {
    if (padIndex < 0 || padIndex >= PAD_SYNTH_COUNT) return nullptr;
    if (auto* existing = getExistingPadSynth(padIndex)) return existing;

    std::lock_guard<std::mutex> lock(padSynthCreationMutex_);
    if (!synthForPad_[padIndex]) {
        synthForPad_[padIndex] = std::make_unique<SynthInstrument>();
        synthForPad_[padIndex]->init(sampleRate_);
        publishedPadSynths_[padIndex].store(synthForPad_[padIndex].get(), std::memory_order_release);
        LOGI("Created per-pad synth for pad %d", padIndex);
    }
    return synthForPad_[padIndex].get();
}

SynthInstrument* AudioEngine::getExistingPadSynth(int padIndex) const {
    if (padIndex < 0 || padIndex >= PAD_SYNTH_COUNT) return nullptr;
    return publishedPadSynths_[padIndex].load(std::memory_order_acquire);
}

void AudioEngine::applyPadSynthState(int padIndex, const SynthParams& params) {
    auto* synth = getPadSynth(padIndex);
    if (synth) {
        synth->setParams(params);
    }
}
