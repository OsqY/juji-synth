## ADDED Requirements

### Requirement: Delay and reverb SHALL be reset on preset load
When a new preset is applied, the internal buffers of the Delay and Reverb effects SHALL be cleared to prevent audio from the previous patch bleeding into the new sound.

#### Scenario: Switching from Bright Lead to Dreamscape
- **WHEN** Bright Lead (dry, no reverb) is playing and the user switches to Dreamscape (reverbMix=0.5)
- **THEN** no audio from Bright Lead's previously ringing notes SHALL be audible through Dreamscape's reverb tail

#### Scenario: Multiple consecutive preset loads
- **WHEN** presets are loaded rapidly (e.g., every 100ms)
- **THEN** each preset SHALL start with clean delay/reverb buffers

### Requirement: Reset SHALL be thread-safe
The `reset()` calls on delay and reverb SHALL be invoked from the audio thread during `swapParamsIfNeeded()` or a dedicated audio-thread reset path, not from the UI thread.

#### Scenario: Reset does not cause audio artifacts
- **WHEN** a preset is loaded and effects are reset
- **THEN** the reset SHALL produce no audible click, pop, or silence gap longer than 1 sample
