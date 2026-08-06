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
        if (!isBinderLive()) return resolveAbsentBinder()
        if (Shizuku.isPreV11()) return ShizukuState.NotRunning
        if (!hasPermission()) return ShizukuState.PermissionRequired

        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        return ShizukuState.Ready(uid = uid, isRoot = uid == ROOT_UID)
    }

    /**
     * pingBinder() alone reports false until the sticky binder-received callback
     * has been delivered, which made the UI claim Shizuku was stopped while a
     * probe run against it was succeeding. getUid() throws only when there is
     * genuinely no binder, so it settles the question directly.
     */
    private fun isBinderLive(): Boolean {
        if (Shizuku.pingBinder()) return true
        return runCatching { Shizuku.getUid() }.isSuccess
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

    /**
     * The same identity, without the spike's annotations.
     *
     * [describeUid] explains a uid to someone reading a diagnostic report;
     * this names it for someone reading a settings row, where "the path under
     * test" would be commentary on the app rather than information.
     */
    fun shortUid(uid: Int): String = when (uid) {
        SHELL_UID -> "2000 (shell)"
        ROOT_UID -> "0 (root)"
        else -> "$uid"
    }

    /**
     * The installed Shizuku's version name, or null when it is absent.
     *
     * Read from the package manager rather than from Shizuku.getVersion(),
     * which returns the API level (13) rather than the release (13.6.0). The
     * release is what matters here: 13.5.4 on Android 16 crashes within
     * minutes, and this is the surface where that is visible.
     *
     * Available whether or not the service is running, since it does not go
     * through the binder.
     */
    fun installedVersion(): String? {
        val packageManager = SpikeApplication.instance.packageManager
        return SHIZUKU_PACKAGES.firstNotNullOfOrNull { candidate ->
            runCatching {
                packageManager.getPackageInfo(candidate, 0).versionName
            }.getOrNull()
        }
    }

    /** Shizuku proper, and Sui's package for root-backed installs. */
    private val SHIZUKU_PACKAGES = listOf("moe.shizuku.privileged.api", "moe.shizuku.redirect")
}
