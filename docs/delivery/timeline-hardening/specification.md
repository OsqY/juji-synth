# Timeline Hardening — Specification

Task: `timeline-hardening`
Baseline: `2b350d6`
Current verified commit: `HEAD` (M23 audit commit; PR #1 merge remains `72fb813`)

## Objective

Close the remaining verification gaps for resize and Delete, harden loaded
audio data and temporal arithmetic, then add an explicit way to re-enable
playhead following after manual Timeline interaction.

## Scope and constraints

In scope: M17–M23 follow-up only. Out of scope: full-width rendering,
changes to `app/src/main/cpp/oboe`, changes to `.commandcode/`, and external
PR/Linear/Notion writes without explicit authorization. Musical state remains in
ticks; pixels are only for rendering/hit detection; one gesture is one history
transaction; viewport virtualization remains mandatory.

## Acceptance criteria

- `AC-F1`: Move near either edge scrolls gradually in the correct direction and stops outside the edge zone.
- `AC-F2`: Auto-scroll clamps to `0..maxScrollX`, works at 100%/500% zoom, preserves musical mapping, and supports row changes.
- `AC-F3`: A complete auto-scrolled move creates one `MoveClipsCommand`; undo restores the initial state.
- `AC-F4`: Release, cancel, pinch, and tool change stop auto-scroll without committing cancelled work.
- `AC-V1`: Delete, zoom, snap, playhead, resize, and mute indicators reflect effective state without intercepting input.
- `AC-D1`: Required device/density scenarios have reproducible evidence; unavailable profiles are marked skipped.
- `AC-P1`: Visible clip/grid/ruler work remains bounded by the viewport and any performance claim has before/after measurements.
- `AC-C1`: Technical docs describe the current tick/viewport architecture and constraints.
- `AC-O1`: Final audit covers original acceptance, security, rollback, Oboe exclusion, and authorized external handoff.
- `AC-R1`: Audio clip paths are normalized to project-relative paths before persistence.
- `AC-R2`: Autosave copies/reloads audio clips across active-project and autosave roots.
- `AC-R3`: Rename and failed-save paths preserve data and never publish false success.
- `AC-R4`: All clips captured by a multi-clip move remain composed during viewport auto-scroll.
- `AC-Q1`: Successful left/right resize gestures are covered through Compose and each produces one undo/redo transaction.
- `AC-Q2`: One Delete stroke can remove several short clips, deduplicates crossings, and undoes/redoes atomically.
- `AC-S1`: Invalid audio offsets, fades, gains, and project-external paths are rejected before native loading.
- `AC-S2`: Clip-end and export-duration calculations cannot wrap to invalid negative values or reach native export after overflow.
- `AC-F5`: Manual Timeline interaction disables playhead following and an explicit control re-enables it.
- `AC-O2`: Follow-up commits, validation, independent reviews, security review, and residual risks are recorded before handoff.

## Baseline evidence

R1 `e1c2f71` (14/14 device, review PASS); R2 `8f25d30` (16/16, PASS);
R3 `5d93af4` (18/18, PASS); R4 `1c7e6e2` (19/19, PASS).

The original PR is merged and its external handoff is complete. The broader
density/orientation matrix remains explicitly deferred; API 35 is the connected
gate for this follow-up. API 37 runner compatibility is infrastructure backlog.
