package com.jujisynth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.jujisynth.audio.SynthEngine
import com.jujisynth.ui.MainSynthScreen
import com.jujisynth.ui.theme.JujiSynthTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JujiSynthTheme {
                // Start audio engine when UI is ready
                LaunchedEffect(Unit) {
                    SynthEngine.start()
                }

                MainSynthScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        SynthEngine.stop()
    }
}
