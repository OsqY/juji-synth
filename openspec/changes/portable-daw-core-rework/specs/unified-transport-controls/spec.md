# Spec: Unified Transport Controls

## Requirements

- **R1** Exactly ONE global transport bar SHALL exist, rendered by
  `MainScreen` (the `PersistentTransportBar`) and visible across all tabs in
  both orientations.
- **R2** `TimelineScreen.TransportStrip` and `SequencerScreen.SequencerTopBar`
  SHALL NOT render their own Play/Record/Reset buttons. They SHALL delegate
  transport control to the global transport bar.
- **R3** There SHALL be exactly one Record button (the global transport
  bar's), wired to `TransportController.setRecording`.
- **R4** The global transport bar SHALL be visible and ergonomic in both
  portrait and landscape; in landscape it SHALL NOT steal height from the
  navigation rail (see `landscape-navigation`).

## Scenarios

### S1: Single record button

- **Given** the app is open on any tab
- **When** the user looks for a record button
- **Then** exactly one record button is visible (in the global transport bar).

### S2: Timeline delegates to global transport

- **Given** the Timeline tab is open
- **When** the user presses Play via the global transport bar
- **Then** the timeline plays; the timeline screen does not render its own
  transport strip.
