## Context

Juji-synth has a `MidiController` that handles USB MIDI input with hardcoded CC mappings (CC1 → mod wheel, CC64 → sustain). Users want to map ANY visible parameter to ANY MIDI CC. The standard solution is MIDI Learn — a workflow where the user selects a control, then presses the desired MIDI button/knob, and the mapping is stored.

## Goals / Non-Goals

**Goals:**
- Tap a button to enter learn mode, tap a control, press a MIDI button, done
- Persist mappings across app restarts
- Visual feedback during learn mode (highlights, selection, mapped indicators)
- Clear individual or all mappings

**Non-Goals:**
- No MIDI pitch bend learning (pitch bend is always global)
- No multi-channel mapping (always omni mode for now)
- No complex mapping curves (linear only)

## Decisions

### 1. MIDI Learn State Machine

```
[Idle] → tap Learn button → [Learn Active]
                                   ↓
                          tap a control → [Control Selected]
                                              ↓
                                   receive MIDI CC → [Mapping Stored]
                                                          ↓
                                                      back to [Learn Active]
                                   tap Learn button → [Idle]
```

### 2. Mapping Data Model

```kotlin
data class MidiMapping(
    val ccNumber: Int,        // MIDI CC number (0-127)
    val paramId: Int,         // JNI param ID (0-62)
    val minValue: Float = 0f, // CC min maps to this value
    val maxValue: Float = 1f, // CC max maps to this value
)
```

### 3. Mapping Storage

DataStore-based. Key: `"midi_mappings"`, value: JSON-serialized `List<MidiMapping>`.

### 4. Visual Feedback

- **Learn mode active**: All knobs get a pulsing amber border (animated alpha)
- **Control selected**: Knob gets a bright white border
- **Control mapped**: Small green dot in corner of knob (visible in normal mode)
- **Mapping stored**: Brief green flash animation

### 5. Learn Mode Activation

- MIDI Learn button in toolbar (between SAVE and HELP)
- Toggleable: tap to enter, tap to exit
- When exiting, any pending selection is cancelled

### 6. Mapping Application

When a MIDI CC arrives:
1. Check if `ccNumber` exists in the mapping table
2. If yes: apply the mapped paramId with the scaled value
3. If no: fall through to default CC handling (CC1 = mod wheel, CC64 = sustain, etc.)

### 7. Integration with Panels

Each panel needs to know which controls are "mappable" and accept learn-mode selection. Add a `selectedParamId: Int?` state to the learn workflow. When a knob is tapped in learn mode, set `selectedParamId` to that knob's param ID. The next MIDI CC event creates a mapping.

## Risks / Trade-offs

[Risk] Learn mode interferes with normal knob interaction
→ Mitigation: In learn mode, tapping a knob SELECTS it (doesn't change its value). Only drags change value.

[Risk] Mapped controls might conflict with hardcoded mappings
→ Mitigation: Mapped CCs override hardcoded CCs. Document this behavior.

[Risk] Persistence format changes break saved mappings
→ Mitigation: Version the mapping format. If deserialization fails, clear mappings.

## Migration Plan

1. Create MidiMappingStore (persistence)
2. Add learn mode state to MainSynthScreen
3. Add learn button to toolbar
4. Add visual feedback to knobs (learn highlight, mapped dot)
5. Wire MidiController to check mappings before default handling
6. Build and test