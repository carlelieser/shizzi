package dev.shizzi.spike

import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku

/** The four states R1.2 requires the UI to distinguish, plus the API floor. */
sealed interface ShizukuState {
    data object NotInstalled : ShizukuState
    data object NotRunning : ShizukuState
    data object PermissionRequired : ShizukuState
    data class Ready(val uid: Int, val isRoot: Boolean) : ShizukuState
    data class UnsupportedPlatform(val sdkInt: Int) : ShizukuState
}

/**
 * Resolves current Shizuku availability.
 *
 * Never requests permission — R1.3 forbids auto-requesting on launch, so the
 * request is a separate explicit call driven by a user action.
 */
object ShizukuGate {

    const val FEATURE_MIN_API = 33
    const val PERMISSION_REQUEST_CODE = 4001

    private const val SHELL_UID = 2000
    private const val ROOT_UID = 0

    fun currentState(): ShizukuState {
        if (Build.VERSION.SDK_INT < FEATURE_MIN_API) {
            return ShizukuState.UnsupportedPlatform(Build.VERSION.SDK_INT)
        }
        if (!Shizuku.pingBinder()) return resolveAbsentBinder()
        if (Shizuku.isPreV11()) return ShizukuState.NotRunning
        if (!hasPermission()) return ShizukuState.PermissionRequired

        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        return ShizukuState.Ready(uid = uid, isRoot = uid == ROOT_UID)
    }

    /**
     * A dead binder means either Shizuku is absent or merely stopped. The
     * distinction drives different UI guidance (P-1 vs P-2), so it is resolved
     * from the package manager rather than guessed.
     */
    private fun resolveAbsentBinder(): ShizukuState = when {
        isShizukuInstalled() -> ShizukuState.NotRunning
        else -> ShizukuState.NotInstalled
    }

    private fun isShizukuInstalled(): Boolean {
        val packageManager = SpikeApplication.instance.packageManager
        return SHIZUKU_PACKAGES.any { candidate -> isPackagePresent(packageManager, candidate) }
    }

    private fun isPackagePresent(packageManager: PackageManager, name: String): Boolean =
        runCatching { packageManager.getPackageInfo(name, 0) }.isSuccess

    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Explicit user-driven request only (R1.3). */
    fun requestPermission() {
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun describeUid(uid: Int): String = when (uid) {
        SHELL_UID -> "shell (2000) — the path under test"
        ROOT_UID -> "root (0) — Sui; behaviour may differ (P-5)"
        else -> "unexpected uid $uid"
    }

    /** Shizuku proper, and Sui's package for root-backed installs. */
    private val SHIZUKU_PACKAGES = listOf("moe.shizuku.privileged.api", "moe.shizuku.redirect")
}
