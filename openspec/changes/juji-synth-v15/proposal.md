## Why

Device testing revealed two persistent issues: presets with sustained/pad sounds trigger a rhythmic "stutter," and most presets sound nearly identical. Investigation found that Reverb.h and Delay.h have default mix values of 0.3 (30% wet), making reverb and delay ALWAYS active from the first note. This constant 30% wet delay at ~198ms creates a rhythmic echo that users hear as "stutter," and the constant reverb/delay wash masks the differences between presets, making them sound the same.

## What Changes

- **Set default reverb mix to 0**: Change `double mix_ = 0.3` to `double mix_ = 0.0` in Reverb.h. Reverb only activates when the user explicitly turns the Mix knob.
- **Set default delay mix to 0**: Change `double mix_ = 0.3` to `double mix_ = 0.0` in Delay.h. Delay only activates when the user explicitly turns the Mix knob.
- **Make fallback presets more diverse**: Rewrite the 20 fallback presets with extreme, contrasting parameter values so each preset is audibly unique. Use wide oscillator, filter, envelope, and effect ranges.

## Capabilities

### Modified Capabilities
- `effects-section`: Reverb and delay now default to off (mix=0) instead of always-on
- `preset-system`: Fallback presets rewritten with audibly diverse parameters

## Impact
- **Reverb.h**: `mix_ = 0.3` → `mix_ = 0.0`
- **Delay.h**: `mix_ = 0.3` → `mix_ = 0.0`
- **PresetBrowser.kt**: Rewrite all 20 fallback presets with contrasting parameters