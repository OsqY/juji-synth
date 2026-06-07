## Why

There is no quality free subtractive synthesizer app for Android that combines the warmth and immediacy of hardware synths like the Korg Minilogue with true depth for customization. Existing options are either too simplistic (preset-only, no real synthesis), too complex (DAW-like interfaces), require internet connectivity, or are locked behind paywalls. Juji-synth fills this gap: a standalone, horizontally-oriented synth that beginners can play immediately with great presets but can grow into as they learn synthesis concepts.

## What Changes

- **New Android synth application**: Juji-synth, a 2-oscillator subtractive synthesizer with 4-voice polyphony
- **16-step sequencer**: Program melodies and automation directly on the device
- **Comprehensive modulation**: Dual LFOs, modulation matrix for routing any source to any destination
- **Built-in effects**: Reverb, delay, and distortion for spatial and harmonic manipulation
- **50-100 curated presets**: Organized by category (leads, pads, bass, FX, ambient) to get users playing immediately while serving as learning templates
- **Full parameter control**: All oscillator, filter, envelope, and effect parameters exposed for deep customization
- **USB MIDI support**: Connect external MIDI controllers for tactile play
- **Comprehensive help system**: Tooltips on every control + searchable in-app manual
- **Horizontal UI**: Landscape-first design optimized for synth playability with on-screen knobs and sliders
- **Deep purple theme**: Dark, studio-inspired aesthetic reminiscent of hardware like the Minilogue

## Capabilities

### New Capabilities

- `oscillator-section`: Dual oscillators with multiple waveforms (saw, square, triangle, sine), oscillator sync, sub-oscillator, noise source
- `filter-section`: Multi-mode filter (low-pass, high-pass, band-pass) with resonance, filter envelope modulation
- `envelope-section`: Dual ADSR envelopes (amplitude and filter) with adjustable curves
- `lfo-section`: Dual LFOs with multiple shapes (sine, square, saw, triangle, random), key sync, multiple destinations
- `effects-section`: Send/insert effects architecture with reverb, delay, and distortion processors
- `modulation-matrix`: Visual modulation routing allowing any source (LFO, envelope, velocity, etc.) to modulate any destination
- `sequencer`: 16-step sequencer with per-step note, velocity, gate length, and parameter automation
- `preset-system`: Categorized preset library with save/load functionality, preset categories matching synth sections
- `midi-connectivity`: USB MIDI device discovery, note handling, CC mapping, device profile storage
- `help-system`: Contextual tooltips on all controls, searchable manual with tutorials and synth architecture explanation

### Modified Capabilities

<!-- No existing specs - this is a greenfield project -->

## Impact

- New application codebase: Android native with Kotlin + Jetpack Compose UI
- C++ audio engine via JNI for real-time synthesis processing
- Google Oboe library for low-latency audio output
- Android 12+ (API 31) minimum for modern audio APIs and device coverage
- No external network dependencies - fully offline operation
- No monetization code paths - 100% free