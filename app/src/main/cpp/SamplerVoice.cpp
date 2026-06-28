#include "SamplerVoice.h"
#include <cmath>

void SamplerVoice::init(double sr) {
    sampleRate = sr;
    filter.init(sr);
    envelope.init(sr);
}

void SamplerVoice::start(const SampleBuffer* buf, int midiNote, int vel) {
    buffer = buf;
    note = midiNote;
    velocity = vel;
    active = true;

    playbackSpeed = getSpeedForSemitones(pitch);

    if (reverse) {
        position = static_cast<float>(buf ? buf->getNumFrames() - 1 : 0);
    } else {
        position = 0.0f;
    }

    filter.init(sampleRate);
    if (useFilter) {
        filter.setMode(0); // LPF by default
    }

    envelope.init(sampleRate);
    envelope.setAttack(static_cast<double>(attack));
    envelope.setRelease(static_cast<double>(release));
    envelope.noteOn();
}

void SamplerVoice::stop() {
    envelope.noteOff();
}

void SamplerVoice::reset() {
    active = false;
    buffer = nullptr;
    position = 0.0f;
    envelope.reset();
}

float SamplerVoice::process() {
    if (!active || buffer == nullptr || !buffer->isLoaded()) {
        return 0.0f;
    }

    int numFrames = buffer->getNumFrames();
    if (numFrames <= 0) {
        active = false;
        return 0.0f;
    }

    float env = envelope.process();
    if (env <= 0.0f && envelope.isIdle()) {
        active = false;
        return 0.0f;
    }

    int channels = buffer->getChannels();
    float sample = 0.0f;
    for (int c = 0; c < channels; c++) {
        sample += buffer->getSampleInterpolated(position, c);
    }
    sample /= channels;

    if (useFilter) {
        sample = filter.process(sample);
    }

    // Pan law
    float left = 1.0f, right = 1.0f;
    float angle = (pan + 1.0f) * 0.25f * 3.14159265f;
    left = std::cos(angle);
    right = std::sin(angle);
    float mono = sample * 0.5f * (left + right);

    float velGain = velocity / 127.0f;
    float out = mono * volume * velGain * env;

    // Advance position
    if (reverse) {
        position -= playbackSpeed;
        if (position <= 0.0f) {
            if (loop) {
                position = static_cast<float>(numFrames - 1);
            } else {
                active = false;
            }
        }
    } else {
        position += playbackSpeed;
        if (position >= static_cast<float>(numFrames - 1)) {
            if (loop) {
                position = 0.0f;
            } else {
                active = false;
            }
        }
    }

    return out;
}

float SamplerVoice::getSpeedForSemitones(float semitones) const {
    return std::pow(2.0f, semitones / 12.0f);
}
