package com.jujidaw.ui.mixer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jujidaw.audio.SynthEngine
import com.jujidaw.model.MidiTarget
import com.jujidaw.midi.MidiRouter
import com.jujidaw.project.AutomationPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One of the 8 live performance FX pads. */
enum class PerformFxType(val label: String) {
    STUTTER("Stutter"),
    GATE("Gate"),
    CUTTER("Cutter"),
    REVERSE("Reverse"),
    DELAY_FREEZE("Dly Freeze"),
    FILTER_SWEEP("Flt Sweep"),
    BITCRUSH("Bitcrush"),
    TAPE_STOP("Tape Stop")
}

/** State of a single insert FX slot on a mixer channel. */
data class InsertSlot(
    val type: SynthEngine.EffectType = SynthEngine.EffectType.None,
    val bypass: Boolean = false
)

/** State of one mixer channel (track). */
data class ChannelState(
    val faderDb: Float = 0f,
    val pan: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val arm: Boolean = false,
    val level: Float = 0f,
    val sendA: Float = 0f,
    val sendB: Float = 0f,
    val inserts: List<InsertSlot> = List(4) { InsertSlot() }
)

/** Master bus state. */
data class MasterState(
    val faderDb: Float = 0f,
    val level: Float = 0f
)

/** Full UI state for the mixer screen. */
data class MixerUiState(
    val channels: List<ChannelState> = List(16) { ChannelState() },
    val master: MasterState = MasterState(),
    val selectedChannel: Int = 0,
    val showInsertSheet: Boolean = false,
    val showAutomationSheet: Boolean = false,
    val selectedAutomationParam: String? = null,
    val automationPoints: List<AutomationPoint> = emptyList(),
    val armedAutomationParams: Set<Int> = emptySet(),
    val toastMessage: String? = null,
    val activePerformFx: Set<PerformFxType> = emptySet(),
    // MIDI Learn
    val midiLearnTarget: MidiTarget? = null
)

/**
 * ViewModel for the mixer / FX screen.
 *
 * Manages 16 channel strips + master, insert FX chains, send levels,
 * live perform FX toggles, and automation lane selection.
 *
 * ### Public API
 * - [uiState] – observable screen state
 * - Channel controls: [setChannelFader], [setChannelPan], [toggleMute], [toggleSolo], [toggleArm]
 * - Send controls: [setSendLevel]
 * - Master controls: [setMasterFader]
 * - Insert FX: [addInsertEffect], [removeInsertEffect], [toggleInsertBypass], [reorderInsert]
 * - Perform FX: [togglePerformFx]
 * - Automation: [selectAutomationParam], [addAutomationPoint] (TODO)
 * - MIDI Learn: [startLearn], [stopLearn], [midiRouter]
 */
class MixerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MixerUiState())
    val uiState: StateFlow<MixerUiState> = _uiState.asStateFlow()

    /** Application-wide MIDI router reference.  null until app is initialised. */
    var midiRouter: MidiRouter? = null

    init {
        startLevelPolling()
    }

    private fun startLevelPolling() {
        viewModelScope.launch {
            while (isActive) {
                val newChannels = if (SynthEngine.isLoaded) {
                    _uiState.value.channels.mapIndexed { index, ch ->
                        val level = try {
                            SynthEngine.getChannelLevel(index)
                        } catch (_: Exception) {
                            0f
                        }
                        ch.copy(level = level.coerceIn(0f, 1f))
                    }
                } else {
                    _uiState.value.channels
                }
                // TODO: engine does not expose a dedicated master level getter;
                // index 16 is a guess. Replace when nativeGetMasterLevel is added.
                val masterLevel = if (SynthEngine.isLoaded) {
                    try {
                        SynthEngine.getChannelLevel(16)
                    } catch (_: Exception) {
                        0f
                    }
                } else 0f
                _uiState.value = _uiState.value.copy(
                    channels = newChannels,
                    master = _uiState.value.master.copy(level = masterLevel.coerceIn(0f, 1f))
                )
                delay(50)
            }
        }
    }

    // ---- Channel controls ----

    fun selectChannel(trackIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedChannel = trackIndex.coerceIn(0, 15))
    }

    fun setChannelFader(trackIndex: Int, db: Float) {
        val clamped = db.coerceIn(-60f, 12f)
        SynthEngine.setChannelFader(trackIndex, clamped)
        updateChannel(trackIndex) { it.copy(faderDb = clamped) }
    }

    fun setChannelPan(trackIndex: Int, pan: Float) {
        val clamped = pan.coerceIn(-1f, 1f)
        SynthEngine.setChannelPan(trackIndex, clamped)
        updateChannel(trackIndex) { it.copy(pan = clamped) }
    }

    fun toggleMute(trackIndex: Int) {
        val newMute = !_uiState.value.channels[trackIndex].mute
        SynthEngine.setChannelMute(trackIndex, newMute)
        updateChannel(trackIndex) { it.copy(mute = newMute) }
    }

    fun toggleSolo(trackIndex: Int) {
        val newSolo = !_uiState.value.channels[trackIndex].solo
        SynthEngine.setChannelSolo(trackIndex, newSolo)
        updateChannel(trackIndex) { it.copy(solo = newSolo) }
    }

    fun toggleArm(trackIndex: Int) {
        val newArm = !_uiState.value.channels[trackIndex].arm
        SynthEngine.setChannelArm(trackIndex, newArm)
        updateChannel(trackIndex) { it.copy(arm = newArm) }
    }

    fun setSendLevel(trackIndex: Int, bus: Int, level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        SynthEngine.setSendLevel(trackIndex, bus, clamped)
        updateChannel(trackIndex) {
            when (bus) {
                0 -> it.copy(sendA = clamped)
                1 -> it.copy(sendB = clamped)
                else -> it
            }
        }
    }

    // ---- Master controls ----

    fun setMasterFader(db: Float) {
        val clamped = db.coerceIn(-60f, 12f)
        SynthEngine.setMasterFader(clamped)
        _uiState.value = _uiState.value.copy(master = _uiState.value.master.copy(faderDb = clamped))
    }

    // ---- Insert FX ----

    fun addInsertEffect(trackIndex: Int, slot: Int, type: SynthEngine.EffectType) {
        if (type == SynthEngine.EffectType.None) return
        SynthEngine.addInsertEffect(trackIndex, slot, type)
        updateChannel(trackIndex) { ch ->
            val inserts = ch.inserts.toMutableList()
            inserts[slot] = InsertSlot(type = type, bypass = false)
            ch.copy(inserts = inserts)
        }
    }

    fun removeInsertEffect(trackIndex: Int, slot: Int) {
        SynthEngine.removeInsertEffect(trackIndex, slot)
        updateChannel(trackIndex) { ch ->
            val inserts = ch.inserts.toMutableList()
            inserts[slot] = InsertSlot()
            ch.copy(inserts = inserts)
        }
    }

    fun toggleInsertBypass(trackIndex: Int, slot: Int) {
        val current = _uiState.value.channels[trackIndex].inserts.getOrNull(slot) ?: return
        val newBypass = !current.bypass
        SynthEngine.setInsertBypass(trackIndex, slot, newBypass)
        updateChannel(trackIndex) { ch ->
            val inserts = ch.inserts.toMutableList()
            inserts[slot] = current.copy(bypass = newBypass)
            ch.copy(inserts = inserts)
        }
    }

    /** Swap two insert slots in the UI. Engine reorder API is TODO. */
    fun reorderInsert(trackIndex: Int, fromSlot: Int, toSlot: Int) {
        if (fromSlot == toSlot) return
        updateChannel(trackIndex) { ch ->
            val inserts = ch.inserts.toMutableList()
            val tmp = inserts[fromSlot]
            inserts[fromSlot] = inserts[toSlot]
            inserts[toSlot] = tmp
            ch.copy(inserts = inserts)
        }
        // TODO: engine does not expose a nativeReorderInsertEffect;
        // re-add effects in new order if required.
    }

    // ---- Perform FX ----

    fun togglePerformFx(type: PerformFxType) {
        val currentlyActive = type in _uiState.value.activePerformFx
        val newSet = if (currentlyActive) {
            _uiState.value.activePerformFx - type
        } else {
            _uiState.value.activePerformFx + type
        }
        _uiState.value = _uiState.value.copy(activePerformFx = newSet)
        showToast("Perform FX: ${type.label} ${if (currentlyActive) "OFF" else "ON"} (TODO: engine integration)")
    }

    // ---- Automation ----

    fun selectAutomationParam(paramId: String?) {
        _uiState.value = _uiState.value.copy(
            selectedAutomationParam = paramId,
            showAutomationSheet = paramId != null
        )
    }

    /** Placeholder for automation point writing. */
    /** Call this when the user draws/edits automation points in the lane overlay. */
    fun updateAutomationPoints(points: List<AutomationPoint>) {
        _uiState.value = _uiState.value.copy(automationPoints = points)
    }

    fun addAutomationPoint(paramId: String, tick: Long, value: Float) {
        // TODO: TransportController does not yet expose setAutomationPoint.
        // When available, call it here and mirror into arrangement state.
        showToast("Automation: $paramId @ tick=$tick value=$value (TODO)")
    }

    // ---- MIDI Learn ----

    /**
     * Enter MIDI learn mode for the given mixer/effect [target].
     *
     * The next incoming MIDI CC will be captured and persistently mapped
     * to this target.  Call [stopLearn] to cancel before a CC arrives.
     */
    fun startLearn(target: MidiTarget) {
        val router = midiRouter ?: return
        router.startLearn(target)
        _uiState.value = _uiState.value.copy(midiLearnTarget = target)
        showToast("MIDI Learn: move a controller for ${target.displayLabel}")
    }

    /** Cancel MIDI learn mode without capturing. */
    fun stopLearn() {
        midiRouter?.stopLearn()
        _uiState.value = _uiState.value.copy(midiLearnTarget = null)
    }

    // ---- Sheets ----

    fun showInsertSheet() {
        _uiState.value = _uiState.value.copy(showInsertSheet = true)
    }

    fun dismissInsertSheet() {
        _uiState.value = _uiState.value.copy(showInsertSheet = false)
    }

    fun showAutomationSheet() {
        _uiState.value = _uiState.value.copy(showAutomationSheet = true)
    }

    fun dismissAutomationSheet() {
        _uiState.value = _uiState.value.copy(showAutomationSheet = false)
    }

    fun consumeToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    // ---- Helpers ----

    private fun updateChannel(trackIndex: Int, transform: (ChannelState) -> ChannelState) {
        val channels = _uiState.value.channels.toMutableList()
        if (trackIndex in channels.indices) {
            channels[trackIndex] = transform(channels[trackIndex])
            _uiState.value = _uiState.value.copy(channels = channels)
        }
    }

    private fun showToast(msg: String) {
        _uiState.value = _uiState.value.copy(toastMessage = msg)
    }
}
