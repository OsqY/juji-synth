package com.jujidaw.project

/**
 * Convert pre-pad-trigger pattern notes at the project boundary. A missing
 * padIndex is represented by -1 in the serialised model.
 */
internal fun migrateLegacyPatternNotes(project: Project): Project =
    project.copy(
        patterns = project.patterns.map { pattern ->
            pattern.copy(
                notes = pattern.notes.map { note ->
                    if (note.padIndex >= 0) note else note.copy(padIndex = note.note % 16)
                },
            )
        },
    )
