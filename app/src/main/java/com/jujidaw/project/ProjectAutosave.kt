package com.jujidaw.project

import android.content.Context
import com.jujidaw.JujiDawApp
import com.jujidaw.audio.SynthEngine
import com.jujidaw.data.SettingsDataStore
import com.jujidaw.engine.TransportController
import com.jujidaw.model.AudioClip
import com.jujidaw.model.SynthState
import com.jujidaw.model.defaultTrackSynthState
import com.jujidaw.model.toParamsArray
import com.jujidaw.ui.pads.PadParamIds
import com.jujidaw.ui.synth.SynthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Serialize disk saves with project loads so a stale snapshot cannot win. */
    private val projectIoMutex = Mutex()

    /**
     * True while [autoLoad] is applying a saved project to the engine. Prevents
     * a change-triggered save from clobbering the autosave file with pre-load
     * (often empty) state during startup.
     */
    private val loadInProgress = AtomicBoolean(false)

    /** Build a [MixerState] snapshot from the live engine (16 channels + buses + master). */
    fun captureMixerState(): MixerState {
        val stored = MixerSessionStore.snapshot()
        if (!SynthEngine.isLoaded) return stored
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
                    insertFx = stored.tracks.getOrNull(i)?.insertFx.orEmpty(),
                )
            }
        val snapshot = MixerState(
            tracks = tracks,
            busA = stored.busA.copy(faderDb = SynthEngine.getBusFaderDb(0)),
            busB = stored.busB.copy(faderDb = SynthEngine.getBusFaderDb(1)),
            masterFaderDb = SynthEngine.getMasterFaderDb(),
            masterInsertFx = stored.masterInsertFx,
        )
        MixerSessionStore.set(snapshot)
        return snapshot
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
            swing = tc.transportState.swing,
            patterns = tc.patterns,
            arrangement = tc.arrangement,
            mixerState = captureMixerState(),
            midiMappings = JujiDawApp.instance.midiRouter.mappings.value,
            automation = tc.automationClips.toList(),
            trackSynthStates = TrackSynthSessionStore.snapshot(),
            // Pad sample paths + cached params live in PadsViewModel; the
            // store is the bridge that keeps them available here.
            pads = normalizePads(PadSessionStore.snapshot()),
            padSynthStates = PadSynthSessionStore.snapshot(),
        )

    /** Apply a [Project] to the live engine (BPM, patterns, arrangement, mixer, audio clips). */
    fun applyProjectToEngine(
        project: Project,
        tc: TransportController,
        context: Context,
    ) {
        val projectsBase =
            ProjectPathPolicy.projectDirectory(
                (context.getExternalFilesDir(null) ?: context.filesDir).resolve("projects"),
                project.name,
            ) ?: return
        (context.applicationContext as? JujiDawApp)?.currentProjectName = project.name
        tc.setTempo(project.bpm)
        tc.setTimeSignature(project.timeSignature)
        tc.setSwing(project.swing)
        tc.loadPatterns(project.patterns)
        tc.loadArrangement(project.arrangement)
        tc.loadAutomation(project.automation)
        val loopStartSample = tickToSample(project.arrangement.loopStartTick, project.bpm)
        val loopEndSample = tickToSample(project.arrangement.loopEndTick, project.bpm)
        SynthEngine.setLoop(project.arrangement.loopEnabled, loopStartSample, loopEndSample)
        applyMixerState(project.mixerState)
        JujiDawApp.instance.midiRouter.replaceMappings(project.midiMappings)
        applyTrackSynthStates(project.trackSynthStates)
        for (clip in project.arrangement.clips) {
            if (clip is AudioClip) {
                val full = ProjectPathPolicy.audioFile(projectsBase, clip.audioFilePath)
                if (full?.isFile == true) SynthEngine.loadAudioClip(clip.id, full.absolutePath)
            }
        }
        // Make the incoming snapshots authoritative before mode restoration.
        // This prevents a synth state from the previously open project being
        // used as the fallback for an older project with no padSynthStates.
        PadSynthSessionStore.replace(project.padSynthStates)
        // Re-hydrate pad samplers + cached params so pads survive restart.
        applyPadSettings(project.pads, projectsBase)
        applyPadSynthStates(project.padSynthStates)
    }

    /**
     * Reload pad sample files + cached params into the engine and publish
     * the snapshot to [PadSessionStore] so [PadsViewModel] can sync its UI
     * state (including when it is created after this load).
     */
    fun applyPadSettings(
        pads: List<PadSettings>,
        projectDirectory: File? = null,
    ) {
        val normalized = normalizePads(pads).map { pad ->
            if (pad.samplePath.isBlank() || projectDirectory == null) {
                pad
            } else {
                pad.copy(
                    samplePath = ProjectPathPolicy.audioFile(projectDirectory, pad.samplePath)?.absolutePath.orEmpty(),
                )
            }
        }
        for ((globalIndex, pad) in normalized.withIndex()) {
            if (SynthEngine.isLoaded) {
                SynthEngine.releasePad(globalIndex)
                SynthEngine.clearPad(globalIndex)
            }
            val path = pad.samplePath
            if (path.isNotEmpty() && File(path).isFile) {
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
            if (value > 0.5f) {
                val state = PadSynthSessionStore.snapshot()[globalIndex] ?: defaultTrackSynthState()
                PadSynthSessionStore.setPadState(globalIndex, state)
                SynthEngine.setPadSynthEnabled(globalIndex, true)
                SynthEngine.applyPadSynthState(globalIndex, state.toParamsArray())
            } else {
                SynthEngine.setPadSynthEnabled(globalIndex, false)
            }
        }
    }

    fun applyMixerState(state: MixerState) {
        MixerSessionStore.set(state)
        if (!SynthEngine.isLoaded) return
        for (i in 0 until 16) {
            repeat(MAX_INSERTS) { slot -> SynthEngine.removeInsertEffect(i, slot) }
            val track = state.tracks.getOrNull(i) ?: continue
            SynthEngine.setChannelFader(i, track.faderDb)
            SynthEngine.setChannelPan(i, track.pan)
            SynthEngine.setChannelMute(i, track.mute)
            SynthEngine.setChannelSolo(i, track.solo)
            SynthEngine.setChannelArm(i, track.arm)
            SynthEngine.setSendLevel(i, 0, track.sendALevel)
            SynthEngine.setSendLevel(i, 1, track.sendBLevel)
            applyInsertChain(i, track.insertFx)
        }
        repeat(MAX_INSERTS) { slot ->
            SynthEngine.removeBusInsertEffect(0, slot)
            SynthEngine.removeBusInsertEffect(1, slot)
            SynthEngine.removeInsertEffect(MASTER_INSERT_TRACK, slot)
        }
        applyBusInsertChain(0, state.busA.insertFx)
        applyBusInsertChain(1, state.busB.insertFx)
        applyMasterInsertChain(state.masterInsertFx)
        SynthEngine.setMasterFader(state.masterFaderDb)
        SynthEngine.setBusFader(0, state.busA.faderDb)
        SynthEngine.setBusFader(1, state.busB.faderDb)
    }

    private fun applyInsertChain(trackIndex: Int, inserts: List<InsertFxSlot>) {
        inserts.forEach { slot ->
            val type = SynthEngine.EffectType.values().firstOrNull { it.value == slot.effectType }
                ?: return@forEach
            if (type == SynthEngine.EffectType.None) return@forEach
            SynthEngine.addInsertEffect(trackIndex, slot.slotIndex, type)
            slot.params.forEach { (paramId, value) ->
                SynthEngine.setInsertParam(trackIndex, slot.slotIndex, paramId, value)
            }
            SynthEngine.setInsertBypass(trackIndex, slot.slotIndex, slot.bypass)
        }
    }

    private fun applyBusInsertChain(busIndex: Int, inserts: List<InsertFxSlot>) {
        inserts.forEach { slot ->
            val type = SynthEngine.EffectType.values().firstOrNull { it.value == slot.effectType }
                ?: return@forEach
            if (type == SynthEngine.EffectType.None) return@forEach
            SynthEngine.addBusInsertEffect(busIndex, slot.slotIndex, type)
            slot.params.forEach { (paramId, value) ->
                SynthEngine.setBusInsertParam(busIndex, slot.slotIndex, paramId, value)
            }
            SynthEngine.setBusInsertBypass(busIndex, slot.slotIndex, slot.bypass)
        }
    }

    private fun applyMasterInsertChain(inserts: List<InsertFxSlot>) {
        inserts.forEach { slot ->
            val type = SynthEngine.EffectType.values().firstOrNull { it.value == slot.effectType }
                ?: return@forEach
            if (type == SynthEngine.EffectType.None) return@forEach
            SynthEngine.addInsertEffect(MASTER_INSERT_TRACK, slot.slotIndex, type)
            slot.params.forEach { (paramId, value) ->
                SynthEngine.setInsertParam(MASTER_INSERT_TRACK, slot.slotIndex, paramId, value)
            }
            SynthEngine.setInsertBypass(MASTER_INSERT_TRACK, slot.slotIndex, slot.bypass)
        }
    }

    /** Restore the active track synth and native modulation snapshots. */
    fun applyTrackSynthStates(states: Map<Int, SynthState>) {
        val normalized = states.filterKeys { it in 0 until NUM_TRACKS }
        val effective = normalized + (0 to (normalized[0] ?: defaultTrackSynthState()))
        TrackSynthSessionStore.replace(effective)
        if (SynthEngine.isLoaded) {
            SynthViewModel.applySynthStateToEngine(effective.getValue(0))
        }
    }

    /** Restore the complete synth snapshots only after pad modes are restored. */
    fun applyPadSynthStates(states: Map<Int, SynthState>) {
        val normalized = states.filterKeys { it in 0 until NUM_PADS }
        PadSynthSessionStore.replace(normalized)
        if (!SynthEngine.isLoaded) return
        normalized.forEach { (globalIndex, state) ->
            SynthEngine.setPadSynthEnabled(globalIndex, true)
            SynthEngine.applyPadSynthState(globalIndex, state.toParamsArray())
        }
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
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            projectIoMutex.withLock {
                val repo = ProjectRepository(context.applicationContext)
                val project = buildProjectFromEngine(name, tc)
                val result = repo.saveProject(project, JujiDawApp.instance.currentProjectName)
                if (result.isSuccess) {
                    SettingsDataStore(context.applicationContext).setLastProjectName(name)
                }
                result
            }
        }
    }

    /**
     * Load the last project (if any) and apply it to the engine.
     * Returns the loaded project name, or null if there was nothing to load.
     * Failures are returned to the caller so startup can offer recovery.
     */
    suspend fun autoLoad(
        context: Context,
        tc: TransportController,
    ): Result<String?> {
        return withContext(Dispatchers.IO) {
            projectIoMutex.withLock {
                // Cancel any debounce scheduled during composition before
                // applying the loaded state.
                cancelPendingAutoSave()
                loadInProgress.set(true)
                try {
                    val settings = SettingsDataStore(context.applicationContext)
                    val lastName = settings.getLastProjectName()
                    val repo = ProjectRepository(context.applicationContext)
                    val nameToLoad =
                        lastName ?: run {
                            val list = repo.listProjects()
                            list.firstOrNull()?.name
                        } ?: return@withContext Result.success(null)
                    repo.loadProject(nameToLoad).map { project ->
                        applyProjectToEngine(project, tc, context.applicationContext)
                        settings.setLastProjectName(project.name)
                        nameToLoad
                    }
                } finally {
                    loadInProgress.set(false)
                }
            }
        }
    }

    private const val NUM_PADS = 32
    private const val NUM_TRACKS = 16
    private const val MAX_INSERTS = 4
    private const val MASTER_INSERT_TRACK = 16

    private fun tickToSample(tick: Long, bpm: Float): Long =
        (tick * (60.0 / bpm) * 48000 / com.jujidaw.model.PPQ).toLong()

    private fun normalizePads(pads: List<PadSettings>): List<PadSettings> = (0 until NUM_PADS).map { pads.getOrNull(it) ?: PadSettings() }
}
