package com.jujidaw.project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** App-level bridge for mixer controls that the native engine cannot bulk-read. */
object MixerSessionStore {
    private val _state = MutableStateFlow(MixerState())
    val state: StateFlow<MixerState> = _state

    fun snapshot(): MixerState = _state.value

    fun set(state: MixerState) {
        _state.value = state
    }

    fun clear() {
        _state.value = MixerState()
    }
}
