package com.jujidaw.project

import kotlinx.coroutines.flow.MutableStateFlow
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
