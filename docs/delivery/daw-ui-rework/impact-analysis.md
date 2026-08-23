# Impact Analysis

| Phase | Surface | Main risk | Evidence |
| --- | --- | --- | --- |
| 1 | App shell + Timeline chrome | Hiding an action or shrinking a touch target | Semantics/bounds test + device captures |
| 2 | Mixer | Breaking fader/knob gestures while changing density | Existing gestures + connected smoke test |
| 3 | Synth | Losing parameter grouping while removing nested chrome | Parameter semantics + device capture |
| 4 | Pads + Keys | Reducing performance target size or note clarity | Touch bounds + multi-touch smoke test |
| 5 | Sequencer + Project + Help/Settings | Obscuring destructive/project actions | Compose tests + device capture |
| 6 | Adaptive integration | Portrait improvements regressing landscape | Physical portrait + API 35 landscape suite |

The shell owns destination selection and global transport. Each destination
continues to own its existing state and gestures. Timeline coordinate,
virtualization, history, and autosave contracts are unchanged. Rollback is per
focused phase. Existing dirty audio/project files and `app/src/main/cpp/oboe`
are excluded.
