package com.jujidaw.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiOutputPort
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages USB MIDI device connections and message routing.
 *
 * Incoming note/CC/pitch events are forwarded to [router] which dispatches
 * them to the correct engine target based on the active keyboard target and
 * the persisted mapping table.
 *
 * Sustain pedal (CC 64) is handled locally because it affects note-off
 * timing regardless of mapping.
 */
class MidiController(context: Context, val router: MidiRouter) {

    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) { refreshDevices() }
        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            refreshDevices()
            if (_activeDevice.value?.info == device) disconnect()
        }
    }

    private val _connectedDevices = MutableStateFlow<List<MidiDeviceInfo>>(emptyList())
    val connectedDevices: StateFlow<List<MidiDeviceInfo>> = _connectedDevices.asStateFlow()

    private val _activeDevice = MutableStateFlow<MidiDevice?>(null)
    val activeDevice: StateFlow<MidiDevice?> = _activeDevice.asStateFlow()

    private var outputPort: MidiOutputPort? = null

    // Sustain pedal state
    private var sustainOn = false
    private val sustainedNotes = mutableSetOf<Int>()

    companion object {
        /**
         * Legacy callback fired on every incoming MIDI CC for MIDI Learn
         * capture in the old synth screen.
         *
         * New code should use [MidiRouter.startLearn] / [MidiRouter.onLearnCaptured]
         * instead.
         */
        @Deprecated("Use MidiRouter learn API")
        var onCcLearnCallback: ((ccNumber: Int, value: Float) -> Unit)? = null
    }

    /** Start listening for MIDI device connections */
    fun startScanning() {
        midiManager.registerDeviceCallback(deviceCallback, mainHandler)
        refreshDevices()
    }

    /** Connect to the first available MIDI device */
    suspend fun connectToFirst(): Boolean = withContext(Dispatchers.IO) {
        val devices = getDeviceList()
        if (devices.isEmpty()) return@withContext false
        connectToDevice(devices.first())
    }

    /** Connect to a specific MIDI device */
    suspend fun connectToDevice(deviceInfo: MidiDeviceInfo): Boolean = withContext(Dispatchers.IO) {
        midiManager.openDevice(deviceInfo, { device ->
            if (device != null) {
                val portInfo = device.info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
                if (portInfo != null) {
                    val port = device.openOutputPort(portInfo.portNumber)
                    port?.connect(object : android.media.midi.MidiReceiver() {
                        override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
                            handleMidiMessage(msg, offset, count)
                        }
                    })
                    outputPort?.close()
                    outputPort = port
                    _activeDevice.value = device
                    refreshDevices()
                }
            }
        }, mainHandler)
        true
    }

    /** Disconnect from the current device */
    fun disconnect() {
        outputPort?.close()
        outputPort = null
        _activeDevice.value?.close()
        _activeDevice.value = null
        refreshDevices()
    }

    fun stopScanning() {
        midiManager.unregisterDeviceCallback(deviceCallback)
        disconnect()
    }

    private fun refreshDevices() {
        _connectedDevices.value = getDeviceList()
    }

    private fun getDeviceList(): List<MidiDeviceInfo> {
        return midiManager.devices?.filter {
            it.ports.any { p -> p.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
        }?.toList() ?: emptyList()
    }

    private fun handleMidiMessage(msg: ByteArray?, offset: Int, count: Int) {
        if (msg == null || count < 2) return

        val status = msg[offset].toInt() and 0xFF
        val command = status and 0xF0
        @Suppress("UNUSED")
        val channel = status and 0x0F

        when (command) {
            0x90 -> { // Note On
                val note = msg[offset + 1].toInt() and 0x7F
                val velocity = msg[offset + 2].toInt() and 0x7F
                if (velocity > 0) {
                    router.processNoteOn(note, velocity)
                } else {
                    // Note on with velocity 0 = note off
                    if (sustainOn) {
                        sustainedNotes.add(note)
                    } else {
                        router.processNoteOff(note)
                    }
                }
            }
            0x80 -> { // Note Off (some controllers send this instead of 0x90 with velocity 0)
                val note = msg[offset + 1].toInt() and 0x7F
                if (sustainOn) {
                    sustainedNotes.add(note)
                } else {
                    router.processNoteOff(note)
                }
            }
            0xB0 -> { // Control Change
                val controller = msg[offset + 1].toInt() and 0x7F
                val value = (msg[offset + 2].toInt() and 0x7F) / 127.0f

                // Fire legacy learn callback (used by MainSynthScreen)
                onCcLearnCallback?.invoke(controller, value)

                if (controller == 64) { // Sustain pedal — handled locally
                    sustainOn = value > 0.5f
                    if (!sustainOn) {
                        sustainedNotes.forEach { router.processNoteOff(it) }
                        sustainedNotes.clear()
                    }
                }
                // Route all CCs (including sustain) through the router
                router.processCc(controller, value)
            }
            0xE0 -> { // Pitch Bend
                val lsb = msg[offset + 1].toInt() and 0x7F
                val msb = msg[offset + 2].toInt() and 0x7F
                val bend = ((msb shl 7) or lsb) - 8192
                val normalized = bend / 8192.0f
                router.processPitchBend(normalized)
            }
        }
    }
}
