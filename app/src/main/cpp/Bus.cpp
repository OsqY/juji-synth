#include "Bus.h"
#include <algorithm>
#include <cmath>

Bus::Bus() {
    resetInserts();
}

void Bus::init(double sampleRate) {
    sampleRate_ = sampleRate;
    for (auto& slot : inserts_) {
        slot.filter.init(sampleRate);
        slot.reverb.init(sampleRate);
        slot.delay.init(sampleRate);
        slot.chorus.init(sampleRate);
    }
}

void Bus::setFader(float db) { faderDb_ = std::clamp(db, -60.0f, 12.0f); }
void Bus::setMute(bool mute) { mute_ = mute; }

void Bus::setInsertBypass(int slot, bool bypass) {
    if (slot >= 0 && slot < MAX_INSERTS) inserts_[slot].bypass = bypass;
}

void Bus::addInsertEffect(int slot, EffectType type) {
    if (slot >= 0 && slot < MAX_INSERTS) {
        inserts_[slot].type = type;
        inserts_[slot].active = (type != EffectType::None);
        inserts_[slot].bypass = false;
    }
}

void Bus::removeInsertEffect(int slot) {
    if (slot >= 0 && slot < MAX_INSERTS) {
        inserts_[slot].type = EffectType::None;
        inserts_[slot].active = false;
        inserts_[slot].bypass = false;
    }
}

void Bus::setInsertParam(int slot, int paramId, float value) {
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
        default: break;
    }
}

void Bus::applyCommand(const MixerCommand& cmd) {
    switch (cmd.type) {
        case MixerCommandType::SetBusFader: setFader(cmd.value); break;
        case MixerCommandType::SetInsertBypass:
        case MixerCommandType::SetBusInsertBypass: setInsertBypass(cmd.slot, cmd.booleanValue); break;
        case MixerCommandType::AddInsertEffect:
        case MixerCommandType::AddBusInsertEffect: addInsertEffect(cmd.slot, cmd.effectType); break;
        case MixerCommandType::RemoveInsertEffect:
        case MixerCommandType::RemoveBusInsertEffect: removeInsertEffect(cmd.slot); break;
        case MixerCommandType::SetInsertParam:
        case MixerCommandType::SetBusInsertParam: setInsertParam(cmd.slot, cmd.paramId, cmd.value); break;
        default: break;
    }
}

float Bus::process(float input) {
    inputSample_ = input;
    if (mute_) return 0.0f;

    float sample = inputSample_;
    sample = applyInserts(sample);
    return sample * dbToLinear(faderDb_);
}

void Bus::resetInserts() {
    for (auto& slot : inserts_) {
        slot.type = EffectType::None;
        slot.active = false;
        slot.bypass = false;
    }
}

float Bus::applyInserts(float sample) {
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

float Bus::dbToLinear(float db) {
    if (db <= -60.0f) return 0.0f;
    return std::pow(10.0f, db / 20.0f);
}
