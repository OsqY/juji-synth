package com.jujidaw.ui.project

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jujidaw.JujiDawApp
import com.jujidaw.audio.SynthEngine
import com.jujidaw.engine.TransportController
import com.jujidaw.model.AudioClip
import com.jujidaw.model.PPQ
import com.jujidaw.model.ParamIds
import com.jujidaw.project.MixerState
import com.jujidaw.project.Project
import com.jujidaw.project.ProjectAutosave
import com.jujidaw.project.ProjectInfo
import com.jujidaw.project.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * UI state exposed by [ProjectViewModel].
 *
 * @property projects list of saved project summaries.
 * @property currentProjectName the name of the currently loaded/active project, or null.
 * @property isLoading true while a save/load operation is in progress.
 * @property isPlaying true when the transport is playing.
 * @property isRecording true when the transport is recording (input recording).
 * @property exportProgress 0..1 progress of an ongoing export operation.
 * @property isExporting true while a WAV export is in progress.
 * @property showNewDialog controls the "new project" dialog visibility.
 * @property showDeleteConfirm controls the delete confirmation dialog.
 * @property showRenameDialog controls the rename dialog.
 * @property projectToDelete the project pending deletion confirmation.
 * @property projectToRename the project pending rename.
 * @property toastMessage one-shot message to surface via [consumeToast].
 * @property recordingTrackIndex the mixer track (0..15) to record onto.
 * @property isTimelineRecording true when timeline recording is active.
 */
data class ProjectUiState(
    val projects: List<ProjectInfo> = emptyList(),
    val currentProjectName: String? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val exportProgress: Float = 0f,
    val isExporting: Boolean = false,
    val showNewDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showRenameDialog: Boolean = false,
    val projectToDelete: ProjectInfo? = null,
    val projectToRename: ProjectInfo? = null,
    val toastMessage: String? = null,
    val recordingTrackIndex: Int = 0,
    val isTimelineRecording: Boolean = false
)

/**
 * ViewModel for the Project screen.
 *
 * Bridges file I/O ([ProjectRepository]) and engine state
 * ([TransportController], [SynthEngine]) to the UI.
 *
 * ### Public API
 * - [uiState] — observable screen state.
 * - [init] — must be called once with a [Context] to initialise the repository.
 * - [refreshProjectList], [createNewProject], [saveProject], [loadProject],
 *   [deleteProject], [renameProject] — project CRUD.
 * - [exportMix], [exportStems] — WAV export (see TODO in repository).
 * - [importAudioClip] — file-picker integration for audio import.
 * - [startTimelineRecording], [stopTimelineRecording], [setRecordingTrack]
 *   — timeline recording workflow.
 * - [playTransport], [stopTransport] — basic transport control.
 * - [consumeToast] — clear the one-shot toast message.
 *
 * ### Assumptions
 * - [TransportController] is obtained from [JujiDawApp.instance].
 * - [ProjectRepository] is lazily initialised with the application context.
 * - WAV export uses a real-time fallback; proper offline rendering
 *   requires future C++ engine additions (see [ProjectRepository.exportMix]).
 */
class ProjectViewModel(
    private val transportController: TransportController = JujiDawApp.instance.transportController
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    private var repository: ProjectRepository? = null

    /**
     * Initialise the repository with the application context.
     * Must be called at least once (e.g. from a LaunchedEffect in the screen).
     */
    fun init(context: Context) {
        if (repository == null) {
            repository = ProjectRepository(context.applicationContext)
            refreshProjectList()
        }
    }

    // ── Project list ────────────────────────────────────────────────

    /** Re-scan the projects directory. */
    fun refreshProjectList() {
        repository?.let { repo ->
            _uiState.value = _uiState.value.copy(projects = repo.listProjects())
        }
    }

    // ── New ─────────────────────────────────────────────────────────

    fun showNewDialog() {
        _uiState.value = _uiState.value.copy(showNewDialog = true)
    }

    fun dismissNewDialog() {
        _uiState.value = _uiState.value.copy(showNewDialog = false)
    }

    /**
     * Create and save a new project with the given [name].
     * Takes a snapshot of the current transport/arrangement/mixer state.
     */
    fun createNewProject(name: String) {
        if (name.isBlank()) return
        _uiState.value = _uiState.value.copy(showNewDialog = false)

        val project = ProjectAutosave.buildProjectFromEngine(name, transportController)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository?.saveProject(project)
            if (result?.isSuccess == true) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentProjectName = name,
                    toastMessage = "Project saved: $name"
                )
                JujiDawApp.instance.currentProjectName = name
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    toastMessage = "Project save failed: ${result?.exceptionOrNull()?.message ?: "repository unavailable"}"
                )
            }
            refreshProjectList()
        }
    }

    // ── Save ────────────────────────────────────────────────────────

    /**
     * Save the currently active project (overwrite).
     * Does nothing if [currentProjectName] is null.
     */
    fun saveProject() {
        val name = _uiState.value.currentProjectName ?: return

        val project = ProjectAutosave.buildProjectFromEngine(name, transportController)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository?.saveProject(project)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                toastMessage = "Project saved: $name"
            )
            refreshProjectList()
        }
    }

    // ── Load ────────────────────────────────────────────────────────

    /**
     * Load a saved project and restore its state into the engine.
     *
     * Restores BPM, patterns, arrangement, mixer state (via JNI),
     * and re-loads audio clips.
     */
    fun loadProject(projectInfo: ProjectInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository?.loadProject(projectInfo.name)
            result?.onSuccess { project ->
                ProjectAutosave.applyProjectToEngine(
                    project,
                    transportController,
                    JujiDawApp.instance.applicationContext,
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentProjectName = project.name,
                    toastMessage = "Loaded: ${project.name}"
                )
            }?.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    toastMessage = "Failed to load: ${e.message}"
                )
            }
            refreshProjectList()
        }
    }

    // ── Delete ──────────────────────────────────────────────────────

    fun showDeleteConfirm(projectInfo: ProjectInfo) {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirm = true,
            projectToDelete = projectInfo
        )
    }

    fun dismissDeleteConfirm() {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirm = false,
            projectToDelete = null
        )
    }

    /** Delete the project that was confirmed via [showDeleteConfirm]. */
    fun deleteProject() {
        val info = _uiState.value.projectToDelete ?: return
        viewModelScope.launch {
            repository?.deleteProject(info.name)
            _uiState.value = _uiState.value.copy(
                showDeleteConfirm = false,
                projectToDelete = null,
                toastMessage = "Deleted: ${info.name}"
            )
            if (_uiState.value.currentProjectName == info.name) {
                _uiState.value = _uiState.value.copy(currentProjectName = null)
                JujiDawApp.instance.currentProjectName = null
            }
            refreshProjectList()
        }
    }

    // ── Rename ──────────────────────────────────────────────────────

    fun showRenameDialog(projectInfo: ProjectInfo) {
        _uiState.value = _uiState.value.copy(
            showRenameDialog = true,
            projectToRename = projectInfo
        )
    }

    fun dismissRenameDialog() {
        _uiState.value = _uiState.value.copy(
            showRenameDialog = false,
            projectToRename = null
        )
    }

    /** Rename the project to [newName]. */
    fun renameProject(newName: String) {
        val info = _uiState.value.projectToRename ?: return
        if (newName.isBlank()) return
        viewModelScope.launch {
            val result = repository?.renameProject(info.name, newName)
            _uiState.value = _uiState.value.copy(
                showRenameDialog = false,
                projectToRename = null
            )
            result?.onSuccess {
                _uiState.value = _uiState.value.copy(
                    toastMessage = "Renamed to: $newName"
                )
                if (_uiState.value.currentProjectName == info.name) {
                    _uiState.value = _uiState.value.copy(currentProjectName = newName)
                    JujiDawApp.instance.currentProjectName = newName
                }
            }?.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    toastMessage = "Rename failed: ${e.message}"
                )
            }
            refreshProjectList()
        }
    }

    // ── WAV Export ──────────────────────────────────────────────────

    /**
     * Export the full arrangement mix to a WAV file at [outputPath].
     *
     * Delegates to [ProjectRepository.exportMix].
     * See its documentation for current limitations and TODOs.
     */
    fun exportMix(outputPath: String) {
        if (_uiState.value.currentProjectName == null) {
            _uiState.value = _uiState.value.copy(toastMessage = "Save a project first")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportProgress = 0f)
            val repo = repository ?: return@launch
            repo.exportMix(
                transportController = transportController,
                outputPath = outputPath,
                onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(exportProgress = progress)
                }
            )
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                exportProgress = 1f,
                toastMessage = "Mix exported"
            )
        }
    }

    /**
     * Export each mixer track as a separate WAV stem to [outputDir].
     *
     * Delegates to [ProjectRepository.exportStems].
     * See its documentation for current limitations and TODOs.
     */
    fun exportStems(outputDir: String) {
        if (_uiState.value.currentProjectName == null) {
            _uiState.value = _uiState.value.copy(toastMessage = "Save a project first")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportProgress = 0f)
            val repo = repository ?: return@launch
            repo.exportStems(
                transportController = transportController,
                outputDir = outputDir,
                onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(exportProgress = progress)
                }
            )
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                exportProgress = 1f,
                toastMessage = "Stems exported"
            )
        }
    }

    // ── Audio Import ────────────────────────────────────────────────

    /**
     * Import an audio file from a content URI.
     *
     * Copies the file into the current project's samples directory,
     * loads it into the engine, and adds an [AudioClip] to the
     * arrangement at the current playhead position.
     */
    fun importAudioClip(uri: Uri?, context: Context) {
        if (uri == null) return
        val projectName = _uiState.value.currentProjectName ?: run {
            _uiState.value = _uiState.value.copy(toastMessage = "Save a project first")
            return
        }
        viewModelScope.launch {
            val repo = repository ?: return@launch
            repo.importAudioClip(uri, projectName)
                .onSuccess { path ->
                    val tick = transportController.transportState.position.toTicks()
                    val clip = AudioClip(
                        id = "clip_${System.nanoTime()}",
                        trackIndex = _uiState.value.recordingTrackIndex,
                        startTick = tick,
                        durationTicks = PPQ * 4L,
                        audioFilePath = path
                    )
                    val newArr = transportController.arrangement.copy(
                        clips = transportController.arrangement.clips + clip
                    )
                    transportController.loadArrangement(newArr)
                    _uiState.value = _uiState.value.copy(
                        toastMessage = "Audio clip imported"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        toastMessage = "Import failed: ${e.message}"
                    )
                }
        }
    }

    // ── Timeline Recording ──────────────────────────────────────────

    /** Set the mixer track that receives newly recorded audio. */
    fun setRecordingTrack(trackIndex: Int) {
        _uiState.value = _uiState.value.copy(recordingTrackIndex = trackIndex.coerceIn(0, 15))
    }

    /** Start timeline recording: arms the transport and starts playback. */
    fun startTimelineRecording() {
        transportController.setRecording(true)
        _uiState.value = _uiState.value.copy(isTimelineRecording = true)
        if (!transportController.transportState.playing) {
            transportController.play()
        }
    }

    /**
     * Stop timeline recording.
     *
     * TODO: After stopping, retrieve the recorded audio buffer from the
     * engine (via [SynthEngine.assignRecordingToPad] + `nativeWritePadToWav`)
     * and create an [AudioClip] in the arrangement at the recorded region.
     * This requires a JNI method to obtain the recorded file path or buffer.
     */
    fun stopTimelineRecording() {
        transportController.setRecording(false)
        _uiState.value = _uiState.value.copy(isTimelineRecording = false)
    }

    // ── Transport ───────────────────────────────────────────────────

    fun playTransport() {
        transportController.play()
        _uiState.value = _uiState.value.copy(isPlaying = true)
    }

    fun stopTransport() {
        transportController.stop()
        _uiState.value = _uiState.value.copy(isPlaying = false, isTimelineRecording = false)
    }

    // ── Toast ───────────────────────────────────────────────────────

    fun consumeToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    // ── Internal ────────────────────────────────────────────────────

    /**
     * Snapshot the current engine mixer state.
     *
     * TODO: The engine does not expose per-track fader/pan/mute/solo
     * getter methods.  This returns a default [MixerState] until the
     * C++ side adds the corresponding JNI getters.  The UI-layer
     * [ProjectUiState] should also cache the last-set mixer values so
     * that save can round-trip accurately.
     */
    private fun captureMixerState(): MixerState {
        return MixerState()
    }

    /**
     * Apply a saved [MixerState] to the engine via JNI setter methods.
     *
     * Insert FX restoration is TODO because the engine does not provide
     * a bulk-set API for insert chains.
     */
    private fun applyMixerState(state: MixerState) {
        for (i in 0 until 16) {
            val track = state.tracks.getOrNull(i) ?: continue
            SynthEngine.setChannelFader(i, track.faderDb)
            SynthEngine.setChannelPan(i, track.pan)
            SynthEngine.setChannelMute(i, track.mute)
            SynthEngine.setChannelSolo(i, track.solo)
            SynthEngine.setChannelArm(i, track.arm)
            SynthEngine.setSendLevel(i, 0, track.sendALevel)
            SynthEngine.setSendLevel(i, 1, track.sendBLevel)
            // TODO: Restore insert FX per slot when engine supports it.
        }
        SynthEngine.setMasterFader(state.masterFaderDb)
        SynthEngine.setBusFader(0, state.busA.faderDb)
        SynthEngine.setBusFader(1, state.busB.faderDb)
        // TODO: Restore bus insert FX.
    }

    override fun onCleared() {
        transportController.release()
        super.onCleared()
    }
}
