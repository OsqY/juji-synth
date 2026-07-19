package com.jujidaw.ui.timeline

/** Bounded, session-only undo/redo history for atomic timeline edits. */
internal class TimelineEditHistory<T>(
    private val capacity: Int = 100,
) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun record(before: T) {
        if (capacity <= 0) return
        undoStack.addLast(before)
        while (undoStack.size > capacity) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: T): T? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return previous
    }

    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
