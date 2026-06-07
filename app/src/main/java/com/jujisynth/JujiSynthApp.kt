package com.jujisynth

import android.app.Application
import androidx.room.Room
import com.jujisynth.data.PresetDatabase
import com.jujisynth.data.seedFactoryPresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JujiSynthApp : Application() {

    val database: PresetDatabase by lazy {
        Room.databaseBuilder(
            this,
            PresetDatabase::class.java,
            "juji-synth-db"
        ).build()
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Seed factory presets on first launch
        applicationScope.launch {
            val dao = database.presetDao()
            if (dao.getPresetCount() == 0) {
                seedFactoryPresets(database)
            }
        }
    }

    companion object {
        lateinit var instance: JujiSynthApp
            private set
    }
}
