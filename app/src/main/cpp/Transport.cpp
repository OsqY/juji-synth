#include "Transport.h"
#include "AudioEngine.h"
#include "MixerChannel.h"
#include "SamplerInstrument.h"
#include "SynthInstrument.h"
#include <android/log.h>

#define LOG_TAG "JujiDawTransport"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace jujidaw {

void Transport::init(int sampleRate) {
    sampleRate_ = sampleRate;
    currentSample_.store(0, std::memory_order_relaxed);
    playing_ = false;
    recording_ = false;
    tempoBpm_ = 120.0f;
    loopEnabled_ = false;
    loopStartSample_ = 0;
    loopEndSample_ = 0;
}

bool Transport::advance(int numSamples) {
    if (!playing_) return false;

    // Atomically advance the playhead. Relaxed ordering is safe because the
    // audio thread is the only one that calls advance() (single writer for
    // increments). The scheduler thread may concurrently setCurrentSample(),
    // which uses release ordering so the audio thread sees a consistent value
    // via getCurrentSample() with acquire ordering.
    int64_t oldSample = currentSample_.fetch_add(numSamples, std::memory_order_relaxed);
    if (loopEnabled_ && (oldSample + numSamples) >= loopEndSample_) {
        // Wrap the playhead and clear stale pending events. The caller
        // (AudioEngine::processAudio) also clears the EventQueue.
        int64_t raw = oldSample + numSamples;
        int64_t wrapped = loopStartSample_ + (raw - loopEndSample_);
        if (wrapped < loopStartSample_) wrapped = loopStartSample_;
        currentSample_.store(wrapped, std::memory_order_relaxed);
        pendingEvents_.clear();
        return true; // signal caller to clear EventQueue
    }
    return false;
}

void Transport::setLoop(bool enabled, int64_t startSample, int64_t endSample) {
    loopEnabled_ = enabled;
    loopStartSample_ = startSample;
    loopEndSample_ = endSample;
}

void Transport::drainEvents(EventQueue& queue) {
    // Build into a local vector, then move into member to avoid
    // capacity bloat from repeated clear()+push_back cycles.
    std::vector<ScheduledEvent> fresh;
    fresh.reserve(64);
    ScheduledEvent event;
    while (queue.pop(event)) {
        fresh.push_back(event);
    }
    pendingEvents_ = std::move(fresh);
}

void Transport::firePendingEvents(AudioEngine& engine, int64_t bufferStartSample, int numFrames) {
    int64_t bufferEndSample = bufferStartSample + numFrames;
    for (const auto& event : pendingEvents_) {
        // Sample-accurate scheduling: events with targetSample >= 0 fire only
        // when targetSample falls within the current buffer range. Events with
        // targetSample < 0 (the default) always fire at buffer boundary, which
        // preserves backward compatibility for transport/automation events.
        if (event.targetSample >= 0) {
            if (event.targetSample < bufferStartSample || event.targetSample >= bufferEndSample) {
                continue;
            }
        }

        int track = event.trackIndex;
        if (track < 0 || track >= AudioEngine::MAX_TRACKS) {
            LOGE("Invalid track index %d", track);
            continue;
        }

        switch (event.type) {
            case ScheduledEventType::NOTE_ON: {
                auto* instr = engine.getChannel(track).getInstrument();
                if (instr) {
                    int vel = static_cast<int>(event.data.noteEvent.velocity * 127.0f + 0.5f);
                    instr->noteOn(event.data.noteEvent.note, vel);
                }
                break;
            }
            case ScheduledEventType::NOTE_OFF: {
                auto* instr = engine.getChannel(track).getInstrument();
                if (instr) {
                    instr->noteOff(event.data.noteEvent.note);
                }
                break;
            }
            case ScheduledEventType::PAD_TRIGGER: {
                auto* instr = engine.getChannel(track).getInstrument();
                auto* sampler = dynamic_cast<SamplerInstrument*>(instr);
                if (sampler) {
                    int vel = static_cast<int>(event.data.padTrigger.velocity * 127.0f + 0.5f);
                    sampler->triggerPad(event.data.padTrigger.padIndex, vel);
                }
                break;
            }
            case ScheduledEventType::AUTOMATION: {
                // Automation events are routed through the synth instrument's
                // block-automation snapshot for now. Expand to per-track
                // automation once the mixer graph supports it.
                auto* instr = engine.getChannel(track).getInstrument();
                auto* synth = dynamic_cast<SynthInstrument*>(instr);
                if (synth) {
                    // TODO: map paramIndex to SynthAutomation field
                    (void)synth;
                }
                break;
            }
            case ScheduledEventType::TRANSPORT: {
                playing_ = event.data.transport.playing != 0;
                recording_ = event.data.transport.recording != 0;
                tempoBpm_ = event.data.transport.tempoBpm;
                break;
            }
        }
    }
    pendingEvents_.clear();
}

} // namespace jujidaw
