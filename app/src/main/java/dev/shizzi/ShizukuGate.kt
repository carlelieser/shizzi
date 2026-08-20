package dev.shizzi

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** The four states R1.2 requires the UI to distinguish. */
sealed interface ShizukuState {
    data object NotInstalled : ShizukuState
    data object NotRunning : ShizukuState
    data object PermissionRequired : ShizukuState
    data class Ready(val uid: Int, val isRoot: Boolean) : ShizukuState
}

/** Never requests permission: R1.3 forbids auto-requesting on launch. */
object ShizukuGate {

    const val PERMISSION_REQUEST_CODE = 4001

    private const val SHELL_UID = 2000
    private const val ROOT_UID = 0

    fun currentState(): ShizukuState {
        if (!isBinderLive()) return resolveAbsentBinder()
        if (Shizuku.isPreV11()) return ShizukuState.NotRunning
        if (!hasPermission()) return ShizukuState.PermissionRequired

        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        return ShizukuState.Ready(uid = uid, isRoot = uid == ROOT_UID)
    }

    /**
     * pingBinder() reports false until the sticky binder-received callback
     * lands, which had the UI claiming Shizuku was stopped while a probe run
     * against it succeeded. getUid() throws only when there is truly no binder.
     */
    private fun isBinderLive(): Boolean {
        if (Shizuku.pingBinder()) return true
        return runCatching { Shizuku.getUid() }.isSuccess
    }

    /**
     * Absent or merely stopped drives different UI guidance (P-1 vs P-2), so
     * the package manager settles it rather than a guess.
     */
    private fun resolveAbsentBinder(): ShizukuState = when {
        isShizukuInstalled() -> ShizukuState.NotRunning
        else -> ShizukuState.NotInstalled
    }

    private fun isShizukuInstalled(): Boolean {
        val packageManager = App.instance.packageManager
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
     * The same identity for a settings row, where [describeUid]'s "the path
     * under test" would be commentary on the app rather than information.
     */
    fun shortUid(uid: Int): String = when (uid) {
        SHELL_UID -> "2000 (shell)"
        ROOT_UID -> "0 (root)"
        else -> "$uid"
    }

    /**
     * From the package manager, not Shizuku.getVersion(), which returns the API
     * level (13) rather than the release (13.6.0) — and the release is what
     * matters, since 13.5.4 on Android 16 crashes within minutes.
     *
     * Works whether or not the service is running; no binder involved.
     */
    fun installedVersion(): String? {
        val packageManager = App.instance.packageManager
        return SHIZUKU_PACKAGES.firstNotNullOfOrNull { candidate ->
            runCatching {
                packageManager.getPackageInfo(candidate, 0).versionName
            }.getOrNull()
        }
    }

    /** Shizuku proper, and Sui's package for root-backed installs. */
    private val SHIZUKU_PACKAGES = listOf("moe.shizuku.privileged.api", "moe.shizuku.redirect")
}
