# SM-G998W — Android 15

## Device facts

- Model: `SM-G998W` (physical USB device `R5CR31WJ5VX`)
- Android: `15`, API `35`
- Orientation: portrait (`orientation=0`, `mCurrentOrientation=0`)
- `adb shell wm size`: physical `1440x3200`, override `1080x2400`
- `adb shell wm density`: physical density `450`
- Screenshot: [portrait Timeline evidence](assets/SM-G998W-portrait.png)

The values above are operator-recorded from the command output below; the
physical device was not resized or re-densitized.

## Validation commands

Build gates:

```text
./gradlew testDebugUnitTest          PASS
./gradlew compileDebugKotlin         PASS
./gradlew lintDebug                  PASS
./gradlew assembleDebug              PASS
./gradlew assembleDebugAndroidTest   PASS
```

Device execution:

```text
adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.jujidaw.app.test/androidx.test.runner.AndroidJUnitRunner
```

Result: **21/21 tests passed** on 2026-07-30 after both APK installs.

Captured device context:

```text
List of devices attached
R5CR31WJ5VX            device usb:1-4 product:p3qcsx model:SM_G998W device:p3q transport_id:1

15
35
Physical size: 1440x3200
Override size: 1080x2400
Physical density: 450
orientation=0, logicalFrame=[0, 0, 1080, 2400], physicalFrame=[0, 0, 1440, 3200]
```

## Automated evidence

The deterministic Compose suite covers opening/measurement, grid/ruler/
playhead visibility, zoom through 500%, horizontal scroll, captured clip
movement with atomic undo, resize cancellation, Delete/Trash for a short clip,
selector isolation and selection, and the live zoom/snap/Delete indicators.
The suite also verifies extreme track clamping and invalid public inputs.

This is UI/instrumentation evidence only; it does not claim native audio
playback or recording.

## Not run in this profile

- Landscape on this physical device was not run.
- A normal clip-placement gesture was not run as a manual scenario.
- A completed normal resize gesture was not run as a manual scenario.
- Multi-delete by dragging across several clips was not run.
- Long playback/playhead observation was not run; the test fixture only checks
  deterministic playhead visibility after seeking and zooming.
- Small-phone mdpi/xhdpi, standard-phone xxhdpi, high-density xxxhdpi, and
  tablet xhdpi/xxhdpi profiles require AVDs. Each pending profile still needs
  portrait and landscape runs plus the same scenario checklist.
