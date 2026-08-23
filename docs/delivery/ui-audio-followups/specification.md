# UI and Audio Follow-ups

## Objective

Resolve five confirmed regressions without mixing their functional concerns:
synth presets become unstable, the Sequencer Piano grid collapses, the Workflow
Guide overflows in landscape, Timeline pad chips cannot audition sounds, and
PadClips show an unwanted decorative marker.

## Scope and acceptance

- `AC-F1`: Factory-preset filter settings produce finite, bounded output across
  the supported cutoff and resonance ranges.
- `AC-F2`: Piano mode displays a non-collapsed, scrollable note grid.
- `AC-F3`: Workflow Guide signal flow and section text remain inside the dialog.
- `AC-F4`: Pressing a Timeline pad chip starts that global pad; release or
  cancellation stops it; the gesture remains audition-only.
- `AC-F5`: PadClips no longer render the line-and-circle marker and retain their
  existing label, selection, move, and resize behavior.
- `AC-Q1`: Each phase passes the repository gates and an independent review
  before its focused commit.
- `AC-S1`: No dependency, permission, persistence, or Oboe submodule change.

## Out of scope

Preset retuning, new audio architecture, recording Timeline preview gestures,
Sequencer feature expansion, and other Timeline visual refinement.

## Evidence

The SM-G998W reproduced the Guide overflow and collapsed Piano grid. Static
tracing found selection-only Timeline chips and an unconditional PadClip marker.
The current native filter recurrence diverges for the reported presets because
the value used as damping is Q rather than inverse Q.
