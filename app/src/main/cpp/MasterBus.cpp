#include "MasterBus.h"
#include <algorithm>
#include <cmath>

MasterBus::MasterBus() {
    resetInserts();
}

void MasterBus::init(double sampleRate) {
    sampleRate_ = sampleRate;
    for (auto& slot : inserts_) {
        slot.filter.init(sampleRate);
        slot.reverb.init(sampleRate);
        slot.delay.init(sampleRate);
        slot.chorus.init(sampleRate);
    }
}

void MasterBus::setFader(float db) { faderDb_ = std::clamp(db, -60.0f, 12.0f); }
void MasterBus::setLimiterThreshold(float db) { limiterThreshold_ = db; }

void MasterBus::setInsertBypass(int slot, bool bypass) {
    if (slot >= 0 && slot < MAX_INSERTS) inserts_[slot].bypass = bypass;
}

void MasterBus::addInsertEffect(int slot, EffectType type) {
    if (slot >= 0 && slot < MAX_INSERTS) {
        inserts_[slot].type = type;
        inserts_[slot].active = (type != EffectType::None);
        inserts_[slot].bypass = false;
    }
}

void MasterBus::removeInsertEffect(int slot) {
    if (slot >= 0 && slot < MAX_INSERTS) {
        inserts_[slot].type = EffectType::None;
        inserts_[slot].active = false;
        inserts_[slot].bypass = false;
    }
}

void MasterBus::setInsertParam(int slot, int paramId, float value) {
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

void MasterBus::applyCommand(const MixerCommand& cmd) {
    switch (cmd.type) {
        case MixerCommandType::SetMasterFader: setFader(cmd.value); break;
        case MixerCommandType::SetInsertBypass: setInsertBypass(cmd.slot, cmd.booleanValue); break;
        case MixerCommandType::AddInsertEffect: addInsertEffect(cmd.slot, cmd.effectType); break;
        case MixerCommandType::RemoveInsertEffect: removeInsertEffect(cmd.slot); break;
        default: break;
    }
}

float MasterBus::process(float input) {
    float sample = input;
    sample = applyInserts(sample);
    sample *= dbToLinear(faderDb_);
    sample = limit(sample);
    return sample;
}

void MasterBus::resetInserts() {
    for (auto& slot : inserts_) {
        slot.type = EffectType::None;
        slot.active = false;
        slot.bypass = false;
    }
}

float MasterBus::applyInserts(float sample) {
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

float MasterBus::limit(float sample) {
    // Simple soft clip / brick-wall limiter
    float threshold = dbToLinear(limiterThreshold_);
    if (sample > threshold) sample = threshold;
    if (sample < -threshold) sample = -threshold;
    return sample;
}

float MasterBus::dbToLinear(float db) {
    if (db <= -60.0f) return 0.0f;
    return std::pow(10.0f, db / 20.0f);
}
