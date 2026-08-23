# Impact Analysis

| Phase | Components | Contract and risk | Verification |
| --- | --- | --- | --- |
| 1 | `Filter.cpp`, native self-check | Shared synth and insert filter; wrong damping can emit non-finite audio | Sweep cutoff/resonance with bounded input |
| 2 | `SequencerScreen.kt`, Compose test | Layout only; preserve gestures and scrolling | Piano grid height assertion |
| 3 | `HelpScreen.kt`, Compose test | Layout only; preserve dialog scrolling and close action | Bounds assertions at device width |
| 4 | `TimelineScreen.kt`, Timeline Compose test | Global pad indexes 0..31; cancellation must release sound; no recording bus | Real touch down/up/cancel callbacks |
| 5 | `TimelineScreen.kt` | Visual deletion only; preserve clip interaction | Existing Timeline Compose suite plus device capture |

There are no data, migration, network, permission, or external integration
changes. Rollback is the focused phase commit revert. User-owned dirty files and
`app/src/main/cpp/oboe` remain untouched and unstaged.
