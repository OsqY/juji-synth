#ifndef JUJIDAW_BUS_H
#define JUJIDAW_BUS_H

#include "Filter.h"
#include "Reverb.h"
#include "Delay.h"
#include "Distortion.h"
#include "Chorus.h"
#include "MixerCommand.h"
#include <array>

// A send/return bus with up to 4 insert effect slots.
class Bus {
public:
    Bus();
    void init(double sampleRate);

    void setFader(float db);
    void setMute(bool mute);

    void setInsertBypass(int slot, bool bypass);
    void addInsertEffect(int slot, EffectType type);
    void removeInsertEffect(int slot);
    void setInsertParam(int slot, int paramId, float value);

    void applyCommand(const MixerCommand& cmd);

    float getFaderDb() const { return faderDb_; }

    // Process incoming send signal and return bus output.
    float process(float input);

private:
    double sampleRate_ = 48000.0;
    float faderDb_ = 0.0f;
    bool mute_ = false;
    float inputSample_ = 0.0f;

    struct InsertSlot {
        EffectType type = EffectType::None;
        bool bypass = false;
        bool active = false;
        Filter filter;
        Reverb reverb;
        Delay delay;
        Distortion distortion;
        Chorus chorus;
    };
    std::array<InsertSlot, MAX_INSERTS> inserts_;

    void resetInserts();
    float applyInserts(float sample);
    static float dbToLinear(float db);
};

#endif // JUJIDAW_BUS_H
