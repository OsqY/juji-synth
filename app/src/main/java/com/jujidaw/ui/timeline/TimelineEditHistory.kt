package com.jujidaw.ui.timeline

/** Bounded, session-only undo/redo history for atomic timeline edits. */
internal class TimelineEditHistory<T>(
    private val capacity: Int = 100,
) {
    private val undoStack = ArrayDeque<TimelineEditCommand<T>>()
    private val redoStack = ArrayDeque<TimelineEditCommand<T>>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    internal val undoCount: Int
        get() = undoStack.size

    internal val redoCount: Int
        get() = redoStack.size

    fun record(command: TimelineEditCommand<T>) {
        if (capacity <= 0) return
        undoStack.addLast(command)
        while (undoStack.size > capacity) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(): TimelineEditCommand<T>? {
        val command = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(command)
        return command
    }

    fun redo(): TimelineEditCommand<T>? {
        val command = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(command)
        return command
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
