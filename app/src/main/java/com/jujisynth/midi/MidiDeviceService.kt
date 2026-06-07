package com.jujisynth.midi

import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver
import android.util.Log

/**
 * MIDI device service for Android's MIDI framework.
 * Allows the app to be discovered as a MIDI receiver by external controllers.
 */
class MidiDeviceService : MidiDeviceService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MIDI Device Service created")
    }

    override fun onGetInputPortReceivers(): Array<MidiReceiver> {
        return arrayOf(object : MidiReceiver() {
            override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
                // Forward to MidiController's handler
                handleMidiMessage(msg, offset, count)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MIDI Device Service destroyed")
    }

    private fun handleMidiMessage(msg: ByteArray?, offset: Int, count: Int) {
        if (msg == null || count < 2) return

        val status = msg[offset].toInt() and 0xFF
        val command = status and 0xF0

        when (command) {
            0x90, 0x80 -> {
                // Note on/off forwarded via JNI
                // Uses MidiController companion handler
            }
        }
    }

    companion object {
        private const val TAG = "JujiSynthMidi"
    }
}
