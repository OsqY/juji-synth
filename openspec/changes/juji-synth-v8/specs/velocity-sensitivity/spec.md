## REMOVED Requirements

### Requirement: Note velocity based on press speed
**Reason**: Incompatible with ADSR envelope synthesis. Retriggering a note on release creates a note that never receives noteOff, causing the envelope to stay in sustain forever. All notes now play at default velocity 100.

**Migration**: Remove `notePressTimes` state from MainSynthScreen. Simplify `onNoteOff` to just `SynthEngine.noteOff(note)`. No velocity calculation needed.
