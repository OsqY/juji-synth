#include "Transport.h"
#include "AudioEngine.h"
#include "MixerChannel.h"
#include "SamplerInstrument.h"
#include "SynthInstrument.h"
#include <android/log.h>
#include <algorithm>

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
    pendingEvents_.reserve(256);
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
    // Append newly scheduled lookahead events. Replacing this vector every
    // audio buffer discarded events whose target sample was in a later buffer.
    ScheduledEvent event;
    while (queue.pop(event)) {
        pendingEvents_.push_back(event);
    }
}

void Transport::firePendingEvents(AudioEngine& engine, int64_t bufferStartSample, int numFrames) {
    int64_t bufferEndSample = bufferStartSample + numFrames;

    // A reset is a barrier. Process the newest reset before any older staged
    // musical events so Stop/Restart cannot leak a note that was already in
    // the lookahead buffer. Everything staged before/after the barrier is
    // discarded, and the reset becomes the sole state transition for this
    // callback.
    for (const auto& event : pendingEvents_) {
        if (event.type == ScheduledEventType::TRANSPORT_RESET) {
            engine.panicAllAudio();
            currentSample_.store(std::max<int64_t>(0, event.data.reset.sample),
                                 std::memory_order_release);
            playing_ = event.data.reset.playing != 0;
            recording_ = event.data.reset.recording != 0;
            pendingEvents_.clear();
            return;
        }
    }

    size_t keepCount = 0;
    for (const auto& event : pendingEvents_) {
        // Sample-accurate scheduling: events with targetSample >= 0 fire only
        // when targetSample falls within the current buffer range. Events with
        // targetSample < 0 (the default) always fire at buffer boundary, which
        // preserves backward compatibility for transport/automation events.
        if (event.targetSample >= 0) {
            if (event.targetSample >= bufferEndSample) {
                pendingEvents_[keepCount++] = event;
                continue;
            }
            // An event that arrived one buffer late should fire once now
            // instead of remaining permanently inaudible.
        }

        int track = event.trackIndex;
        if (track < 0 || track >= AudioEngine::MAX_TRACKS) {
            LOGE("Invalid track index %d", track);
            continue;
        }

        switch (event.type) {
            case ScheduledEventType::NOTE_ON: {
                // Pattern notes are currently rendered by the one global
                // synth. The event track remains available for future
                // per-track instruments, but channel 1 is the sampler and
                // cannot interpret arbitrary synth MIDI notes.
                int vel = static_cast<int>(event.data.noteEvent.velocity * 127.0f + 0.5f);
                engine.getSynth().noteOnFromAudioThread(event.data.noteEvent.note, vel);
                break;
            }
            case ScheduledEventType::NOTE_OFF: {
                engine.getSynth().noteOffFromAudioThread(event.data.noteEvent.note);
                break;
            }
            case ScheduledEventType::PAD_TRIGGER: {
                auto& sampler = engine.getSampler();
                int vel = static_cast<int>(event.data.padTrigger.velocity * 127.0f + 0.5f);
                sampler.triggerPadFromAudioThread(event.data.padTrigger.padIndex, vel);
                break;
            }
            case ScheduledEventType::PAD_RELEASE: {
                engine.getSampler().releasePad(event.data.padTrigger.padIndex);
                break;
            }
            case ScheduledEventType::AUTOMATION: {
                int pi = event.data.automation.paramIndex;
                float val = event.data.automation.value;
                if (pi <= AUTOMATION_SYNTH_PARAM_MAX) {
                    auto* instr = engine.getChannel(track).getInstrument();
                    auto* synth = dynamic_cast<SynthInstrument*>(instr);
                    if (synth) {
                        synth->applyAutomationParam(pi, val);
                    }
                } else if (pi >= AUTOMATION_MIXER_PARAM_FIRST && pi <= AUTOMATION_MIXER_PARAM_LAST) {
                    MixerCommand cmd;
                    cmd.track = static_cast<uint8_t>(track);
                    cmd.value = val;
                    switch (pi) {
                        case AUTOMATION_FADER:  cmd.type = MixerCommandType::SetFader; break;
                        case AUTOMATION_PAN:    cmd.type = MixerCommandType::SetPan; break;
                        case AUTOMATION_MUTE:   cmd.type = MixerCommandType::SetMute; cmd.booleanValue = val > 0.5f; break;
                        case AUTOMATION_SOLO:   cmd.type = MixerCommandType::SetSolo; cmd.booleanValue = val > 0.5f; break;
                        case AUTOMATION_ARM:    cmd.type = MixerCommandType::SetArm; cmd.booleanValue = val > 0.5f; break;
                        case AUTOMATION_SENDA:  cmd.type = MixerCommandType::SetSendA; break;
                        case AUTOMATION_SENDB:  cmd.type = MixerCommandType::SetSendB; break;
                    }
                    engine.pushMixerCommand(cmd);
                }
                break;
            }
            case ScheduledEventType::TRANSPORT: {
                playing_ = event.data.transport.playing != 0;
                recording_ = event.data.transport.recording != 0;
                tempoBpm_ = event.data.transport.tempoBpm;
                break;
            }
            case ScheduledEventType::TRANSPORT_RESET:
                break; // handled above
        }
    }
    pendingEvents_.resize(keepCount);
}

} // namespace jujidaw
