package com.jujidaw.ui.timeline

/**
 * Accumulates the clips crossed by one Delete gesture.
 *
 * Keeping IDs in insertion order makes the preview deterministic while the
 * set semantics ensure that crossing one clip repeatedly still produces one
 * atomic delete operation.
 */
internal class TimelineDeleteSession {
    private val deletedIds = linkedSetOf<String>()

    val ids: Set<String>
        get() = deletedIds.toSet()

    fun add(ids: Iterable<String>): Set<String> {
        deletedIds.addAll(ids)
        return this.ids
    }
}
