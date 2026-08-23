# DAW UI Rework

## Objective

Turn the current label-heavy Android interface into a compact, content-first
music workstation. The visual direction is Ableton-inspired: neutral industrial
surfaces, one strong yellow focus color, category color only where it carries
meaning, and icon-led controls whose accessible names remain available to
TalkBack and tests.

## Baseline evidence

Physical SM-G998W captures on 2026-08-23 show:

- Timeline controls occupy roughly the first quarter of the phone before the
  arrangement canvas begins.
- Seven persistent navigation labels and a large selected pill compete with the
  active workspace on every primary screen.
- Mixer, Project, and Sequencer leave large inactive regions while their
  controls are concentrated at the top.
- Synth repeats nested panel titles and borders; Pads and Keys use labels where
  spatial position already identifies the control.

## Acceptance criteria

- `AC-D1`: The Timeline opens content-first; advanced loop, punch, swing, zoom,
  and automation controls remain available but do not precede the canvas by
  default.
- `AC-D2`: Primary navigation is compact and icon-led. The active destination is
  conveyed by accent and a short label without seven persistent text labels.
- `AC-D3`: Transport is global, visually consistent, and appears exactly once on
  every primary screen.
- `AC-D4`: Timeline, Mixer, Synth, Pads, Keys, Sequencer, and Project each make
  their music/task surface the visual subject rather than their labels or
  container chrome.
- `AC-D5`: Repeated titles, decorative borders, and redundant labels are removed
  while necessary values, pad/track identities, and destructive-action clarity
  remain.
- `AC-Q1`: Every phase passes the repository Gradle gates and relevant connected
  Compose tests on an available API 35 device.
- `AC-Q2`: Every phase receives independent adversarial review with no open
  blocking findings.
- `AC-A1`: Interactive icons retain meaningful content descriptions, selected
  state, readable contrast, and at least a 44dp touch target.
- `AC-P1`: Timeline rendering remains viewport-virtualized and all musical state
  remains tick-based.
- `AC-S1`: No audio engine, persistence format, dependency, permission, or Oboe
  submodule change is part of the visual rework.

## Scope

Compose UI, theme usage, layout hierarchy, iconography, accessibility semantics,
and focused layout/semantics tests.

## Out of scope

New DAW features, transport behavior changes, audio changes, project migrations,
and a pixel-for-pixel Ableton copy.
