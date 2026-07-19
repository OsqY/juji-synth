#ifndef JUJIDAW_SAMPLER_VOICE_H
#define JUJIDAW_SAMPLER_VOICE_H

#include "SampleBuffer.h"
#include "Filter.h"
#include "Envelope.h"
#include <cstdint>

// One-shot, loop, or reverse sample playback voice.
struct SamplerVoice {
    const SampleBuffer* buffer = nullptr;

    // Playback state
    float position = 0.0f;
    float playbackSpeed = 1.0f;
    bool active = false;
    bool reverse = false;
    bool loop = false;
    bool oneShot = true;

    // Per-voice controls
    float pitch = 0.0f;      // semitones
    float pan = 0.0f;        // -1..1
    float volume = 1.0f;     // 0..1
    float attack = 0.0f;     // seconds
    float release = 0.0f;    // seconds
    bool useFilter = false;
    Filter filter;
    Envelope envelope;

    double sampleRate = 48000.0;
    int note = -1;
    // Destination mixer row, assigned when the voice is triggered.
    int trackIndex = 1;
    int velocity = 100;
    uint64_t age = 0;
    uint64_t triggerId = 0;

    void init(double sr);
    void start(const SampleBuffer* buf, int midiNote, int vel);
    void stop();
    void reset();

    // Process one sample. Returns mono output.
    float process();

    bool isActive() const { return active; }

private:
    float getSpeedForSemitones(float semitones) const;
};

#endif // JUJIDAW_SAMPLER_VOICE_H
