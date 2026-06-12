## MODIFIED Requirements

### Requirement: Re-trigger same note
When the same MIDI note is played again while it's still playing (or releasing), the system SHALL silence the previous instance before starting the new one.

#### Scenario: Same note pressed twice
- **WHEN** user presses C4 and releases it, then presses C4 again before the envelope finishes releasing
- **THEN** the second press plays cleanly without creating a second stuck voice

#### Scenario: Same note held then re-pressed
- **WHEN** user holds C4 and presses C4 again with a different finger
- **THEN** the first voice is silenced and a new voice plays C4 (re-trigger)

### Requirement: Release all matching voices
When noteOff is called for a MIDI note, the system SHALL release ALL voices currently playing that note.

#### Scenario: Two voices on same note
- **WHEN** two voices are accidentally playing the same MIDI note (edge case)
- **THEN** noteOff releases both voices
