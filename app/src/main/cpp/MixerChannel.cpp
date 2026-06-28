#include "MixerChannel.h"
#include "AudioClipPlayer.h"
#include <algorithm>
#include <cmath>

MixerChannel::MixerChannel() {
    resetInserts();
}

void MixerChannel::init(double sampleRate) {
    sampleRate_ = sampleRate;
    peakDecayFactor_ = std::exp(-1.0f / static_cast<float>(sampleRate * 0.01));
    for (auto& slot : inserts_) {
        slot.filter.init(sampleRate);
        slot.reverb.init(sampleRate);
        slot.delay.init(sampleRate);
        slot.chorus.init(sampleRate);
    }
}

void MixerChannel::setInstrument(Instrument* instrument) {
    instrument_ = instrument;
}

void MixerChannel::setFader(float db) { faderDb_ = std::clamp(db, -60.0f, 12.0f); }
void MixerChannel::setPan(float pan) { pan_ = std::clamp(pan, -1.0f, 1.0f); }
void MixerChannel::setMute(bool mute) { mute_ = mute; }
void MixerChannel::setSolo(bool solo) { solo_ = solo; }
void MixerChannel::setArm(bool arm) { arm_ = arm; }
void MixerChannel::setSendA(float level) { sendALevel_ = std::clamp(level, 0.0f, 1.0f); }
void MixerChannel::setSendB(float level) { sendBLevel_ = std::clamp(level, 0.0f, 1.0f); }

void MixerChannel::setInsertBypass(int slot, bool bypass) {
    if (slot >= 0 && slot < MAX_INSERTS) {
        inserts_[slot].bypass = bypass;
    }
}

void MixerChannel::addInsertEffect(int slot, EffectType type) {
    if (slot >= 0 && slot < MAX_INSERTS) {
        inserts_[slot].type = type;
        inserts_[slot].active = (type != EffectType::None);
        inserts_[slot].bypass = false;
    }
}

void MixerChannel::removeInsertEffect(int slot) {
    if (slot >= 0 && slot < MAX_INSERTS) {
        inserts_[slot].type = EffectType::None;
        inserts_[slot].active = false;
        inserts_[slot].bypass = false;
    }
}

void MixerChannel::setInsertParam(int slot, int paramId, float value) {
    if (slot < 0 || slot >= MAX_INSERTS) return;
    auto& s = inserts_[slot];
    if (!s.active || s.bypass) return;
    switch (s.type) {
        case EffectType::Filter:
            if (paramId == 0) s.filter.setCutoff(static_cast<double>(value));
            else if (paramId == 1) s.filter.setResonance(static_cast<double>(value));
            break;
        case EffectType::Reverb:
            if (paramId == 0) s.reverb.setMix(value);
            else if (paramId == 1) s.reverb.setDecay(value);
            break;
        case EffectType::Delay:
            if (paramId == 0) s.delay.setMix(value);
            else if (paramId == 1) s.delay.setTime(value);
            else if (paramId == 2) s.delay.setFeedback(value);
            break;
        case EffectType::Distortion:
            if (paramId == 0) s.distortion.setDrive(value);
            else if (paramId == 1) s.distortion.setMix(value);
            break;
        case EffectType::Chorus:
            if (paramId == 0) s.chorus.setRate(value);
            else if (paramId == 1) s.chorus.setDepth(value);
            else if (paramId == 2) s.chorus.setMix(value);
            break;
        default:
            break;
    }
}

void MixerChannel::applyCommand(const MixerCommand& cmd) {
    switch (cmd.type) {
        case MixerCommandType::SetFader: setFader(cmd.value); break;
        case MixerCommandType::SetPan: setPan(cmd.value); break;
        case MixerCommandType::SetMute: setMute(cmd.booleanValue); break;
        case MixerCommandType::SetSolo: setSolo(cmd.booleanValue); break;
        case MixerCommandType::SetArm: setArm(cmd.booleanValue); break;
        case MixerCommandType::SetSendA: setSendA(cmd.value); break;
        case MixerCommandType::SetSendB: setSendB(cmd.value); break;
        case MixerCommandType::SetInsertBypass: setInsertBypass(cmd.slot, cmd.booleanValue); break;
        case MixerCommandType::AddInsertEffect: addInsertEffect(cmd.slot, cmd.effectType); break;
        case MixerCommandType::RemoveInsertEffect: removeInsertEffect(cmd.slot); break;
        case MixerCommandType::SetInsertParam: setInsertParam(cmd.slot, cmd.paramId, cmd.value); break;
        case MixerCommandType::SetSendLevel: {
            if (cmd.slot == 0) setSendA(cmd.value);
            else setSendB(cmd.value);
        } break;
        default: break;
    }
}

float MixerChannel::process(float& sendA, float& sendB) {
    sendA = 0.0f;
    sendB = 0.0f;

    if (mute_) return 0.0f;

    float sample = 0.0f;
    if (instrument_ != nullptr) {
        sample = instrument_->process();
    }
    if (clipPlayer_ != nullptr) {
        sample += clipPlayer_->process();
    }
    if (instrument_ == nullptr && clipPlayer_ == nullptr) {
        return 0.0f;
    }

    sample = applyInserts(sample);

    float linear = dbToLinear(faderDb_);
    float left = 1.0f, right = 1.0f;
    panLaw(pan_, left, right);

    // Mono-to-stereo panning then downmix to mono for the main path
    float stereo = sample * linear;
    float mainOut = stereo * 0.5f * (left + right);

    // Sends are pre-fader (post-insert) for now
    sendA = sample * sendALevel_;
    sendB = sample * sendBLevel_;

    // Peak level: attack=immediate, decay=~10ms exponential decayFactor
    float absSample = std::abs(mainOut);
    float decay = peakLevel_.load(std::memory_order_relaxed) * peakDecayFactor_;
    peakLevel_.store(std::max(absSample, decay), std::memory_order_relaxed);

    return mainOut;
}

void MixerChannel::reorderInserts(int fromSlot, int toSlot) {
    if (fromSlot < 0 || fromSlot >= MAX_INSERTS) return;
    if (toSlot < 0 || toSlot >= MAX_INSERTS) return;
    if (fromSlot == toSlot) return;
    std::swap(inserts_[fromSlot], inserts_[toSlot]);
}

void MixerChannel::resetInserts() {
    for (auto& slot : inserts_) {
        slot.type = EffectType::None;
        slot.active = false;
        slot.bypass = false;
    }
}

float MixerChannel::applyInserts(float sample) {
    for (auto& slot : inserts_) {
        if (!slot.active || slot.bypass) continue;
        switch (slot.type) {
            case EffectType::Filter: sample = slot.filter.process(sample); break;
            case EffectType::Reverb: sample = slot.reverb.process(sample); break;
            case EffectType::Delay: sample = slot.delay.process(sample); break;
            case EffectType::Distortion: sample = slot.distortion.process(sample); break;
            case EffectType::Chorus: sample = slot.chorus.process(sample); break;
            default: break;
        }
    }
    return sample;
}

float MixerChannel::dbToLinear(float db) {
    if (db <= -60.0f) return 0.0f;
    return std::pow(10.0f, db / 20.0f);
}

void MixerChannel::panLaw(float pan, float& left, float& right) {
    // Sin/cos pan with ±30° spread for a natural stereo image.
    // Full π/2 spread (hard left/right) is fatiguing on headphones;
    // ±30° maps pan 0 to center (45° / equal cos/sin) and extremes
    // to 15° / 75° respectively.
    float angle = 0.785398163f + pan * 0.523598776f; // π/4 + pan * π/6
    left = std::cos(angle);
    right = std::sin(angle);
}
