#ifndef JUJIDAW_TRANSPORT_H
#define JUJIDAW_TRANSPORT_H

#include "EventQueue.h"
#include <atomic>
#include <cstdint>
#include <vector>

class AudioEngine;

namespace jujidaw {

/**
 * Sample clock and event dispatcher for the DAW transport.
 *
 * Owned by AudioEngine. The Kotlin scheduler pushes events into `EventQueue`;
 * the audio callback advances the sample clock and drains the queue once per
 * buffer. Drained events are fired at the start of the buffer (buffer-boundary
 * triggering).
 */
class Transport {
public:
    Transport() = default;

    void init(int sampleRate);

    // Called once per audio callback to advance the playhead.
    // Returns true if a loop wrap occurred (caller may want to clear the
    // event queue to discard stale events).
    bool advance(int numSamples);

    // Drain all queued events and stage them for firing.
    void drainEvents(EventQueue& queue);

    // Fire staged events into the mixer graph (called after drainEvents).
    // bufferStartSample is the transport sample at the start of this buffer.
    void firePendingEvents(AudioEngine& engine, int64_t bufferStartSample, int numFrames);

    // Transport state setters (called from queued events or directly).
    void setPlaying(bool playing) { playing_ = playing; }
    void setRecording(bool recording) { recording_ = recording; }
    void setTempo(float bpm) { tempoBpm_ = bpm; }
    void setLoop(bool enabled, int64_t startSample, int64_t endSample);

    // Direct position control (thread-safe via atomic).
    void setCurrentSample(int64_t sample) { currentSample_.store(sample, std::memory_order_release); }
    int64_t getCurrentSample() const { return currentSample_.load(std::memory_order_acquire); }

    // Clear staged pending events (used on loop wrap to discard stale events).
    void clearPendingEvents() { pendingEvents_.clear(); }

    bool isPlaying() const { return playing_; }
    bool isRecording() const { return recording_; }
    float getTempo() const { return tempoBpm_; }

    bool isLoopEnabled() const { return loopEnabled_; }
    int64_t getLoopStart() const { return loopStartSample_; }
    int64_t getLoopEnd() const { return loopEndSample_; }

private:
    int sampleRate_ = 48000;
    std::atomic<int64_t> currentSample_{0}; // written by audio + scheduler threads
    bool playing_ = false;
    bool recording_ = false;
    float tempoBpm_ = 120.0f;

    bool loopEnabled_ = false;
    int64_t loopStartSample_ = 0;
    int64_t loopEndSample_ = 0;

    std::vector<ScheduledEvent> pendingEvents_;
};

} // namespace jujidaw

#endif // JUJIDAW_TRANSPORT_H
