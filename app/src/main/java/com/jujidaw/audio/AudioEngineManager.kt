package com.jujidaw.audio

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-level owner of the native audio engine lifecycle.
 *
 * This manager keeps the engine running across Activity lifecycle transitions
 * (file pickers, permission dialogs, configuration changes) instead of tying
 * it to a single Activity's `onStop()`/`onStart()` pair.
 */
object AudioEngineManager {

    /** Result of an engine start attempt. */
    sealed class StartResult {
        data class Success(val sampleRate: Int, val framesPerBurst: Int) : StartResult()
        data class Failure(val message: String) : StartResult()
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Start the engine if it is not already running. Idempotent. */
    fun ensureStarted(): StartResult {
        if (_isRunning.value && SynthEngine.isRunning()) {
            return StartResult.Success(
                sampleRate = SynthEngine.getNativeSampleRate(),
                framesPerBurst = SynthEngine.getNativeFramesPerBurst(),
            )
        }
        _isRunning.value = false
        return startInternal()
    }

    /** Explicit start; useful for retry after a failure. */
    fun start(): StartResult = startInternal()

    private fun startInternal(): StartResult {
        _lastError.value = null
        val ok = SynthEngine.nativeStart()
        _isRunning.value = ok
        return if (ok) {
            LOGI("AudioEngineManager: engine started")
            StartResult.Success(
                sampleRate = SynthEngine.getNativeSampleRate(),
                framesPerBurst = SynthEngine.getNativeFramesPerBurst()
            )
        } else {
            val msg = SynthEngine.getNativeLastError()
                ?.takeIf { it.isNotBlank() }
                ?: "Audio engine failed to start"
            _lastError.value = msg
            LOGE("AudioEngineManager: $msg")
            StartResult.Failure(msg)
        }
    }

    /** Stop the engine. Safe to call multiple times. */
    fun stop() {
        if (!_isRunning.value) return
        SynthEngine.nativeStop()
        _isRunning.value = false
        LOGI("AudioEngineManager: engine stopped")
    }

    /**
     * Convenience: ensure the engine is started and show a Toast on failure.
     * Intended for use from Activities.
     */
    fun ensureStartedWithToast(context: Context): Boolean {
        return when (val result = ensureStarted()) {
            is StartResult.Success -> true
            is StartResult.Failure -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                false
            }
        }
    }

    private fun LOGI(msg: String) = android.util.Log.i("JujiDaw", msg)
    private fun LOGE(msg: String) = android.util.Log.e("JujiDaw", msg)
}
