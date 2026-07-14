package com.jujidaw.project

import android.content.Context
import com.jujidaw.audio.SynthEngine
import com.jujidaw.data.SettingsDataStore
import com.jujidaw.engine.TransportController
import com.jujidaw.model.AudioClip
import com.jujidaw.ui.pads.PadParamIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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

    /**
     * Debounce window for change-triggered auto-save. Rapid edits (toggling many
     * sequencer steps, dragging clips) coalesce into a single disk write.
     */
    private const val AUTOSAVE_DEBOUNCE_MS = 1500L

    /** Dedicated scope for debounced saves; outlives individual ViewModels. */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Outstanding debounce job; replaced on every new schedule. */
    private var debounceJob: Job? = null

    /**
     * True while [autoLoad] is applying a saved project to the engine. Prevents
     * a change-triggered save from clobbering the autosave file with pre-load
     * (often empty) state during startup.
     */
    private val loadInProgress = AtomicBoolean(false)

    /** Build a [MixerState] snapshot from the live engine (16 channels + buses + master). */
    fun captureMixerState(): MixerState {
        if (!SynthEngine.isLoaded) return MixerState()
        val tracks =
            (0 until 16).map { i ->
                TrackState(
                    faderDb = SynthEngine.getChannelFaderDb(i),
                    pan = SynthEngine.getChannelPan(i),
                    mute = SynthEngine.isChannelMute(i),
                    solo = SynthEngine.isChannelSolo(i),
                    arm = SynthEngine.isChannelArm(i),
                    sendALevel = SynthEngine.getSendLevel(i, 0),
                    sendBLevel = SynthEngine.getSendLevel(i, 1),
                )
            }
        return MixerState(
            tracks = tracks,
            busA = BusState(faderDb = SynthEngine.getBusFaderDb(0)),
            busB = BusState(faderDb = SynthEngine.getBusFaderDb(1)),
            masterFaderDb = SynthEngine.getMasterFaderDb(),
        )
    }

    /** Build a full [Project] from the live transport + engine state. */
    fun buildProjectFromEngine(
        name: String,
        tc: TransportController,
    ): Project =
        Project(
            name = name,
            bpm = tc.transportState.tempoBpm,
            timeSignature = tc.transportState.timeSignature,
            patterns = tc.patterns,
            arrangement = tc.arrangement,
            mixerState = captureMixerState(),
            // Pad sample paths + cached params live in PadsViewModel; the
            // store is the bridge that keeps them available here.
            pads = normalizePads(PadSessionStore.snapshot()),
        )

    /** Apply a [Project] to the live engine (BPM, patterns, arrangement, mixer, audio clips). */
    fun applyProjectToEngine(
        project: Project,
        tc: TransportController,
        context: Context,
    ) {
        tc.setTempo(project.bpm)
        tc.loadPatterns(project.patterns)
        tc.loadArrangement(project.arrangement)
        applyMixerState(project.mixerState)
        val projectsBase =
            (context.getExternalFilesDir(null) ?: context.filesDir)
                .resolve("projects")
                .resolve(project.name)
        for (clip in project.arrangement.clips) {
            if (clip is AudioClip) {
                val full =
                    if (File(clip.audioFilePath).isAbsolute) {
                        clip.audioFilePath
                    } else {
                        projectsBase.resolve(clip.audioFilePath).absolutePath
                    }
                if (File(full).exists()) SynthEngine.loadAudioClip(clip.id, full)
            }
        }
        // Re-hydrate pad samplers + cached params so pads survive restart.
        applyPadSettings(project.pads)
    }

    /**
     * Reload pad sample files + cached params into the engine and publish
     * the snapshot to [PadSessionStore] so [PadsViewModel] can sync its UI
     * state (including when it is created after this load).
     */
    fun applyPadSettings(pads: List<PadSettings>) {
        val normalized = normalizePads(pads)
        for ((globalIndex, pad) in normalized.withIndex()) {
            val path = pad.samplePath
            if (path.isNotEmpty() && File(path).exists()) {
                SynthEngine.loadSampleToPad(path, globalIndex)
            }
            val params = pad.params
            applyPadParam(globalIndex, PadParamIds.PITCH, params.pitch)
            applyPadParam(globalIndex, PadParamIds.PAN, params.pan)
            applyPadParam(globalIndex, PadParamIds.VOLUME, params.volume)
            applyPadParam(globalIndex, PadParamIds.ATTACK, params.attack)
            applyPadParam(globalIndex, PadParamIds.RELEASE, params.release)
            applyPadParam(globalIndex, PadParamIds.FILTER_CUTOFF, params.filterCutoff)
            applyPadParam(globalIndex, PadParamIds.FILTER_RESONANCE, params.filterResonance)
            applyPadParam(globalIndex, PadParamIds.REVERSE, if (params.reverse) 1f else 0f)
            applyPadParam(globalIndex, PadParamIds.LOOP, if (params.loop) 1f else 0f)
            applyPadParam(globalIndex, PadParamIds.ONE_SHOT, if (params.oneShot) 1f else 0f)
            applyPadParam(globalIndex, PadParamIds.USE_FILTER, if (params.useFilter) 1f else 0f)
            applyPadParam(globalIndex, PadParamIds.SYNTH_MODE, if (params.synthMode) 1f else 0f)
            applyPadParam(globalIndex, PadParamIds.SYNTH_ROOT_NOTE, params.synthRootNote.toFloat())
        }
        PadSessionStore.set(normalized)
    }

    private fun applyPadParam(
        globalIndex: Int,
        paramId: Int,
        value: Float,
    ) {
        if (!SynthEngine.isLoaded) return
        SynthEngine.setPadParam(globalIndex, paramId, value)
        // Mirror PadsViewModel.setPadParam's synth-mode side effect.
        if (paramId == PadParamIds.SYNTH_MODE) {
            val padIdx = globalIndex % 16
            if (value > 0.5f) {
                SynthEngine.setPadSynthEnabled(padIdx, true)
                SynthEngine.applyPadSynthState(padIdx, SynthEngine.defaultSynthParams())
            } else {
                SynthEngine.setPadSynthEnabled(padIdx, false)
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

    /**
     * Schedule a debounced auto-save of the current engine state. Each call
     * resets the timer so only the most recent edit batch is persisted; rapid
     * edits produce a single write.
     *
     * Skipped while a load is in progress, and the load cancels any pending job
     * on entry, so the just-loaded state is never overwritten by stale
     * pre-load state.
     */
    fun scheduleAutoSave(
        context: Context,
        tc: TransportController,
        name: String = AUTOSAVE_NAME,
        delayMs: Long = AUTOSAVE_DEBOUNCE_MS,
    ) {
        val app = context.applicationContext
        debounceJob?.cancel()
        debounceJob =
            saveScope.launch {
                delay(delayMs)
                // A load may have started after scheduling; never write over one.
                if (loadInProgress.get()) return@launch
                runCatching { autoSave(app, tc, name) }
            }
    }

    /** Cancel any pending debounced auto-save (e.g. before an immediate save). */
    fun cancelPendingAutoSave() {
        debounceJob?.cancel()
        debounceJob = null
    }

    /** Save the current engine state into the autosave project. */
    suspend fun autoSave(
        context: Context,
        tc: TransportController,
        name: String = AUTOSAVE_NAME,
    ) {
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
    suspend fun autoLoad(
        context: Context,
        tc: TransportController,
    ): String? {
        return withContext(Dispatchers.IO) {
            // Cancel any debounce scheduled during composition (e.g. from a
            // ViewModel init) and block change-triggered saves until the loaded
            // state is applied, so the autosave file is not overwritten with
            // pre-load state.
            cancelPendingAutoSave()
            loadInProgress.set(true)
            try {
                val settings = SettingsDataStore(context.applicationContext)
                val lastName = settings.getLastProjectName()
                val repo = ProjectRepository(context.applicationContext)
                val nameToLoad =
                    lastName ?: run {
                        val list = runCatching { repo.listProjects() }.getOrDefault(emptyList())
                        list.firstOrNull()?.name
                    } ?: return@withContext null
                val result =
                    runCatching { repo.loadProject(nameToLoad) }.getOrNull()
                        ?: return@withContext null
                result.onSuccess { project ->
                    applyProjectToEngine(project, tc, context.applicationContext)
                    settings.setLastProjectName(project.name)
                }
                nameToLoad
            } finally {
                loadInProgress.set(false)
            }
        }
    }

    private const val NUM_PADS = 32

    private fun normalizePads(pads: List<PadSettings>): List<PadSettings> = (0 until NUM_PADS).map { pads.getOrNull(it) ?: PadSettings() }
}
