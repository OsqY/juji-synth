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
    fun corruptAudioRestoreLeavesCurrentArrangementUntouched() = runBlocking {
        val name = "audio_corrupt_${System.nanoTime()}"
        val projectsDir = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("projects")
        val corrupt = projectsDir.resolve(name).resolve("samples/corrupt.wav")
        corrupt.parentFile?.mkdirs()
        corrupt.writeBytes(byteArrayOf(1, 2, 3))
        val transport = TransportController()
        val previous = Arrangement(loopEnabled = true, loopEndTick = 960L)
        transport.arrangement = previous
        try {
            val result = ProjectAutosave.applyProjectToEngine(
                audioProject(name, "samples/corrupt.wav"),
                transport,
                context,
            )
            assertTrue(result.isFailure)
            assertEquals(previous, transport.arrangement)
        } finally {
            transport.release()
            corrupt.parentFile?.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun invalidProjectScalarLeavesCurrentArrangementUntouched() = runBlocking {
        val transport = TransportController()
        val previous = Arrangement(loopEnabled = true, loopEndTick = 960L)
        transport.arrangement = previous
        try {
            val result = ProjectAutosave.applyProjectToEngine(
                Project(name = "invalid_${System.nanoTime()}", swing = 2f),
                transport,
                context,
            )
            assertTrue(result.isFailure)
            assertEquals(previous, transport.arrangement)
        } finally {
            transport.release()
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

    @Test
    fun failedSaveDoesNotModifyExistingProjectAssets() = runBlocking {
        val repository = ProjectRepository(context)
        val name = "save_rollback_${System.nanoTime()}"
        val projectsDir = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("projects")
        val source = projectsDir.resolve("source-${System.nanoTime()}.wav")
        source.writeBytes(byteArrayOf(1, 2, 3))
        try {
            assertTrue(
                repository.saveProject(
                    Project(name = name, pads = listOf(PadSettings(samplePath = source.absolutePath))),
                ).isSuccess,
            )
            val projectDir = projectsDir.resolve(name)
            val asset = projectDir.resolve("samples/pad_0_${source.name}")
            val originalJson = projectDir.resolve("project.json").readText()

            assertTrue(
                repository.saveProject(
                    Project(
                        name = name,
                        pads = listOf(
                            PadSettings(samplePath = source.absolutePath),
                            PadSettings(samplePath = projectsDir.resolve("missing.wav").absolutePath),
                        ),
                    ),
                ).isFailure,
            )
            assertEquals(originalJson, projectDir.resolve("project.json").readText())
            assertEquals(listOf<Byte>(1, 2, 3), asset.readBytes().toList())
        } finally {
            source.delete()
            repository.deleteProject(name)
        }
    }

    @Test
    fun interruptedSaveBackupIsRecoveredAndHiddenFromProjectList() = runBlocking {
        val repository = ProjectRepository(context)
        val name = "save_recovery_${System.nanoTime()}"
        val projectsDir = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("projects")
        val projectDir = projectsDir.resolve(name)
        val backupDir = projectsDir.resolve(".$name.123.bak")
        try {
            assertTrue(repository.saveProject(Project(name = name)).isSuccess)
            assertTrue(projectDir.renameTo(backupDir))

            assertTrue(repository.listProjects().any { it.name == name })
            assertTrue(projectDir.resolve("project.json").isFile)
            assertFalse(repository.listProjects().any { it.name == backupDir.name })
        } finally {
            repository.deleteProject(name)
            backupDir.deleteRecursively()
        }
    }

    @Test
    fun interruptedRenameCompletesWhenTargetWasInstalled() = runBlocking {
        val repository = ProjectRepository(context)
        val oldName = "rename_old_${System.nanoTime()}"
        val newName = "rename_new_${System.nanoTime()}"
        val projectsDir = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("projects")
        try {
            assertTrue(repository.saveProject(Project(name = oldName)).isSuccess)
            assertTrue(repository.saveProject(Project(name = newName)).isSuccess)
            projectsDir.resolve(".rename.123.txn").writeText("$oldName\n$newName")

            val names = repository.listProjects().map { it.name }
            assertFalse(oldName in names)
            assertTrue(newName in names)
        } finally {
            repository.deleteProject(oldName)
            repository.deleteProject(newName)
        }
    }

    @Test
    fun padAssetsStayProjectRelativeAcrossSaveLoadAndRename() = runBlocking {
        val repository = ProjectRepository(context)
        val name = "pad_assets_${System.nanoTime()}"
        val renamed = "pad_assets_renamed_${System.nanoTime()}"
        val source = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve("imported-pad-${System.nanoTime()}.wav")
        source.writeBytes(byteArrayOf(4, 5, 6))
        val projectsDir = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("projects")
        try {
            assertTrue(
                repository.saveProject(
                    Project(
                        name = name,
                        pads = listOf(PadSettings(samplePath = source.absolutePath, name = "Kick")),
                    ),
                ).isSuccess,
            )
            val savedJson = projectsDir.resolve(name).resolve("project.json").readText()
            assertFalse(savedJson.contains(source.absolutePath))
            assertTrue(projectsDir.resolve(name).resolve("samples/pad_0_${source.name}").isFile)
            assertEquals("samples/pad_0_${source.name}", repository.loadProject(name).getOrThrow().pads[0].samplePath)

            assertTrue(repository.renameProject(name, renamed).isSuccess)
            val loaded = repository.loadProject(renamed).getOrThrow()
            assertEquals("samples/pad_0_${source.name}", loaded.pads[0].samplePath)
            assertTrue(projectsDir.resolve(renamed).resolve(loaded.pads[0].samplePath).isFile)
        } finally {
            source.delete()
            repository.deleteProject(name)
            repository.deleteProject(renamed)
        }
    }

    @Test
    fun loadMigratesManagedAbsolutePadSampleToProjectStorage() = runBlocking {
        val repository = ProjectRepository(context)
        val name = "pad_legacy_${System.nanoTime()}"
        val source = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve("legacy-pad-${System.nanoTime()}.wav")
        val projectDir = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve("projects").resolve(name)
        source.writeBytes(byteArrayOf(7, 8, 9))
        try {
            projectDir.mkdirs()
            projectDir.resolve("project.json").writeText(
                json.encodeToString(
                    Project(
                        name = name,
                        pads = listOf(PadSettings(samplePath = source.absolutePath)),
                    ),
                ),
            )
            val loaded = repository.loadProject(name).getOrThrow()
            assertEquals("samples/pad_0_${source.name}", loaded.pads[0].samplePath)
            assertTrue(projectDir.resolve(loaded.pads[0].samplePath).isFile)
            source.delete()
            val reloaded = repository.loadProject(name).getOrThrow()
            assertEquals(loaded.pads[0].samplePath, reloaded.pads[0].samplePath)
            assertFalse(projectDir.resolve("project.json").readText().contains(source.absolutePath))
        } finally {
            source.delete()
            repository.deleteProject(name)
        }
    }

    @Test
    fun failedLegacyPadMigrationDoesNotCopyEarlierPads() = runBlocking {
        val repository = ProjectRepository(context)
        val name = "pad_migration_failure_${System.nanoTime()}"
        val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
        val source = filesDir.resolve("legacy-valid-${System.nanoTime()}.wav")
        val missing = filesDir.resolve("legacy-missing-${System.nanoTime()}.wav")
        val projectDir = filesDir.resolve("projects/$name")
        source.writeBytes(byteArrayOf(7, 8, 9))
        try {
            projectDir.mkdirs()
            projectDir.resolve("project.json").writeText(
                json.encodeToString(
                    Project(
                        name = name,
                        pads = listOf(
                            PadSettings(samplePath = source.absolutePath),
                            PadSettings(samplePath = missing.absolutePath),
                        ),
                    ),
                ),
            )

            assertTrue(repository.loadProject(name).isFailure)
            assertFalse(projectDir.resolve("samples/pad_0_${source.name}").exists())
        } finally {
            source.delete()
            repository.deleteProject(name)
        }
    }

    @Test
    fun movingPadAssetDoesNotAccumulateIndexPrefixes() = runBlocking {
        val repository = ProjectRepository(context)
        val name = "pad_move_${System.nanoTime()}"
        val source = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve("pad-move-${System.nanoTime()}.wav")
        source.writeBytes(byteArrayOf(4, 5, 6))
        try {
            assertTrue(
                repository.saveProject(
                    Project(name = name, pads = listOf(PadSettings(samplePath = source.absolutePath))),
                ).isSuccess,
            )
            val firstPath = repository.loadProject(name).getOrThrow().pads[0].samplePath
            assertTrue(
                repository.saveProject(
                    Project(name = name, pads = listOf(PadSettings(), PadSettings(samplePath = firstPath))),
                    sourceProjectName = name,
                ).isSuccess,
            )

            assertEquals("samples/pad_1_${source.name}", repository.loadProject(name).getOrThrow().pads[1].samplePath)
        } finally {
            source.delete()
            repository.deleteProject(name)
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
