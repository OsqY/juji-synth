package com.jujidaw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.jujidaw.audio.AudioEngineManager
import com.jujidaw.project.ProjectAutosave
import com.jujidaw.ui.main.MainScreen
import com.jujidaw.ui.theme.JujiDawTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LaunchedEffect(Unit) {
                // Auto-load the last project on first composition.
                val app = application as JujiDawApp
                ProjectAutosave.autoLoad(app, app.transportController)
            }

            JujiDawTheme {
                MainScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AudioEngineManager.ensureStartedWithToast(this)
    }

    override fun onStop() {
        super.onStop()
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
