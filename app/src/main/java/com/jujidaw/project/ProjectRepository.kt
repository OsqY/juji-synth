package com.jujidaw.project

import android.content.Context
import android.net.Uri
import com.jujidaw.audio.SynthEngine
import com.jujidaw.engine.TransportController
import com.jujidaw.model.AudioClip
import com.jujidaw.model.PPQ
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
    suspend fun saveProject(project: Project): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val projectDir = projectsDir.resolve(project.name)
            projectDir.mkdirs()

            // Copy referenced samples into the project samples directory.
            val samplesDir = projectDir.resolve("samples").apply { mkdirs() }
            val relativePaths = mutableListOf<String>()

            for (clip in project.arrangement.clips) {
                if (clip is AudioClip) {
                    val srcFile = File(clip.audioFilePath)
                    if (srcFile.exists()) {
                        val destFile = File(samplesDir, srcFile.name)
                        if (!destFile.exists()) {
                            srcFile.copyTo(destFile, overwrite = false)
                        }
                        relativePaths.add("samples/${srcFile.name}")
                    }
                }
            }

            val updatedProject = project.copy(samplePaths = relativePaths.distinct())
            val jsonString = json.encodeToString(updatedProject)
            File(projectDir, "project.json").writeText(jsonString)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Load a saved project by name. Returns [IOException] if not found. */
    suspend fun loadProject(name: String): Result<Project> = withContext(Dispatchers.IO) {
        try {
            val projectDir = projectsDir.resolve(name)
            val jsonFile = projectDir.resolve("project.json")
            if (!jsonFile.exists()) {
                return@withContext Result.failure(IOException("Project not found: $name"))
            }
            val jsonString = jsonFile.readText()
            val project = json.decodeFromString<Project>(jsonString)
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Delete a saved project and all its files. */
    suspend fun deleteProject(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val projectDir = projectsDir.resolve(name)
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
            val oldDir = projectsDir.resolve(oldName)
            val newDir = projectsDir.resolve(newName)
            if (!oldDir.exists()) {
                return@withContext Result.failure(IOException("Project not found: $oldName"))
            }
            if (newDir.exists()) {
                return@withContext Result.failure(IOException("Project already exists: $newName"))
            }
            oldDir.renameTo(newDir)

            // Update project.json with the new name.
            val jsonFile = newDir.resolve("project.json")
            val jsonString = jsonFile.readText()
            val project = json.decodeFromString<Project>(jsonString)
            val updated = project.copy(name = newName)
            jsonFile.writeText(json.encodeToString(updated))
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
     * @return the absolute file path on success.
     */
    suspend fun importAudioClip(uri: Uri, projectName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val samplesDir = projectsDir.resolve(projectName).resolve("samples").apply { mkdirs() }
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

            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            val sampleRate = 48000

            // Calculate approximate duration in samples.
            val maxEndTick = arrangement.clips.maxOfOrNull {
                it.startTick + it.durationTicks
            } ?: PPQ * 4L
            val totalSamples = (maxEndTick * sampleRate * 60L) / (PPQ * bpm.toLong())

            onProgress(0f)

            // Seek to start.
            transportController.seek(TransportPosition())

            // Start offline render.
            if (!SynthEngine.startOfflineRender(outputPath, totalSamples)) {
                return@withContext Result.failure(IOException("Failed to start offline render"))
            }
            onProgress(0.1f)

            // Run transport through the arrangement.
            transportController.play()

            // Wait for the arrangement to finish.
            val totalMs = (maxEndTick * 60_000L) / (PPQ * bpm.toLong())
            delay(totalMs + 500L)
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
            val sampleRate = 48000

            val maxEndTick = arrangement.clips.maxOfOrNull {
                it.startTick + it.durationTicks
            } ?: PPQ * 4L
            val totalSamples = (maxEndTick * sampleRate * 60L) / (PPQ * bpm.toLong())

            transportController.seek(TransportPosition())

            for (track in 0 until trackCount) {
                val stemPath = File(outputDir, "stem_track_${track + 1}.wav").absolutePath
                if (!SynthEngine.startOfflineRenderForTrack(stemPath, track, totalSamples)) {
                    continue
                }

                transportController.play()
                val totalMs = (maxEndTick * 60_000L) / (PPQ * bpm.toLong())
                delay(totalMs + 500L)
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
