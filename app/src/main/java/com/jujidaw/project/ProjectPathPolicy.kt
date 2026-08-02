package com.jujidaw.project

import java.io.File

/** Canonical path checks for project directories and referenced audio files. */
internal object ProjectPathPolicy {
    fun projectDirectory(projectsDirectory: File, name: String): File? {
        if (name.isBlank() || name == "." || name == ".." || name.any { it == '/' || it == '\\' }) {
            return null
        }
        return runCatching {
            val root = projectsDirectory.canonicalFile
            val candidate = root.resolve(name).canonicalFile
            candidate.takeIf { it.parentFile == root }
        }.getOrNull()
    }

    fun audioFile(projectDirectory: File, path: String): File? =
        runCatching {
            val root = projectDirectory.canonicalFile
            val candidate =
                if (File(path).isAbsolute) File(path) else root.resolve(path)
            val canonical = candidate.canonicalFile
            canonical.takeIf { it != root && canonical.path.startsWith(root.path + File.separator) }
        }.getOrNull()

    fun relativeAudioPath(projectDirectory: File, path: String): String? =
        audioFile(projectDirectory, path)
            ?.toPath()
            ?.let { projectDirectory.canonicalFile.toPath().relativize(it).toString() }
            ?.replace(File.separatorChar, '/')

    fun appAudioFile(appFilesDirectory: File, projectName: String?, path: String): File? {
        val projectDirectory =
            projectName?.let { projectDirectory(appFilesDirectory.resolve("projects"), it) }
        return projectDirectory?.let { audioFile(it, path) }
    }
}
