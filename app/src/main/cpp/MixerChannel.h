#ifndef JUJIDAW_MIXER_CHANNEL_H
#define JUJIDAW_MIXER_CHANNEL_H

#include "Instrument.h"
#include "Filter.h"
#include "Reverb.h"
#include "Delay.h"
#include "Distortion.h"
#include "Chorus.h"
#include "MixerCommand.h"
#include <array>
#include <atomic>
#include <cmath>

class AudioClipPlayer;

// A channel in the mixer: one instrument + insert chain + pan/fader/mute/solo + sends.
class MixerChannel {
public:
    MixerChannel();

    void init(double sampleRate);

    // Set the instrument for this channel. Does not take ownership.
    void setInstrument(Instrument* instrument);

    // Optional audio clip player mixed on top of the instrument. Does not
    // take ownership. Setting nullptr disables clip playback on this channel.
    void setClipPlayer(AudioClipPlayer* player) { clipPlayer_ = player; }

    // Mixer controls (may be called from UI thread; values are atomic or queued).
    void setFader(float db);
    void setPan(float pan); // -1..1
    void setMute(bool mute);
    void setSolo(bool solo);
    void setArm(bool arm);
    void setSendA(float level);
    void setSendB(float level);

    // Insert FX (real-time safe parameter changes are applied immediately here).
    void setInsertBypass(int slot, bool bypass);
    void addInsertEffect(int slot, EffectType type);
    void removeInsertEffect(int slot);
    void setInsertParam(int slot, int paramId, float value);

    // Apply a queued command atomically on the audio thread.
    void applyCommand(const MixerCommand& cmd);

    // Render one sample. Returns the channel's output and optionally writes send levels.
    float process(float& sendA, float& sendB);

    // Insert reorder: swap two insert slots
    void reorderInserts(int fromSlot, int toSlot);

    // Accessors
    Instrument* getInstrument() const { return instrument_; }
    bool isMute() const { return mute_; }
    bool isSolo() const { return solo_; }
    bool isArm() const { return arm_; }
    float getFaderDb() const { return faderDb_; }
    float getPan() const { return pan_; }
    float getSendALevel() const { return sendALevel_; }
    float getSendBLevel() const { return sendBLevel_; }
    float getLevel() const { return peakLevel_.load(std::memory_order_relaxed); }
    EffectType getInsertType(int slot) const {
        return (slot >= 0 && slot < MAX_INSERTS) ? inserts_[slot].type : EffectType::None;
    }

private:
    Instrument* instrument_ = nullptr;
    AudioClipPlayer* clipPlayer_ = nullptr;

    double sampleRate_ = 48000.0;

    // Mix controls
    float faderDb_ = 0.0f;
    float pan_ = 0.0f;      // -1..1
    bool mute_ = false;
    bool solo_ = false;
    bool arm_ = false;
    float sendALevel_ = 0.0f;
    float sendBLevel_ = 0.0f;

    // Insert chain
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
    static void panLaw(float pan, float& left, float& right);

    // Peak level with fast-attach / exponential-decay (approx 10ms decay)
    std::atomic<float> peakLevel_{0.0f};
    float peakDecayFactor_ = 0.99977f; // exp(-1/(44100*0.01)), recomputed in init()
};

#endif // JUJIDAW_MIXER_CHANNEL_H
