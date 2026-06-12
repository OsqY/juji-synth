## 1. Fix Default Effect Mix

- [x] 1.1 In Reverb.h, change `double mix_ = 0.3` to `double mix_ = 0.0`
- [x] 1.2 In Delay.h, change `double mix_ = 0.3` to `double mix_ = 0.0`
- [x] 1.3 Build and verify first note has no reverb/delay

## 2. Diversify Fallback Presets

- [x] 2.1 Rewrite lead presets (5) with saw/square waveforms, high cutoff (0.7-1.0), fast attack, detune ±20-50¢, no reverb, no delay
- [x] 2.2 Rewrite pad presets (5) with triangle/sine waveforms, medium cutoff (0.4-0.6), slow attack (0.3-0.6), long release (0.4-0.8), reverb 0.3-0.5
- [x] 2.3 Rewrite bass presets (4) with square/sine waveforms, low cutoff (0.05-0.3), fast attack, short release, no reverb, no delay
- [x] 2.4 Rewrite FX presets (3) with diverse settings: noise, extreme filter, heavy delay/reverb, short plucks
- [x] 2.5 Rewrite ambient presets (3) with noise/triangle, slow attack (0.5-0.8), long release (0.6-1.0), reverb 0.5-0.7, delay 0.2-0.4
- [x] 2.6 Build and verify presets sound audibly different

## 3. Quality Gate

- [x] 3.1 Run `./gradlew lint`
- [x] 3.2 Run `./gradlew clean assembleDebug`