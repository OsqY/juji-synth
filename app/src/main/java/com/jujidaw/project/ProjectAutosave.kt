package com.jujidaw.project

import android.content.Context
import com.jujidaw.audio.SynthEngine
import com.jujidaw.data.SettingsDataStore
import com.jujidaw.engine.TransportController
import com.jujidaw.model.AudioClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Auto-save / auto-load helpers so the user never loses their work on app
 * close. A single implicit "autosave" project is persisted under the standard
 * projects dir, and its name is remembered in [SettingsDataStore].
 *
 * The mixer state is captured by reading the C++ engine getters so a save
 * actually reflects what the user hears (the legacy [ProjectViewModel]
 * [captureMixerState] returned an empty [MixerState]).
 */
object ProjectAutosave {

    const val AUTOSAVE_NAME = "autosave"

    /** Build a [MixerState] snapshot from the live engine (16 channels + buses + master). */
    fun captureMixerState(): MixerState {
        if (!SynthEngine.isLoaded) return MixerState()
        val tracks = (0 until 16).map { i ->
            TrackState(
                faderDb = SynthEngine.getChannelFaderDb(i),
                pan = SynthEngine.getChannelPan(i),
                mute = SynthEngine.isChannelMute(i),
                solo = SynthEngine.isChannelSolo(i),
                arm = SynthEngine.isChannelArm(i),
                sendALevel = SynthEngine.getSendLevel(i, 0),
                sendBLevel = SynthEngine.getSendLevel(i, 1)
            )
        }
        return MixerState(
            tracks = tracks,
            busA = BusState(faderDb = SynthEngine.getBusFaderDb(0)),
            busB = BusState(faderDb = SynthEngine.getBusFaderDb(1)),
            masterFaderDb = SynthEngine.getMasterFaderDb()
        )
    }

    /** Build a full [Project] from the live transport + engine state. */
    fun buildProjectFromEngine(name: String, tc: TransportController): Project {
        return Project(
            name = name,
            bpm = tc.transportState.tempoBpm,
            timeSignature = tc.transportState.timeSignature,
            patterns = tc.patterns,
            arrangement = tc.arrangement,
            mixerState = captureMixerState()
        )
    }

    /** Apply a [Project] to the live engine (BPM, patterns, arrangement, mixer, audio clips). */
    fun applyProjectToEngine(project: Project, tc: TransportController, context: Context) {
        tc.setTempo(project.bpm)
        tc.loadPatterns(project.patterns)
        tc.loadArrangement(project.arrangement)
        applyMixerState(project.mixerState)
        val projectsBase = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve("projects").resolve(project.name)
        for (clip in project.arrangement.clips) {
            if (clip is AudioClip) {
                val full = if (File(clip.audioFilePath).isAbsolute) clip.audioFilePath
                           else projectsBase.resolve(clip.audioFilePath).absolutePath
                if (File(full).exists()) SynthEngine.loadAudioClip(clip.id, full)
            }
        }
    }

    fun applyMixerState(state: MixerState) {
        if (!SynthEngine.isLoaded) return
        for (i in 0 until 16) {
            val track = state.tracks.getOrNull(i) ?: continue
            SynthEngine.setChannelFader(i, track.faderDb)
            SynthEngine.setChannelPan(i, track.pan)
            SynthEngine.setChannelMute(i, track.mute)
            SynthEngine.setChannelSolo(i, track.solo)
            SynthEngine.setChannelArm(i, track.arm)
            SynthEngine.setSendLevel(i, 0, track.sendALevel)
            SynthEngine.setSendLevel(i, 1, track.sendBLevel)
        }
        SynthEngine.setMasterFader(state.masterFaderDb)
        SynthEngine.setBusFader(0, state.busA.faderDb)
        SynthEngine.setBusFader(1, state.busB.faderDb)
    }

    /** Save the current engine state into the autosave project. */
    suspend fun autoSave(context: Context, tc: TransportController, name: String = AUTOSAVE_NAME) {
        withContext(Dispatchers.IO) {
            val repo = ProjectRepository(context.applicationContext)
            val project = buildProjectFromEngine(name, tc)
            runCatching { repo.saveProject(project) }
            SettingsDataStore(context.applicationContext).setLastProjectName(name)
        }
    }

    /**
     * Load the last project (if any) and apply it to the engine.
     * Returns the loaded project name, or null if there was nothing to load.
     */
    suspend fun autoLoad(context: Context, tc: TransportController): String? {
        return withContext(Dispatchers.IO) {
            val settings = SettingsDataStore(context.applicationContext)
            val lastName = settings.getLastProjectName()
            val repo = ProjectRepository(context.applicationContext)
            val nameToLoad = lastName ?: run {
                val list = runCatching { repo.listProjects() }.getOrDefault(emptyList())
                list.firstOrNull()?.name
            } ?: return@withContext null
            val result = runCatching { repo.loadProject(nameToLoad) }.getOrNull()
                ?: return@withContext null
            result.onSuccess { project ->
                applyProjectToEngine(project, tc, context.applicationContext)
                settings.setLastProjectName(project.name)
            }
            nameToLoad
        }
    }
}