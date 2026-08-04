# Timeline device validation

This directory is the M12 evidence set. Each profile records the exact device
or AVD, Android/API, size/density, orientation, commands, test result, and
known gaps. Physical-device density and resolution are never changed by the
validation workflow.

## Matrix

| Profile | Status | Evidence |
| --- | --- | --- |
| SM-G998W, Android 15 | Validated: portrait | [profile](SM-G998W-android15.md) |
| Small phone, mdpi/xhdpi | Pending AVD (portrait + landscape) | No emulator configured |
| Standard phone, xxhdpi | Pending AVD (portrait + landscape) | No emulator configured |
| High-density phone, xxxhdpi | Pending AVD (portrait; landscape optional) | No emulator configured |
| Tablet, xhdpi/xxhdpi | Pending AVD (portrait + landscape) | No emulator configured |

The pending profiles are intentionally not marked green without device
evidence. M12 can be extended with AVDs without changing timeline code.
