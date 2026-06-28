#ifndef JUJIDAW_AUTOMATION_PARAM_IDS_H
#define JUJIDAW_AUTOMATION_PARAM_IDS_H

/**
 * Automation paramIndex mapping shared between C++ and JNI.
 *
 * 0–38:  Synth params (same index order as SynthInstrument::setAllParamsFromArray).
 * 39–45: Per-channel mixer commands.
 *
 * Values outside this range are silently ignored.
 */

// ---- Synth params (0–38, identical to setAllParamsFromArray order) ----
// The synth param range is implicitly 0–38. No individual constants are
// needed here because the index is defined by the array layout in
// SynthInstrument::setAllParamsFromArray (SynthInstrument.cpp lines 247–288).
// Refer to that function for exact field ordering.

constexpr int AUTOMATION_SYNTH_PARAM_MAX = 38;

// ---- Mixer params (39–45) ----
constexpr int AUTOMATION_FADER  = 39;
constexpr int AUTOMATION_PAN    = 40;
constexpr int AUTOMATION_MUTE   = 41;
constexpr int AUTOMATION_SOLO   = 42;
constexpr int AUTOMATION_ARM    = 43;
constexpr int AUTOMATION_SENDA  = 44;
constexpr int AUTOMATION_SENDB  = 45;

constexpr int AUTOMATION_MIXER_PARAM_FIRST = 39;
constexpr int AUTOMATION_MIXER_PARAM_LAST  = 45;

#endif // JUJIDAW_AUTOMATION_PARAM_IDS_H
