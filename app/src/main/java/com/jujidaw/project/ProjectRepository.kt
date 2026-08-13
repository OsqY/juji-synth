package com.jujidaw.project

import android.content.Context
import android.net.Uri
import com.jujidaw.audio.AudioConverter
import com.jujidaw.audio.SynthEngine
import com.jujidaw.engine.TransportController
import com.jujidaw.model.AudioClip
import com.jujidaw.model.TransportPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.io.IOException

/**
 * Summary of a saved project shown in the project browser list.
 */
data class ProjectInfo(
    val name: String,
    val lastModified: Long,
    val fileSize: Long
)

/**
 * Repository for project save/load/delete/rename and WAV export.
 *
 * Projects are stored on external files storage under
 * `getExternalFilesDir(null)/projects/<projectName>/project.json`.
 * Audio samples are copied to `projects/<projectName>/samples/`.
 *
 * ### Public API
 * - [listProjects] — scan the projects directory and return summaries.
 * - [saveProject] — serialise [Project] to JSON; copy referenced samples.
 * - [loadProject] — parse JSON and return a [Project] value.
 * - [deleteProject] — remove the project directory recursively.
 * - [renameProject] — rename the project directory and update JSON.
 * - [importAudioClip] — copy a content-URI audio file into the project samples dir.
 * - [exportMix] — render the full arrangement to a stereo WAV file.
 * - [exportStems] — render each track to a separate WAV file.
 *
 * ### Assumptions
 * - The project directory layout is: `projects/<name>/project.json` and
 *   `projects/<name>/samples/` with flat file names.
 * - All audio clips store paths relative to the project directory.
 * - `[SynthEngine]` is already initialised before calling [loadProject].
 * - [exportMix] and [exportStems] rely on real-time playback + engine recording
 *   as a fallback. Proper offline render requires future JNI additions.
 */
class ProjectRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private val projectsDir: File
        get() = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve("projects")
            .apply { mkdirs() }

    private val appFilesDirectory: File
        get() = context.getExternalFilesDir(null) ?: context.filesDir

    // ── Project CRUD ────────────────────────────────────────────────

    /** Return all saved projects, newest first. */
    fun listProjects(): List<ProjectInfo> {
        val dir = projectsDir
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && File(it, "project.json").exists() }
            ?.map { dir ->
                val jsonFile = File(dir, "project.json")
                ProjectInfo(
                    name = dir.name,
                    lastModified = jsonFile.lastModified(),
                    fileSize = jsonFile.length()
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    /**
     * Save [project] as JSON.
     *
     * Referenced audio samples are copied into the project's samples/
     * subdirectory. The [Project.samplePaths] list is updated with
     * relative paths.
     */
    suspend fun saveProject(project: Project, sourceProjectName: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val projectDir =
                ProjectPathPolicy.projectDirectory(projectsDir, project.name)
                    ?: return@withContext Result.failure(IOException("Invalid project name"))
            projectDir.mkdirs()
            val sourceDir = if (sourceProjectName == null) {
                projectDir
            } else {
                ProjectPathPolicy.projectDirectory(projectsDir, sourceProjectName)
            }
            if (sourceDir == null || !sourceDir.isDirectory) {
                return@withContext Result.failure(IOException("Invalid source project name"))
            }
            val normalizedProject =
                normalizeAudioPaths(project, sourceDir, projectDir).getOrElse {
                    return@withContext Result.failure(it)
                }

            // Copy referenced samples into the project samples directory.
            projectDir.resolve("samples").mkdirs()
            for (clip in normalizedProject.arrangement.clips.filterIsInstance<AudioClip>()) {
                val srcFile = ProjectPathPolicy.audioFile(sourceDir, clip.audioFilePath)
                    ?: return@withContext Result.failure(IOException("Audio path is outside the source project"))
                val destFile = ProjectPathPolicy.audioFile(projectDir, clip.audioFilePath)
                    ?: return@withContext Result.failure(IOException("Audio path is outside the target project"))
                destFile.parentFile?.mkdirs()
                if (srcFile.canonicalFile != destFile.canonicalFile) {
                    srcFile.copyTo(destFile, overwrite = true)
                }
            }

            val jsonString = json.encodeToString(normalizedProject)
            File(projectDir, "project.json").writeText(jsonString)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Load a saved project by name. Returns [IOException] if not found. */
    suspend fun loadProject(name: String): Result<Project> = withContext(Dispatchers.IO) {
        try {
            val projectDir =
                ProjectPathPolicy.projectDirectory(projectsDir, name)
                    ?: return@withContext Result.failure(IOException("Invalid project name"))
            val jsonFile = projectDir.resolve("project.json")
            if (!jsonFile.exists()) {
                return@withContext Result.failure(IOException("Project not found: $name"))
            }
            val jsonString = jsonFile.readText()
            val project = json.decodeFromString<Project>(jsonString)
            if (project.name != name) {
                return@withContext Result.failure(IOException("Project name does not match its directory"))
            }
            validateLoadedAudio(projectDir, project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Delete a saved project and all its files. */
    suspend fun deleteProject(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val projectDir =
                ProjectPathPolicy.projectDirectory(projectsDir, name)
                    ?: return@withContext Result.failure(IOException("Invalid project name"))
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Rename a project. Fails if a project with [newName] already exists. */
    suspend fun renameProject(oldName: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val oldDir =
                ProjectPathPolicy.projectDirectory(projectsDir, oldName)
                    ?: return@withContext Result.failure(IOException("Invalid project name"))
            val newDir =
                ProjectPathPolicy.projectDirectory(projectsDir, newName)
                    ?: return@withContext Result.failure(IOException("Invalid project name"))
            if (!oldDir.exists()) {
                return@withContext Result.failure(IOException("Project not found: $oldName"))
            }
            if (newDir.exists()) {
                return@withContext Result.failure(IOException("Project already exists: $newName"))
            }
            val oldJsonFile = oldDir.resolve("project.json")
            val originalJson = oldJsonFile.readBytes()
            val jsonString = originalJson.toString(Charsets.UTF_8)
            val project = json.decodeFromString<Project>(jsonString)
            val normalized =
                normalizeAudioPaths(project, oldDir, oldDir).getOrElse {
                    return@withContext Result.failure(it)
                }
            val suffix = System.nanoTime().toString()
            val stagedJson = oldDir.resolve("project.json.rename.$suffix.tmp")
            val backupJson = oldDir.resolve("project.json.rename.$suffix.bak")
            try {
                stagedJson.writeText(json.encodeToString(normalized.copy(name = newName)))
            } catch (e: Exception) {
                stagedJson.delete()
                throw e
            }
            if (!oldJsonFile.renameTo(backupJson)) {
                stagedJson.delete()
                return@withContext Result.failure(IOException("Unable to stage project metadata"))
            }
            if (!stagedJson.renameTo(oldJsonFile)) {
                val restored = backupJson.renameTo(oldJsonFile)
                stagedJson.delete()
                if (!restored) {
                    throw IOException("Rename failed and metadata rollback was unsuccessful")
                }
                return@withContext Result.failure(IOException("Unable to install renamed project metadata"))
            }
            if (!oldDir.renameTo(newDir)) {
                val restored = runCatching {
                    if (!oldJsonFile.delete() || !backupJson.renameTo(oldJsonFile)) {
                        throw IOException("Unable to restore original project metadata")
                    }
                }.isSuccess
                if (!restored) {
                    throw IOException("Rename failed and metadata rollback was unsuccessful")
                }
                return@withContext Result.failure(IOException("Unable to rename project"))
            }
            newDir.resolve(backupJson.name).delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Audio Import ────────────────────────────────────────────────

    /**
     * Copy an audio file from a content URI into the project's samples
     * directory and load it into the engine.
     *
     * @return the project-relative file path on success.
     */
    suspend fun importAudioClip(uri: Uri, projectName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val projectDir =
                ProjectPathPolicy.projectDirectory(projectsDir, projectName)
                    ?: return@withContext Result.failure(IOException("Invalid project name"))
            val samplesDir = projectDir.resolve("samples").apply { mkdirs() }
            val ext = context.contentResolver.getType(uri)?.let { type ->
                when {
                    type.contains("wav", ignoreCase = true) -> ".wav"
                    type.contains("mp3", ignoreCase = true) -> ".mp3"
                    type.contains("flac", ignoreCase = true) -> ".flac"
                    type.contains("ogg", ignoreCase = true) -> ".ogg"
                    type.contains("aiff", ignoreCase = true) -> ".aiff"
                    else -> ".wav"
                }
            } ?: ".wav"

            val outFile = File(samplesDir, "clip_${System.currentTimeMillis()}$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IOException("Cannot open content URI"))

            // Notify the engine about the new audio clip.
            SynthEngine.loadAudioClip(outFile.nameWithoutExtension, outFile.absolutePath)

            Result.success(ProjectPathPolicy.relativeAudioPath(projectDir, outFile.absolutePath) ?: return@withContext Result.failure(IOException("Cannot normalize imported audio path")))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Decode and package a pad sample directly under the active project. */
    suspend fun importPadSample(uri: Uri, projectName: String, padIndex: Int): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (padIndex !in 0..31) return@withContext Result.failure(IOException("Invalid pad index"))
                val projectDir =
                    ProjectPathPolicy.projectDirectory(projectsDir, projectName)
                        ?: return@withContext Result.failure(IOException("Invalid project name"))
                val wavFile = projectDir.resolve("samples/pad_${padIndex}_${System.currentTimeMillis()}.wav")
                wavFile.parentFile?.mkdirs()
                if (!AudioConverter.convertToWav(context, uri, wavFile.absolutePath)) {
                    wavFile.delete()
                    return@withContext Result.failure(IOException("Cannot decode audio format"))
                }
                if (!SynthEngine.loadSampleToPad(wavFile.absolutePath, padIndex)) {
                    wavFile.delete()
                    return@withContext Result.failure(IOException("Audio file could not be loaded"))
                }
                Result.success(
                    ProjectPathPolicy.relativeAudioPath(projectDir, wavFile.absolutePath)
                        ?: return@withContext Result.failure(IOException("Cannot normalize imported pad path")),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun normalizeAudioPaths(
        project: Project,
        sourceDir: File,
        targetDir: File,
    ): Result<Project> {
        val relativePaths = mutableListOf<String>()
        val clips = project.arrangement.clips.map { clip ->
            if (clip !is AudioClip) return@map clip
            val relativePath = ProjectPathPolicy.relativeAudioPath(sourceDir, clip.audioFilePath)
                ?: return Result.failure(IOException("Audio path is outside the source project"))
            val sourceFile = ProjectPathPolicy.audioFile(sourceDir, relativePath)
                ?: return Result.failure(IOException("Audio path is outside the source project"))
            if (!sourceFile.isFile) return Result.failure(IOException("Audio file not found: $relativePath"))
            relativePaths += relativePath
            clip.copy(audioFilePath = relativePath)
        }
        val pads = project.pads.mapIndexed { index, pad ->
            if (pad.samplePath.isBlank()) return@mapIndexed pad
            val sourceFile = sequenceOf(
                ProjectPathPolicy.audioFile(sourceDir, pad.samplePath),
                ProjectPathPolicy.audioFile(appFilesDirectory, pad.samplePath)
                    ?.takeIf { File(pad.samplePath).isAbsolute },
            ).filterNotNull().firstOrNull { it.isFile }
                ?: return Result.failure(IOException("Pad sample not found: ${pad.samplePath}"))
            val relativePath = "samples/pad_${index}_${sourceFile.name}"
            val destination = ProjectPathPolicy.audioFile(targetDir, relativePath)
                ?: return Result.failure(IOException("Pad sample path is outside the target project"))
            destination.parentFile?.mkdirs()
            if (sourceFile.canonicalFile != destination.canonicalFile) {
                sourceFile.copyTo(destination, overwrite = true)
            }
            relativePaths += relativePath
            pad.copy(samplePath = relativePath)
        }
        return Result.success(
            project.copy(
                arrangement = project.arrangement.copy(clips = clips),
                pads = pads,
                samplePaths = relativePaths.distinct(),
            )
        )
    }

    private fun validateLoadedAudio(projectDir: File, project: Project): Result<Project> {
        val relativePaths = mutableListOf<String>()
        val clips = project.arrangement.clips.map { clip ->
            if (clip !is AudioClip) return@map clip
            val audioFile = ProjectPathPolicy.audioFile(projectDir, clip.audioFilePath)
                ?: return Result.failure(IOException("Audio path is outside the project"))
            if (!audioFile.isFile) {
                return Result.failure(IOException("Audio file not found: ${clip.audioFilePath}"))
            }
            val relativePath = ProjectPathPolicy.relativeAudioPath(projectDir, audioFile.absolutePath)
                ?: return Result.failure(IOException("Cannot normalize audio path: ${clip.audioFilePath}"))
            relativePaths += relativePath
            clip.copy(audioFilePath = relativePath)
        }
        val pads = project.pads.mapIndexed { index, pad ->
            if (pad.samplePath.isBlank()) return@mapIndexed pad
            val projectFile = ProjectPathPolicy.audioFile(projectDir, pad.samplePath)
                ?.takeIf { it.isFile }
            val legacyFile = if (File(pad.samplePath).isAbsolute) {
                ProjectPathPolicy.audioFile(appFilesDirectory, pad.samplePath)?.takeIf { it.isFile }
            } else {
                null
            }
            val sourceFile = projectFile ?: legacyFile
                ?: return Result.failure(IOException("Pad sample not found: ${pad.samplePath}"))
            val relativePath = if (projectFile != null) {
                ProjectPathPolicy.relativeAudioPath(projectDir, sourceFile.absolutePath)
            } else {
                val migratedPath = "samples/pad_${index}_${sourceFile.name}"
                val destination = ProjectPathPolicy.audioFile(projectDir, migratedPath)
                    ?: return Result.failure(IOException("Pad sample path is outside the project"))
                destination.parentFile?.mkdirs()
                if (sourceFile.canonicalFile != destination.canonicalFile) {
                    sourceFile.copyTo(destination, overwrite = true)
                }
                migratedPath
            } ?: return Result.failure(IOException("Cannot normalize pad sample: ${pad.samplePath}"))
            relativePaths += relativePath
            pad.copy(samplePath = relativePath)
        }
        return Result.success(
            project.copy(
                arrangement = project.arrangement.copy(clips = clips),
                pads = pads,
                samplePaths = relativePaths.distinct(),
            )
        )
    }

    // ── WAV Export ──────────────────────────────────────────────────

    /**
     * Export the full arrangement mix to a stereo WAV file.
     *
     * Uses the engine's offline render path which processes audio at maximum
     * speed without real-time Oboe playback.
     */
    suspend fun exportMix(
        transportController: TransportController,
        outputPath: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val arrangement = transportController.arrangement
            val bpm = transportController.transportState.tempoBpm
            val timing = calculateProjectExportTiming(arrangement, bpm)
                .getOrElse { return@withContext Result.failure(it) }

            onProgress(0f)

            // Seek to start.
            transportController.seek(TransportPosition())

            // Start offline render.
            if (!SynthEngine.startOfflineRender(outputPath, timing.totalSamples)) {
                return@withContext Result.failure(IOException("Failed to start offline render"))
            }
            onProgress(0.1f)

            // Run transport through the arrangement.
            transportController.play()

            // Wait for the arrangement to finish.
            delay(timing.waitMillis)
            onProgress(0.8f)

            transportController.stop()

            // Stop the engine to ensure the audio callback is done before
            // writing the WAV file from the captured buffer.
            SynthEngine.stop()

            // Stop offline render (writes WAV file).
            SynthEngine.stopOfflineRender()
            onProgress(1f)

            // Restart the engine.
            SynthEngine.start()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export each mixer track as a separate WAV stem.
     *
     * Uses the engine's per-track offline render which solos only the
     * target track during render.
     */
    suspend fun exportStems(
        transportController: TransportController,
        outputDir: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            File(outputDir).mkdirs()
            val trackCount = 16
            val arrangement = transportController.arrangement
            val bpm = transportController.transportState.tempoBpm
            val timing = calculateProjectExportTiming(arrangement, bpm)
                .getOrElse { return@withContext Result.failure(it) }

            transportController.seek(TransportPosition())

            for (track in 0 until trackCount) {
                val stemPath = File(outputDir, "stem_track_${track + 1}.wav").absolutePath
                if (!SynthEngine.startOfflineRenderForTrack(stemPath, track, timing.totalSamples)) {
                    continue
                }

                transportController.play()
                delay(timing.waitMillis)
                transportController.stop()

                SynthEngine.stop()
                SynthEngine.stopOfflineRender()
                SynthEngine.start()
                onProgress((track + 1).toFloat() / trackCount)
            }

            onProgress(1f)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
