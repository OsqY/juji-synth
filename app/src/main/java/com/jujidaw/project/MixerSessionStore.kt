package com.jujidaw.project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** App-level bridge for mixer controls that the native engine cannot bulk-read. */
object MixerSessionStore {
    private val _state = MutableStateFlow(MixerState())
    val state: StateFlow<MixerState> = _state

    fun snapshot(): MixerState = _state.value

    fun set(state: MixerState) {
        _state.value = state
    }

    fun update(transform: (MixerState) -> MixerState) {
        _state.update(transform)
    }

    fun updateTrack(
        trackIndex: Int,
        transform: (TrackState) -> TrackState,
    ) {
        if (trackIndex !in 0 until 16) return
        update { state ->
            state.copy(
                tracks = state.tracks.mapIndexed { index, track ->
                    if (index == trackIndex) transform(track) else track
                },
            )
        }
    }

    fun updateBus(
        busIndex: Int,
        transform: (BusState) -> BusState,
    ) {
        update { state ->
            when (busIndex) {
                0 -> state.copy(busA = transform(state.busA))
                1 -> state.copy(busB = transform(state.busB))
                else -> state
            }
        }
    }

    fun updateInsertParam(
        trackIndex: Int,
        slotIndex: Int,
        paramId: Int,
        value: Float,
    ) {
        updateTrack(trackIndex) { track ->
            track.copy(
                insertFx = track.insertFx.map { slot ->
                    if (slot.slotIndex == slotIndex) {
                        slot.copy(params = slot.params + (paramId to value))
                    } else {
                        slot
                    }
                },
            )
        }
    }

    fun clear() {
        _state.value = MixerState()
    }
}
