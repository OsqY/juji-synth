## ADDED Requirements

### Requirement: LFO1 and LFO2 SHALL modulate their routed destinations
The modulation matrix SHALL process LFO1 and LFO2 as source values and apply their output to configured destinations. Modulation routes SHALL be evaluated at audio block rate (~5.8ms at 256 frames/44.1kHz).

#### Scenario: LFO destination route updates audio
- **WHEN** a modulation route is active from LFO1 to Filter Cutoff with amount = 0.5
- **THEN** the filter cutoff SHALL vary at LFO1's rate with the specified depth

#### Scenario: Multiple LFO routes stack
- **WHEN** both LFO1 and LFO2 have active routes to different destinations
- **THEN** both destinations SHALL be independently modulated by their respective LFO sources

#### Scenario: Zero-depth LFO has no effect
- **WHEN** an LFO's depth is 0.0
- **THEN** the modulation value SHALL be 0.0 regardless of LFO waveform or rate

### Requirement: Modulation matrix SHALL be called from the audio loop
`ModulationMatrix::getModulation()` SHALL be invoked for each destination during `processAudio()`. The returned modulation values SHALL be applied to the voice parameters (filter cutoff, pitch, amp, osc mix) at block rate.

#### Scenario: Moving Pad preset produces filter sweep
- **WHEN** the Moving Pad preset is loaded (lfo1Rate=0.3, lfo1Depth=0.4, route to filter)
- **THEN** the filter cutoff SHALL audibly modulate at ~0.3Hz with the specified depth
