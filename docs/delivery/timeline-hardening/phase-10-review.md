# M10 Review — Edge auto-scroll

State: pass; P2 coverage gap tracked for the later validation matrix.

Independent reviewer: `/root/m10_redesign_review` (fresh context).

Resolved findings:

- The explicit Job now stops at edge exit or a clamped boundary and restarts on
  edge re-entry; Finish/Cancel, pinch, tool change, and dispose cancel it.
- The Compose fixture derives its target from viewport bounds and verifies
  scroll movement plus one undo transaction.

Remaining non-blocking gap: explicit left/right coverage at both 100% and 500%,
row changes during auto-scroll, and cancellation/re-entry scenarios remain for
the device matrix phase.

Current evidence:

- Pure edge velocity and delta tests pass.
- Unit tests, Kotlin compilation, lint, and debug assembly pass.
- Focused M10 device test passes; full device suite passes 20/20.

Closure decision: PASS for M10. The P2 coverage gap is assigned to M12.

Oboe remains excluded.
