## ADDED Requirements

### Requirement: Dual oscillator system
The synthesizer SHALL provide two independent oscillators (OSC1 and OSC2), each capable of generating audio waveforms for subtractive synthesis.

#### Scenario: Oscillator waveform selection
- **WHEN** user selects waveform type for an oscillator
- **THEN** oscillator generates the selected waveform (saw, square, triangle, sine) at the current frequency

#### Scenario: Oscillator frequency modulation
- **WHEN** oscillator receives note-on command with a specific pitch
- **THEN** oscillator generates audio at the corresponding frequency for that note

#### Scenario: Both oscillators active
- **WHEN** both OSC1 and OSC2 are enabled
- **THEN** both oscillators generate audio simultaneously and their signals are mixed

### Requirement: Waveform types
Each oscillator SHALL support the following waveform types: saw, square, triangle, sine.

#### Scenario: Sawtooth waveform
- **WHEN** user selects sawtooth waveform for an oscillator
- **THEN** oscillator generates harmonics following the harmonic series (1/f, 1/2f, 1/3f, ...)

#### Scenario: Square waveform
- **WHEN** user selects square waveform for an oscillator
- **THEN** oscillator generates odd harmonics only (1/f, 1/3f, 1/5f, ...)

#### Scenario: Triangle waveform
- **WHEN** user selects triangle waveform for an oscillator
- **THEN** oscillator generates odd harmonics at decreasing amplitude (1/f, 1/9f, 1/25f, ...)

#### Scenario: Sine waveform
- **WHEN** user selects sine waveform for an oscillator
- **THEN** oscillator generates pure fundamental frequency with no harmonics

### Requirement: Oscillator detune
Each oscillator SHALL support adjustable detune between -100 and +100 cents to create thickness or beating effects.

#### Scenario: Detune increases perceived width
- **WHEN** user increases detune on OSC2 relative to OSC1
- **THEN** the combined sound exhibits increased width and beating sensation

### Requirement: Oscillator level control
Each oscillator SHALL have an independent level control (0-100%) controlling its contribution to the mixed output.

#### Scenario: OSC2 level at zero
- **WHEN** OSC2 level is set to 0%
- **THEN** only OSC1 contributes to the audio output

#### Scenario: OSC2 level at maximum
- **WHEN** OSC2 level is set to 100%
- **THEN** OSC2 contributes maximally to the audio output

### Requirement: Sub-oscillator
OSC1 SHALL include a sub-oscillator option that generates a square wave one octave below the played note.

#### Scenario: Sub-oscillator enabled
- **WHEN** sub-oscillator is enabled and a note is played
- **THEN** a square wave at exactly half the fundamental frequency is generated

### Requirement: Noise source
A noise generator SHALL be available as an alternative audio source for creating percussion-like sounds, wind, or textural effects.

#### Scenario: Noise selected as source
- **WHEN** user selects noise as the sound source
- **THEN** white noise with equal energy across all frequencies is generated

### Requirement: Oscillator sync
OSC2 SHALL support oscillator sync mode where resetting OSC2's phase when OSC1 completes a cycle creates harmonically rich sounds.

#### Scenario: Hard sync enabled
- **WHEN** oscillator sync is enabled on OSC2
- **THEN** OSC2's phase is reset whenever OSC1 completes one full cycle