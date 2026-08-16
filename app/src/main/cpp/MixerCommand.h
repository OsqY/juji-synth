#ifndef JUJIDAW_MIXER_COMMAND_H
#define JUJIDAW_MIXER_COMMAND_H

#include <cstdint>

// Maximum insert effect slots per channel or bus.
constexpr int MAX_INSERTS = 4;

// Lock-free command types sent from UI thread to audio thread for mixer control.
// All numeric values are normalized floats unless otherwise noted.
enum class MixerCommandType : uint8_t {
    None,
    SetFader,           // track index, value 0..1
    SetPan,             // track index, value -1..1
    SetMute,            // track index, bool
    SetSolo,            // track index, bool
    SetArm,             // track index, bool
    SetSendA,           // track index, value 0..1
    SetSendB,           // track index, value 0..1
    SetInsertBypass,    // track index, slot index, bool
    AddInsertEffect,    // track index, slot index, effect type
    RemoveInsertEffect, // track index, slot index
    SetBusFader,        // bus index 0=A, 1=B, value 0..1
    SetMasterFader,     // value 0..1
    SetTrackCount,      // active track count 1..16
    SetInsertParam,     // track index, slot, paramId, value
    SetSendLevel,       // track index, bus (0/1 in slot), level
    SetBusInsertBypass, // bus index, slot index, bool
    AddBusInsertEffect,  // bus index, slot index, effect type
    RemoveBusInsertEffect, // bus index, slot index
    SetBusInsertParam,   // bus index, slot, paramId, value
};

enum class EffectType : uint8_t {
    None,
    Reverb,
    Delay,
    Distortion,
    Chorus,
    Filter,
    Bitcrusher,
    Compressor,
    Eq,
};

struct MixerCommand {
    MixerCommandType type = MixerCommandType::None;
    uint8_t track = 0;
    uint8_t slot = 0;
    float value = 0.0f;
    bool booleanValue = false;
    EffectType effectType = EffectType::None;
    uint8_t paramId = 0;
};

#endif // JUJIDAW_MIXER_COMMAND_H
