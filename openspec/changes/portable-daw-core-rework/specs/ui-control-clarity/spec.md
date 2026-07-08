# Spec: UI Control Clarity

## Requirements

- **R1** The `L-`/`L+`/`I-`/`O+`/`P` cryptic micro-buttons in
  `TimelineScreen.TransportStrip` SHALL be replaced with labeled and/or
  iconographic controls using consistent vocabulary: "Loop Start", "Loop End",
  "Punch In", "Punch Out", "Punch Enable".
- **R2** Loop/punch controls SHALL provide help text (tooltip or long-press
  hint) describing what each does.
- **R3** Punch control labels SHALL be symmetric and unambiguous
  (e.g. "Punch In" / "Punch Out"), not asymmetric placeholders like "I-"/"O+".
- **R4** The Time/BPM display SHALL NOT concatenate "Time" and "BPM" into a
  single run-on label on narrow widths; the two pieces of information SHALL be
  visually separated with adequate spacing or a divider, and SHALL wrap or
  truncate gracefully on narrow widths.
- **R5** No single-letter unlabeled button SHALL exist in the transport UI
  without an icon or tooltip.

## Scenarios

### S1: Replaced cryptic labels

- **Given** the Timeline tab is open
- **When** the user views the loop/punch controls
- **Then** the controls show "Loop Start", "Loop End", "Punch In", "Punch
  Out", "Punch Enable" (or equivalent icons with tooltips), not "L-", "L+",
  "I-", "O+", "P".

### S2: Time/BPM layout on narrow width

- **Given** a narrow phone screen in portrait
- **When** the transport bar renders Time and BPM
- **Then** the two values are visually separated and do not read as a single
  token like "1|1|2 BPM".
