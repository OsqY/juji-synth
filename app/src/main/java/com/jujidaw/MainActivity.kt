package com.jujidaw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.jujidaw.audio.AudioEngineManager
import com.jujidaw.ui.main.MainScreen
import com.jujidaw.ui.theme.JujiDawTheme

class MainActivity : ComponentActivity() {
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
        // Engine is owned by the Application; ensure it is running whenever
        // this Activity becomes visible again (cold start, permission dialog,
        // file picker, backgrounding, etc.).
        AudioEngineManager.ensureStartedWithToast(this)
    }

    // Do NOT stop the engine in onStop(). The audio engine must survive
    // Activity transitions such as file pickers and permission dialogs.
}
