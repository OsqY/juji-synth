## 1. Theme & Colors

- [x] 1.1 Create new `Color.kt` with dark gunmetal color palette (BgGunmetal, PanelMetal, PanelHighlight, PanelShadow, ScrewHead, ScrewHighlight)
- [x] 1.2 Define section LED colors: LedAmber, LedCyan, LedGreen, LedPink, LedRed
- [x] 1.3 Create `Type.kt` with monospace font for LCD displays and condensed sans-serif for labels
- [x] 1.4 Create `SynthPanel.kt` composable with brushed metal background, screw heads, title label, and inner shadow

## 2. Realistic Knob Component

- [x] 2.1 Create `RealKnob.kt` with Canvas-based layered drawing: outer shadow, metal rim, face gradient, 30 tick marks, value arc, pointer needle
- [x] 2.2 Add color-coded LED ring around knob that glows brighter with higher values
- [x] 2.3 Implement vertical drag gesture with fine control (sensitivity curve)
- [ ] 2.4 Add haptic feedback at major tick positions (0%, 25%, 50%, 75%, 100%)
- [x] 2.5 Add `LcdDisplay.kt` — small rectangular readout with monospace font, dark bg, light text, subtle glow
- [x] 2.6 Refactor panels to use RealKnob with color-coded ledColor

## 3. Hardware Chassis & Layout

- [x] 3.1 Rewrite `MainSynthScreen.kt` layout: remove tab bar, use 3-column persistent grid
- [x] 3.2 Left column: OSC panel (full height) + MOD panel (patch bay)
- [x] 3.3 Center column: FILTER panel + ENV panel (stacked) + LFO panel
- [x] 3.4 Right column: FX panel + SEQ panel
- [x] 3.5 Update `HardwareChassis.kt` with brushed metal texture, screw heads at corners, section dividers
- [ ] 3.6 Ensure all panels fit on a 1080x2400 phone screen without scrolling the main layout

## 4. Patch Bay Modulation

- [x] 4.1 Create `PatchBayView.kt` with 8 rows, each row has source jack (left), amount knob (center), destination jack (right)
- [x] 4.2 Draw Bézier curve cables between active source/destination jacks
- [x] 4.3 Cable thickness and brightness SHALL scale with modulation amount
- [x] 4.4 Replace `ModulationMatrixPanel.kt` list view with `PatchBayView` (deleted ModulationMatrixPanel.kt)

## 5. Oscilloscope

- [x] 5.1 Add `nativeGetWaveform(float[] buffer, int size)` to `SynthEngine.kt` and JNI bridge
- [x] 5.2 Implement C++ side: circular buffer in `AudioEngine` that stores last 512 output samples
- [x] 5.3 Create `OscilloscopeView.kt` with Canvas drawing: dark screen, bright green trace, scanlines, rounded corners
- [x] 5.4 Poll waveform data at 30fps using `LaunchedEffect` + delay
- [x] 5.5 Add oscilloscope to right column layout

## 6. Slide-Out Preset Drawer

- [x] 6.1 Replace `AlertDialog` preset browser with `ModalBottomSheet` slide-out drawer
- [x] 6.2 Drawer shows categories and preset list with existing layout
- [x] 6.3 Tapping a preset loads it and animates drawer closed
- [x] 6.4 Add preset button to transport bar (already present)
- [x] 6.5 Ensure drawer works with existing preset DAO and database

## 7. Keyboard & Transport

- [ ] 7.1 Redesign piano keys with proper overhang, velocity shading, octave markers
- [x] 7.2 Add pitch-bend ribbon controller above keyboard (horizontal drag) — already existed
- [ ] 7.3 Add modulation wheel next to pitch bend
- [x] 7.4 Redesign transport buttons with hardware-style border and inset effect
- [x] 7.5 Replace BPM text with LcdDisplay

## 9. Build & Verify

- [x] 9.1 Run `./gradlew lint` and fix any issues
- [x] 9.2 Run `./gradlew clean assembleDebug` — BUILD SUCCESSFUL
- [x] 9.3 Install and verify via `./install.sh` — installed on device
- [ ] 9.4 User test: verify knobs, oscilloscope, preset drawer on device

## Session 1 Complete — Old color migration artifacts (retained for reference)
## 10. Color Palette Migration (Entire Codebase)

- [x] 10.1 Remove all `PurpleMid`, `PurplePrimary`, `PurpleLight`, `PurpleDark`, `PurpleAccent`, `PurpleSecondary` references
- [x] 10.2 Remove `SurfaceDark`, `SurfaceCard`, `SurfaceLight`, `SurfaceKnob`, `BgKnobArea` references
- [x] 10.3 Delete unused `TabBar.kt`
- [x] 10.4 Fix `RealKnob.kt` `background` import issue
