package com.jujidaw.project

import com.jujidaw.model.SynthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * App-level bridge for pad runtime state between [PadsViewModel]
 * (which owns the cached pad params because the engine has no getters) and
 * [ProjectAutosave] (which captures/loads state from the UI thread without
 * a ViewModel reference).
 *
 * The store holds a 32-entry list (bank A then bank B). PadsViewModel writes
 * the authoritative snapshot whenever a pad is imported/edited; ProjectAutosave
 * reads it when saving and writes it when loading so the ViewModel can sync
 * (including after a fresh ViewModel is created post-autoLoad).
 */
object PadSessionStore {
    private val _state: MutableStateFlow<List<PadSettings>> =
        MutableStateFlow(emptyList())
    val state: StateFlow<List<PadSettings>> = _state

    fun snapshot(): List<PadSettings> = _state.value

    /** Replace the whole 32-entry list. */
    fun set(pads: List<PadSettings>) {
        _state.value = pads
    }

    /** Update a single pad by global index (0..31), growing the list as needed. */
    fun setPad(
        globalIndex: Int,
        settings: PadSettings,
    ) {
        _state.update { current ->
            val size = maxOf(current.size, NUM_PADS)
            val list = current.padTo(size) { PadSettings() }.toMutableList()
            if (globalIndex in 0 until size) {
                list[globalIndex] = settings
            }
            list
        }
    }

    fun clear() {
        _state.value = emptyList()
    }

    private const val NUM_PADS = 32

    private fun <T> List<T>.padTo(
        size: Int,
        fill: (Int) -> T,
    ): List<T> {
        if (this.size >= size) return this
        val result = this.toMutableList()
        for (i in this.size until size) result.add(fill(i))
        return result
    }
}

/**
 * App-level bridge for the complete state of synth-mode pads. Native pad
 * synths deliberately have no bulk getter because the audio thread owns their
 * live DSP state, so the UI keeps the authoritative serializable snapshots.
 *
 * Keys are global pad indexes (0..31): Bank A occupies 0..15 and Bank B
 * occupies 16..31.
 */
object PadSynthSessionStore {
    private const val NUM_PADS = 32

    private val _state = MutableStateFlow<Map<Int, SynthState>>(emptyMap())
    val state: StateFlow<Map<Int, SynthState>> = _state

    fun snapshot(): Map<Int, SynthState> = _state.value

    fun setPadState(globalIndex: Int, synthState: SynthState) {
        if (globalIndex !in 0 until NUM_PADS) return
        _state.update { it + (globalIndex to synthState) }
    }

    fun replace(states: Map<Int, SynthState>) {
        _state.value = states.filterKeys { it in 0 until NUM_PADS }
    }

    fun clear() {
        _state.value = emptyMap()
    }
}

/** The pad currently selected for performance/editing, addressed globally (0..31). */
object PadSelectionStore {
    private val _selectedPad = MutableStateFlow(0)
    val selectedPad: StateFlow<Int> = _selectedPad
    private val _selectionEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val selectionEvents = _selectionEvents.asSharedFlow()

    fun select(globalIndex: Int) {
        if (globalIndex in 0 until 32) {
            _selectedPad.value = globalIndex
            _selectionEvents.tryEmit(globalIndex)
        }
    }
}
