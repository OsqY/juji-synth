# Timeline Hardening — Specification

Task: `timeline-hardening`
Baseline: `2b350d6`
Current verified commit: `229f144`

## Objective

Finish modules 10–15 from `docs/plans/timeline-hardening-pending.md` before
new Timeline features: edge auto-scroll, indicators, device evidence,
performance evidence, technical documentation, and final audit.

## Scope and constraints

In scope: M10–M15 only. Out of scope: new features, full-width rendering,
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

## Baseline evidence

R1 `e1c2f71` (14/14 device, review PASS); R2 `8f25d30` (16/16, PASS);
R3 `5d93af4` (18/18, PASS); R4 `1c7e6e2` (19/19, PASS).

Open decisions: external tracker/PR authorization and availability of more
device profiles than SM-G998W Android 15.
