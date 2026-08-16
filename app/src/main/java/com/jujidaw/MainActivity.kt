package com.jujidaw

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.jujidaw.audio.AudioEngineManager
import com.jujidaw.project.ProjectAutosave
import com.jujidaw.ui.main.MainScreen
import com.jujidaw.ui.theme.JujiDawTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var restoreRunning = false
    private var restoreComplete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JujiDawTheme {
                MainScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        when (val result = (application as JujiDawApp).ensureAudioEngineStarted()) {
            is AudioEngineManager.StartResult.Success -> {
                if (!restoreRunning && !restoreComplete) {
                    restoreRunning = true
                    val app = application as JujiDawApp
                    lifecycleScope.launch {
                        try {
                            ProjectAutosave.autoLoad(app, app.transportController)
                                .onSuccess { restoreComplete = true }
                                .onFailure {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Could not restore the last project: ${it.message ?: "unknown error"}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                        } finally {
                            restoreRunning = false
                        }
                    }
                }
            }
            is AudioEngineManager.StartResult.Failure ->
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!restoreComplete) return
        // Auto-save the current project when the app goes to background.
        val app = application as JujiDawApp
        JujiDawApp.instance.applicationScope.launch {
            ProjectAutosave.autoSave(
                app, app.transportController,
                app.currentProjectName ?: ProjectAutosave.AUTOSAVE_NAME
            )
        }
    }
}
