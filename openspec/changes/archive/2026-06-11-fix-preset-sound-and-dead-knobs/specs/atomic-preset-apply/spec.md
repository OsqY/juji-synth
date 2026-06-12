## ADDED Requirements

### Requirement: Preset parameters SHALL be applied atomically
All parameters of a preset SHALL be committed to the audio engine as a single atomic swap. The audio thread SHALL never see a partially-applied preset where some parameters have been updated and others have not.

#### Scenario: Preset with wide-ranging parameters
- **WHEN** a preset is loaded that changes osc1Waveform, filterCutoff, reverbMix, and ampAttack simultaneously
- **THEN** the audio engine SHALL transition to all new values at once, with none of the old values audible after the transition

#### Scenario: No mid-load glitch
- **WHEN** a preset is being applied while a note is held
- **THEN** there SHALL be no audible glitch, pop, or drop-out caused by partially-updated parameters

### Requirement: JNI bulk apply method
A new JNI method `nativeApplySynthState(values: FloatArray)` SHALL replace the existing 35+ individual `nativeSetParam` calls in `applySynthStateToEngine()`. The method SHALL unpack all values into `pendingParams_` and set `paramsPending_ = true` in a single call.

#### Scenario: Bulk apply matches per-param apply
- **WHEN** a preset's parameters are applied via `nativeApplySynthState`
- **THEN** the resulting audio engine state SHALL be identical to applying each parameter individually via `nativeSetParam`
