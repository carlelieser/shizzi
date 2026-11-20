package dev.shizzi

import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku

/** Calls [onReady] each time Shizuku becomes usable again, while started. */
class ShizukuWatch(private val onReady: () -> Unit) {

    private val handler = Handler(Looper.getMainLooper())

    private val binderReceived = Shizuku.OnBinderReceivedListener { checkReady() }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> checkReady() }

    private var isWatching = false

    @Synchronized
    fun start() {
        if (isWatching) return
        isWatching = true

        Shizuku.addRequestPermissionResultListener(permissionResult, handler)
        Shizuku.addBinderReceivedListenerSticky(binderReceived, handler)
    }

    @Synchronized
    fun stop() {
        if (!isWatching) return
        isWatching = false

        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
    }

    private fun checkReady() {
        val isReady = synchronized(this) { isWatching } &&
            ShizukuGate.currentState() is ShizukuState.Ready
        if (isReady) onReady()
    }
}
