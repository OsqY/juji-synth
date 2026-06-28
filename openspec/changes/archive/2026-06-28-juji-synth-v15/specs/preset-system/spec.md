## ADDED Requirements

### Requirement: Audibly distinct presets
Each fallback preset SHALL have parameter values that are audibly different from other presets. Presets within the same category SHALL use contrasting waveforms, filter ranges, envelope shapes, and effect settings.

#### Scenario: Lead vs Bass sound different
- **WHEN** user loads a lead preset and then a bass preset
- **THEN** the two sounds are clearly different (different pitch range, waveform character, and envelope response)

#### Scenario: Pad vs FX sound different
- **WHEN** user loads a pad preset and then an FX preset
- **THEN** the pad sounds smooth and sustained while the FX sounds percussive or effected
