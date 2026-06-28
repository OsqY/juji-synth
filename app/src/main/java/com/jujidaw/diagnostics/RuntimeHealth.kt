package com.jujidaw.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import com.jujidaw.audio.AudioEngineManager
import com.jujidaw.audio.SynthEngine
import java.io.File

/**
 * Snapshot of all runtime checks performed by [RuntimeHealth.check].
 *
 * @property ok true when every check passed.
 * @property messages human-readable list of pass/fail lines.
 */
data class RuntimeHealthReport(
    val ok: Boolean,
    val messages: List<String>
)

/**
 * Verifies core device/runtime requirements for the Juji DAW engine.
 *
 * Checks cover native library loading, audio permissions, low-latency
 * audio feature flags, and external storage write access.
 *
 * Intended for use from a diagnostics UI (e.g. an AlertDialog button)
 * or during automated device testing (Group 15 of the openspec).
 */
object RuntimeHealth {

    /**
     * Run all health checks against the given [context].
     *
     * ### Checks performed
     * 1. Native library loaded via [SynthEngine.isLoaded].
     * 2. [android.Manifest.permission.RECORD_AUDIO] granted.
     * 3. Low-latency audio feature flag
     *    ([PackageManager.FEATURE_AUDIO_LOW_LATENCY]) present in the device
     *    package manager. [PackageManager.FEATURE_AUDIO_PRO] is reported but
     *    is NOT required for a PASS.
     * 4. External files directory ([Context.getExternalFilesDir]) exists
     *    and is writable.
     * 5. Audio engine is currently running.
     */
    fun check(context: Context): RuntimeHealthReport {
        val messages = mutableListOf<String>()

        // 1. Native library loaded
        messages.add(
            if (SynthEngine.isLoaded) "PASS  Native library loaded"
            else "FAIL  Native library NOT loaded"
        )

        // 2. RECORD_AUDIO permission
        val recordAudioGranted = context.checkSelfPermission(
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        messages.add(
            if (recordAudioGranted) "PASS  RECORD_AUDIO granted"
            else "FAIL  RECORD_AUDIO denied"
        )

        // 3. Low-latency audio features
        val pm = context.packageManager
        val hasLowLatency = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        val hasProAudio = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)
        messages.add(
            if (hasLowLatency) "PASS  FEATURE_AUDIO_LOW_LATENCY available"
            else "FAIL  FEATURE_AUDIO_LOW_LATENCY missing"
        )
        messages.add(
            if (hasProAudio) "PASS  FEATURE_AUDIO_PRO available"
            else "INFO  FEATURE_AUDIO_PRO missing (not required)"
        )

        // 4. External files dir writable
        val extDir: File? = context.getExternalFilesDir(null)
        val writable = extDir != null && (extDir.isDirectory || extDir.mkdirs()) && extDir.canWrite()
        messages.add(
            if (writable) "PASS  External files dir writable: $extDir"
            else "FAIL  External files dir NOT writable"
        )

        // 5. Engine running
        val engineRunning = AudioEngineManager.isRunning.value
        messages.add(
            if (engineRunning) "PASS  Audio engine running"
            else "FAIL  Audio engine NOT running"
        )

        // 6. Last engine error, if any
        val lastErr = AudioEngineManager.lastError.value
        if (lastErr != null) {
            messages.add("ERROR $lastErr")
        }

        val ok = SynthEngine.isLoaded &&
                recordAudioGranted &&
                hasLowLatency &&
                writable &&
                engineRunning

        return RuntimeHealthReport(ok = ok, messages = messages)
    }
}
