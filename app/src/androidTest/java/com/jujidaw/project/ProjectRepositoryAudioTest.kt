package com.jujidaw.project

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jujidaw.JujiDawApp
import com.jujidaw.data.SettingsDataStore
import com.jujidaw.engine.TransportController
import com.jujidaw.model.Arrangement
import com.jujidaw.model.AudioClip
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectRepositoryAudioTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val json = Json { encodeDefaults = true; classDiscriminator = "type" }

    @Test
    fun autosaveAndRenameKeepAudioClipsProjectRelative() = runBlocking {
        val repository = ProjectRepository(context)
        val sourceName = "audio_source_${System.nanoTime()}"
        val autosaveName = "audio_autosave_${System.nanoTime()}"
        val renamedName = "audio_renamed_${System.nanoTime()}"
        val projectsDir = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("projects")
        val sourceSample = projectsDir.resolve(sourceName).resolve("recordings/take.wav")
        sourceSample.parentFile?.mkdirs()
        sourceSample.writeBytes(byteArrayOf(1, 2, 3))
        val app = JujiDawApp.instance
        val settings = SettingsDataStore(context)
        val previousProjectName = app.currentProjectName
        val previousLastProject = settings.getLastProjectName()

        try {
            val source = audioProject(sourceName, sourceSample.absolutePath)
            assertTrue(repository.saveProject(source).isSuccess)

            app.currentProjectName = sourceName
            val transport = TransportController()
            transport.arrangement = source.arrangement
            assertTrue(ProjectAutosave.autoSave(context, transport, autosaveName).isSuccess)
            val saved = repository.loadProject(autosaveName).getOrThrow()
            assertEquals("audio-$sourceName", saved.arrangement.clips.filterIsInstance<AudioClip>().single().id)
            assertEquals("recordings/take.wav", saved.arrangement.clips.filterIsInstance<AudioClip>().single().audioFilePath)
            assertTrue(projectsDir.resolve(autosaveName).resolve("recordings/take.wav").isFile)

            assertTrue(repository.renameProject(autosaveName, renamedName).isSuccess)
            val renamed = repository.loadProject(renamedName).getOrThrow()
            assertEquals("recordings/take.wav", renamed.arrangement.clips.filterIsInstance<AudioClip>().single().audioFilePath)
            assertTrue(projectsDir.resolve(renamedName).resolve("recordings/take.wav").isFile)
        } finally {
            app.currentProjectName = previousProjectName
            settings.setLastProjectName(previousLastProject)
            repository.deleteProject(sourceName)
            repository.deleteProject(autosaveName)
            repository.deleteProject(renamedName)
        }
    }

    @Test
    fun missingAudioFailsSaveInsteadOfWritingAnEmptyProject() = runBlocking {
        val repository = ProjectRepository(context)
        val name = "audio_missing_${System.nanoTime()}"
        try {
            val result = repository.saveProject(audioProject(name, "samples/missing.wav"))
            assertTrue(result.isFailure)
            assertFalse(
                (context.getExternalFilesDir(null) ?: context.filesDir)
                    .resolve("projects/$name/project.json")
                    .exists()
            )
        } finally {
            repository.deleteProject(name)
        }
    }

    @Test
    fun loadRejectsProjectExternalAudioReference() = runBlocking {
        val repository = ProjectRepository(context)
        val name = "audio_external_${System.nanoTime()}"
        val projectsDir = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("projects")
        val projectDir = projectsDir.resolve(name)
        val outside = projectsDir.resolve("outside-${System.nanoTime()}.wav")
        try {
            projectDir.mkdirs()
            projectDir.resolve("project.json").writeText(json.encodeToString(audioProject(name, outside.absolutePath)))
            assertTrue(repository.loadProject(name).isFailure)
        } finally {
            repository.deleteProject(name)
            outside.delete()
        }
    }

    @Test
    fun failedAutosaveDoesNotAdvanceLastProjectSetting() = runBlocking {
        val app = JujiDawApp.instance
        val settings = SettingsDataStore(context)
        val previousName = "keep_${System.nanoTime()}"
        val sourceName = "autosave_source_${System.nanoTime()}"
        val previousProjectName = app.currentProjectName
        val previousLastProject = settings.getLastProjectName()
        settings.setLastProjectName(previousName)
        app.currentProjectName = sourceName
        val transport = TransportController()
        transport.arrangement = Arrangement(
            clips = listOf(
                AudioClip(
                    id = "missing-audio",
                    trackIndex = 0,
                    startTick = 0L,
                    durationTicks = 480L,
                    audioFilePath = "samples/missing.wav",
                )
            )
        )

        try {
            val result = ProjectAutosave.autoSave(context, transport, ProjectAutosave.AUTOSAVE_NAME)
            assertTrue(result.isFailure)
            assertEquals(previousName, settings.getLastProjectName())
        } finally {
            app.currentProjectName = previousProjectName
            settings.setLastProjectName(previousLastProject)
        }
    }

    private fun audioProject(name: String, path: String) =
        Project(
            name = name,
            arrangement = Arrangement(
                clips = listOf(
                    AudioClip(
                        id = "audio-$name",
                        trackIndex = 0,
                        startTick = 0L,
                        durationTicks = 480L,
                        audioFilePath = path,
                    )
                )
            )
        )
}
