## Context

Reverb.h and Delay.h initialize with `mix_ = 0.3` (30% wet) by default. This means every note played through the synth is processed through reverb and delay at 30% mix, even when the user hasn't touched the effects controls. The delay at default settings (~198ms, 30% feedback) creates a rhythmic pulsation that users perceive as "stutter." The constant reverb wash masks subtle differences between preset parameters.

## Goals / Non-Goals

**Goals:**
- Reverb and delay default to OFF (mix=0) — user must explicitly enable them
- Each fallback preset is audibly distinct with contrasting parameters

**Non-Goals:**
- No changes to audio engine architecture
- No UI changes

## Decisions

### 1. Default Effect Mix to 0

**Decision:** Change the default `mix_` value in both Reverb.h and Delay.h from `0.3` to `0.0`.

```cpp
// Reverb.h:24
double mix_ = 0.0;  // was 0.3

// Delay.h:24
double mix_ = 0.0;  // was 0.3
```

**Rationale:** Effects should be opt-in, not always-on. This matches user expectations — when you look at the effects panel with all knobs at zero, you expect zero effect. The previous defaults caused unexpected reverb and delay on every sound.

### 2. Diverse Fallback Presets

**Decision:** Rewrite each of the 20 fallback presets with audibly contrasting parameters. Each preset should have a unique combination of:
- Waveform (saw, square, triangle, sine, noise) — change between presets
- Detune (±20 to ±50 cents) — add/remove for variety
- Filter cutoff (low, band, high, full range)
- Envelope shape (short pluck, slow pad, long release)
- Effects mix (reverb, delay, chorus) — explicitly enabled for presets that need them

Key distinguishing changes per type:
- **Leads**: Saw/square, high cutoff, fast attack, some detune, no reverb
- **Pads**: Triangle/sine, medium cutoff, slow attack, high release, reverb 0.3-0.5
- **Bass**: Square/sine, low cutoff (0.05-0.25), fast attack, no reverb/no delay
- **FX**: Extreme settings, noise, effects-heavy
- **Ambient**: Noise/sine, LFO modulation, slow everything, reverb 0.5-0.7, delay 0.2-0.4

## Risks / Trade-offs

[Risk] Users might expect reverb by default after update
→ Mitigation: This is intentional — clean sound by default. Users can add reverb/delay by turning the knobs.

[Risk] Extreme preset params might sound harsh
→ Mitigation: Test extremes but keep musical. Filter cutoff at 0.95 is bright but not painful.

## Migration Plan

1. Fix Reverb.h default mix
2. Fix Delay.h default mix
3. Rewrite fallbackPresets with diverse params
4. Build and verify