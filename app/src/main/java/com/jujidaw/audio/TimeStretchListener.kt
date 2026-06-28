package com.jujidaw.audio

/**
 * Listener for asynchronous SoundTouch time-stretch completion events
 * dispatched from the C++ worker thread back to the JVM.
 *
 * Implementations should be safe to call from any thread; the JNI bridge
 * is responsible for dispatching onto the JVM context.
 */
interface TimeStretchListener {
    /**
     * Called when a time-stretch job finishes.
     *
     * @param padIndex the pad that was processed
     * @param success true if the buffer was successfully time-stretched, false otherwise
     */
    fun onTimeStretchComplete(padIndex: Int, success: Boolean)
}
