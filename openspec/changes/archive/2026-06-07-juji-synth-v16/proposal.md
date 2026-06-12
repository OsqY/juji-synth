## Why

Audit of all 20 fallback presets found 3 ambient presets with problematic parameters. Attack times of 600-800ms make the synth feel unresponsive (silence for nearly a second after pressing a key). Combined with active delay effects, users hear rhythmic echoes before the main sound even arrives — which they perceive as "stutter." All other preset categories (Leads, Pads, Bass, FX) were verified clean.

## What Changes

- **Fix ambient preset attacks**: Reduce from 0.6-0.8 (600-800ms) to 0.25-0.35 (still slow and swelling, but responsive)
- **Remove delay from ambient presets**: Set delayMix to 0 in all ambient presets. Reverb alone provides enough space and wash for ambient sounds without the rhythmic echo confusion.
- **Slightly reduce Evolving Pad attack**: From 0.6 to 0.4 for consistency

## Capabilities

### Modified Capabilities
- `preset-system`: 3 ambient presets + 1 pad preset fixed for responsive attack and no stutter-inducing delay