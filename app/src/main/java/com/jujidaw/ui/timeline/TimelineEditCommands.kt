package com.jujidaw.ui.timeline

/** A completed timeline transaction with the exact states needed for undo/redo. */
internal sealed interface TimelineEditCommand<T> {
    val before: T
    val after: T
}

internal data class AddClipCommand<T>(
    val clipIds: Set<String>,
    override val before: T,
    override val after: T,
) : TimelineEditCommand<T>

internal data class MoveClipsCommand<T>(
    val clipIds: Set<String>,
    override val before: T,
    override val after: T,
) : TimelineEditCommand<T>

internal data class ResizeClipCommand<T>(
    val clipId: String,
    val previousStart: Long,
    val previousDuration: Long,
    val newStart: Long,
    val newDuration: Long,
    override val before: T,
    override val after: T,
) : TimelineEditCommand<T>

internal data class DeleteClipsCommand<T>(
    val clipIds: Set<String>,
    override val before: T,
    override val after: T,
) : TimelineEditCommand<T>

internal data class DuplicateClipsCommand<T>(
    val clipIds: Set<String>,
    override val before: T,
    override val after: T,
) : TimelineEditCommand<T>

internal data class PasteClipsCommand<T>(
    val clipIds: Set<String>,
    override val before: T,
    override val after: T,
) : TimelineEditCommand<T>

internal data class MuteClipsCommand<T>(
    val clipIds: Set<String>,
    override val before: T,
    override val after: T,
) : TimelineEditCommand<T>

internal data class RestoreTrashClipCommand<T>(
    val clipId: String,
    override val before: T,
    override val after: T,
) : TimelineEditCommand<T>

/** Fallback for arrangement settings and legacy edits outside the clip tools. */
internal data class ArrangementEditCommand<T>(
    val label: String,
    override val before: T,
    override val after: T,
) : TimelineEditCommand<T>
