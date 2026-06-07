## Context

Juji-synth is a greenfield Android application building a hardware-inspired subtractive synthesizer. The target user is a beginner to intermediate musician who wants immediate musical output via quality presets but desires the ability to learn and customize every parameter as their skills grow.

**Technical Constraints from Research:**
- Real-time audio synthesis on Android requires C++ for DSP processing (Dart/Kotlin GC causes audio glitches)
- Google Oboe is the standard for low-latency audio on Android (AAudio with OpenSL ES fallback)
- Kotlin + Jetpack Compose is the modern Android UI standard
- JNI bridges Kotlin UI layer to C++ audio engine
- Android 12+ (API 31) provides best audio latency and USB MIDI support

**Hardware Reference:** Korg Minilogue — 2 oscillators, 4-voice polyphony, 16-step sequencer, Steiner-Parker filter topology

## Goals / Non-Goals

**Goals:**
- Provide a musically useful subtractive synthesizer with 4-voice polyphony
- Enable immediate playability via 50-100 curated presets organized by sound category
- Expose all synthesis parameters for deep customization
- Include a 16-step sequencer for creating musical phrases on-device
- Support USB MIDI controllers for tactile performance
- Provide comprehensive in-app documentation (tooltips + full manual)
- Deliver <20ms audio latency for responsive play feel

**Non-Goals:**
- iOS or other platform support (Android only for v1)
- Cloud connectivity, preset sharing, or social features
- Audio recording or export (future consideration)
- Modular synthesis or non-subtractive architectures
- Commercial monetization

## Decisions

### 1. Tech Stack: Kotlin + Compose UI, C++ Audio Engine, JNI Bridge

**Decision:** Use Kotlin for UI and application logic, C++ for real-time audio synthesis, JNI for the bridge between them.

**Rationale:** Research confirms that Flutter/Dart has threading limitations for real-time audio (GC pauses cause dropouts). Kotlin Multiplatform was considered but still requires C++ for audio. Native Android with Oboe provides the best latency. Kotlin + Compose is the official modern Android UI toolkit.

**Alternatives considered:**
- Flutter + native audio plugin: Tried by other developers, leads to complex threading and latency issues
- Pure Kotlin synthesis: Not viable — JVM GC causes audio glitches in real-time contexts
- Kotlin Multiplatform: Good for sharing business logic, but audio engine still needs C++

**Implementation:**
- UI Layer: Jetpack Compose in Kotlin
- Audio Engine: C++ classes for oscillators, filters, envelopes, effects, sequencer
- Bridge: JNI with carefully designed API to minimize crossing between JVM and native
- Build: CMake for C++ compilation within Android Gradle project

### 2. Audio Architecture: Oboe + Triple-buffer ring buffer

**Decision:** Use Google Oboe library with a triple-buffer ring buffer between UI thread and audio thread.

**Rationale:** Oboe automatically selects AAudio (Android 8.1+) or OpenSL ES (older devices) while providing a unified C++ API. The triple-buffer allows parameter changes from UI to be atomic and non-blocking on the audio thread.

**Alternatives considered:**
- AudioTrack (Java): Higher latency, more GC pressure
- SoundPool: Not suitable for synth — designed for short samples
- AAudio directly: More complex device handling than Oboe provides

### 3. Synth Architecture: Minilogue-inspired with enhancements

**Decision:** 2 oscillators (OSC1, OSC2) + sub-oscillator + noise, Steiner-Parker style filter, dual ADSR, dual LFO, effects send/insert, modulation matrix.

**Rationale:** Minilogue hits the sweet spot of capability vs. complexity. It's well-documented, beloved by users, and provides enough depth for beginners to learn while having enough power for useful sounds. Enhancements (modulation matrix, extended effects) add value without overcomplicating.

**Oscillator waveforms:** Saw, square, triangle, sine (classic subtractive set)
**Filter modes:** Low-pass, high-pass, band-pass with 12dB/octave and resonance
**Effects:** Reverb (room/hall), delay (syncable), distortion (soft clip)

### 4. Parameter Control: Expose all parameters with smart defaults

**Decision:** Every synthesis parameter is accessible via on-screen knobs/sliders. Presets provide starting points; users can modify anything.

**Rationale:** Hardware synths have no "hidden" parameters — this philosophy translates to trust and learning. Beginners use presets; intermediate users see how presets are built; advanced users create from scratch.

**UI Pattern:** Each synth section (oscillators, filter, envelopes, LFO, effects, modulation) is a panel with labeled knobs. Tapping a knob shows tooltip with explanation and value range.

### 5. Preset System: Categorized library with 50-100 initial presets

**Decision:** Presets organized into categories (Leads, Pads, Bass, FX, Ambient) with metadata (character, complexity, suggested use).

**Rationale:** Categorization helps beginners find sounds for their needs. Each preset saves complete synth state (all parameters, effects, modulation routing). Users can save custom presets locally.

**Storage:** Internal storage using Room database for preset management (name, category, parameters as JSON, timestamp).

### 6. MIDI Support: USB MIDI via Android's Native MIDI API

**Decision:** Support USB MIDI devices via Android's Native MIDI API (API 31+), with plug-and-play device detection.

**Rationale:** USB MIDI provides the most reliable, low-latency external controller experience. Android 12+ has excellent USB MIDI support built into the OS.

**Implementation:** Use `android.media.midi.MidiManager` for device discovery, pass device reference to C++ via JNI for direct MIDI message handling (reduces latency vs. going through Kotlin).

## Risks / Trade-offs

[Risk] Audio latency varies by device manufacturer
→ Mitigation: Oboe automatically configures optimal buffer size per device. Target <20ms on modern devices; accept higher on older hardware.

[Risk] C++ audio engine complexity for maintenance
→ Mitigation: Clean separation between audio DSP (C++) and UI (Kotlin). Well-documented architecture with class diagrams in specs.

[Risk] USB MIDI compatibility issues with non-compliant devices
→ Mitigation: Test with popular controllers (Akai, Novation, Korg). Provide device profile system for user-reported compatibility fixes.

[Risk] 4-voice polyphony may be limiting for complex chords
→ Mitigation: Document this clearly. Advanced users can use mono mode with unison for thicker sound.

[Risk] Battery consumption with sustained synth use
→ Mitigation: Provide audio quality vs. battery trade-off setting. Lower sample rate option for power-saving mode.

## Migration Plan

This is a greenfield project — no migration needed. Deployment approach:

1. Internal alpha testing on 3-5 diverse Android devices (different chipsets, Android versions)
2. Beta release via internal testing track
3. Gradual rollout to production

**Rollback:** Not applicable — fully local app with no server dependency. Previous APK can be reinstalled from any source.

## Open Questions

1. **Sample rate:** 44100Hz (standard) vs 48000Hz vs 96000Hz (higher fidelity, more CPU). Decision: Start with 44100Hz, add 48000Hz option in settings.

2. **Buffer size:** 256 samples (low latency) vs 512 (safer). Decision: Oboe default (device-optimized), with manual override in settings for advanced users.

3. **Mono vs stereo output:** Mono saves CPU, stereo is more musical. Decision: Stereo by default with mono option.

4. **Sequencer resolution:** 16 steps is Minilogue-standard, but 32 or 64 enables longer phrases. Decision: Start with 16, evaluate 32 in future iteration.

5. **Preset export/import:** Useful for sharing but adds complexity. Decision: v1 is local-only; evaluate sharing in v2.