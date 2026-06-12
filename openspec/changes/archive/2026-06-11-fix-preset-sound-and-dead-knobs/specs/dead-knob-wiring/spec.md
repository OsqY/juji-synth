## ADDED Requirements

### Requirement: OSC Mix knob SHALL control oscillator balance
The oscMix parameter SHALL control the balance between OSC1 and OSC2 output in each voice. At 0.0, only OSC1 is heard. At 1.0, only OSC2 is heard. At 0.5, both are at equal amplitude.

#### Scenario: oscMix at 0.0
- **WHEN** oscMix = 0.0 and both oscillators are active
- **THEN** only OSC1 SHALL be audible

#### Scenario: oscMix at 1.0
- **WHEN** oscMix = 1.0 and both oscillators are active
- **THEN** only OSC2 SHALL be audible

#### Scenario: oscMix at 0.5 balanced
- **WHEN** oscMix = 0.5 and both oscillators are active with equal amplitude
- **THEN** the output SHALL be a balanced mix of both, same as the current fixed 50/50 behavior

### Requirement: OSC Sync SHALL reset OSC2 phase from OSC1
When oscSync is enabled and OSC1's phase index wraps around (completes a full cycle), OSC2's phase SHALL be reset to zero, producing oscillator sync (hard sync) effect.

#### Scenario: Sync Lead preset
- **WHEN** the Sync Lead preset is loaded (oscSync=true) and a note plays
- **THEN** the output SHALL contain the characteristic hard-sync waveform with spectral peaks

#### Scenario: Sync disabled
- **WHEN** oscSync is false
- **THEN** OSC2's phase SHALL be independent of OSC1 (no sync)

### Requirement: Mod Wheel SHALL affect pitch
The modulation wheel SHALL add a pitch bend (vibrato when combined with LFO). At 0.0, no effect. At 1.0, the pitch SHALL be bent upward by a configurable amount (default 0.5 semitones), with a smoothing time constant of ~20ms.

#### Scenario: Mod wheel at maximum
- **WHEN** modWheel_ = 1.0 and a note is sustained
- **THEN** the pitch SHALL be audibly raised by approximately 0.5 semitones

### Requirement: Sub Osc Level SHALL add a square wave one octave below
The subOscLevel parameter SHALL mix a square wave one octave below the note's fundamental frequency into the voice output. The sub-oscillator SHALL be per-voice (not global) and SHALL NOT phase-glitch across polyphonic voices.

#### Scenario: Deep Sub preset with subOsc
- **WHEN** a low note is played with subOscLevel > 0.0
- **THEN** the output SHALL contain an audible sub-octave component one octave below the fundamental

#### Scenario: Zero sub level
- **WHEN** subOscLevel = 0.0
- **THEN** no sub-oscillator component SHALL be present in the output
