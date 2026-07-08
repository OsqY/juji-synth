# Spec: Landscape Navigation

## Requirements

- **R1** In landscape, the navigation surface SHALL make all 7 tabs
  (Timeline, Mixer, Synth, Pads, Keys, Sequencer, Project) reachable without
  invisible scrolling.
- **R2** The landscape navigation rail SHALL provide a visible scroll
  affordance (scrollbar or peek indicator) OR be replaced by a compact rail
  - overflow drawer for tabs that do not fit.
- **R3** The global transport bar SHALL NOT share the same scrolled column as
  the navigation rail; the rail SHALL have its own dedicated width and height.
- **R4** Tab items SHALL remain readable (icon + label) at the landscape rail
  width; no item is clipped below the fold without an indicator that more
  items exist.

## Scenarios

### S1: All tabs reachable in landscape

- **Given** the device is in landscape on a typical phone screen
- **When** the user opens the app
- **Then** all 7 tabs are either visible in the rail or reachable via a
  visible overflow control/scrollbar — none is silently cut off.

### S2: Transport does not eat rail height

- **Given** landscape mode
- **When** the global transport bar is rendered
- **Then** the navigation rail has a dedicated column whose height is not
  reduced by the transport bar.
