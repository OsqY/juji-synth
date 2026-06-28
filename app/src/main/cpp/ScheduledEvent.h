#ifndef JUJIDAW_SCHEDULED_EVENT_H
#define JUJIDAW_SCHEDULED_EVENT_H

#include <cstdint>
#include <cstring>

namespace jujidaw {

enum class ScheduledEventType : uint8_t {
    NOTE_ON = 0,
    NOTE_OFF = 1,
    PAD_TRIGGER = 2,
    AUTOMATION = 3,
    TRANSPORT = 4
};

/**
 * A lightweight event scheduled by the Kotlin transport and consumed on the
 * audio thread. Events with targetSample >= 0 fire at the exact sample
 * position (sample-accurate). Events with targetSample < 0 (default) fire
 * at buffer boundary for backward compatibility.
 *
 * targetSample represents the absolute sample offset on the transport clock
 * at which the event should fire. It is set by the Kotlin scheduler and
 * checked against the current buffer range in firePendingEvents.
 */
struct ScheduledEvent {
    ScheduledEventType type = ScheduledEventType::NOTE_ON;
    int trackIndex = 0; // 0..15 mixer channel

    /** Absolute sample at which this event should fire, or -1 for ASAP. */
    int64_t targetSample = -1;

    union EventData {
        struct {
            int note;
            float velocity;
        } noteEvent;
        struct {
            int padIndex;
            float velocity;
        } padTrigger;
        struct {
            int paramIndex;
            float value;
        } automation;
        struct {
            uint8_t playing;
            uint8_t recording;
            float tempoBpm;
        } transport;

        EventData() { std::memset(this, 0, sizeof(*this)); }
        ~EventData() = default;
    } data;

    ScheduledEvent() = default;

    /** @param targetSample Absolute sample position, or -1 for ASAP (buffer boundary). */
    static ScheduledEvent makeNoteOn(int track, int note, float velocity,
                                     int64_t targetSample = -1) {
        ScheduledEvent e;
        e.type = ScheduledEventType::NOTE_ON;
        e.trackIndex = track;
        e.targetSample = targetSample;
        e.data.noteEvent.note = note;
        e.data.noteEvent.velocity = velocity;
        return e;
    }

    /** @param targetSample Absolute sample position, or -1 for ASAP (buffer boundary). */
    static ScheduledEvent makeNoteOff(int track, int note,
                                      int64_t targetSample = -1) {
        ScheduledEvent e;
        e.type = ScheduledEventType::NOTE_OFF;
        e.trackIndex = track;
        e.targetSample = targetSample;
        e.data.noteEvent.note = note;
        e.data.noteEvent.velocity = 0.0f;
        return e;
    }

    /** @param targetSample Absolute sample position, or -1 for ASAP (buffer boundary). */
    static ScheduledEvent makePadTrigger(int track, int padIndex, float velocity,
                                          int64_t targetSample = -1) {
        ScheduledEvent e;
        e.type = ScheduledEventType::PAD_TRIGGER;
        e.trackIndex = track;
        e.targetSample = targetSample;
        e.data.padTrigger.padIndex = padIndex;
        e.data.padTrigger.velocity = velocity;
        return e;
    }

    static ScheduledEvent makeAutomation(int track, int paramIndex, float value,
                                          int64_t targetSample = -1) {
        ScheduledEvent e;
        e.type = ScheduledEventType::AUTOMATION;
        e.trackIndex = track;
        e.targetSample = targetSample;
        e.data.automation.paramIndex = paramIndex;
        e.data.automation.value = value;
        return e;
    }

    static ScheduledEvent makeTransport(bool playing, bool recording, float tempoBpm,
                                          int64_t targetSample = -1) {
        ScheduledEvent e;
        e.type = ScheduledEventType::TRANSPORT;
        e.targetSample = targetSample;
        e.data.transport.playing = playing ? 1u : 0u;
        e.data.transport.recording = recording ? 1u : 0u;
        e.data.transport.tempoBpm = tempoBpm;
        return e;
    }
};

} // namespace jujidaw

#endif // JUJIDAW_SCHEDULED_EVENT_H
