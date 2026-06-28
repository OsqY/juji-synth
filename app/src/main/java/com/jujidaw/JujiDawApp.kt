package com.jujidaw

import android.app.Application
import androidx.room.Room
import com.jujidaw.audio.AudioEngineManager
import com.jujidaw.audio.SynthEngine
import com.jujidaw.data.MidiMappingStore
import com.jujidaw.data.PresetDatabase
import com.jujidaw.data.seedFactoryPresets
import com.jujidaw.engine.TransportController
import com.jujidaw.midi.MidiRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JujiDawApp : Application() {

    val database: PresetDatabase by lazy {
        Room.databaseBuilder(
            this,
            PresetDatabase::class.java,
            "juji-daw-db"
        ).build()
    }

    val transportController: TransportController by lazy {
        TransportController()
    }

    /** Application-wide MIDI router.  Created on first access. */
    val midiRouter: MidiRouter by lazy {
        MidiRouter(MidiMappingStore(this)).also { it.load() }
    }

    // Current project name (null = no project loaded / autosave only).
    var currentProjectName: String? = null

    /** App-wide scope for fire-and-forget work (auto-save, etc.). */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Start the audio engine at app level so it survives Activity transitions.
        if (SynthEngine.isLoaded) {
            AudioEngineManager.ensureStarted()
        }

        // Seed factory presets on first launch
        applicationScope.launch {
            val dao = database.presetDao()
            if (dao.getPresetCount() == 0) {
                seedFactoryPresets(database)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        AudioEngineManager.stop()
    }

    companion object {
        lateinit var instance: JujiDawApp
            private set
    }
}
