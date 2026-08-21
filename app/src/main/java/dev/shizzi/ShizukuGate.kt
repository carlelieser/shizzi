package dev.shizzi

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

sealed interface ShizukuState {
    data object NotInstalled : ShizukuState
    data object NotRunning : ShizukuState
    data object PermissionRequired : ShizukuState
    data class Ready(val uid: Int, val isRoot: Boolean) : ShizukuState
}

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

    private fun isBinderLive(): Boolean {
        if (Shizuku.pingBinder()) return true
        return runCatching { Shizuku.getUid() }.isSuccess
    }

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

    fun requestPermission() {
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun describeUid(uid: Int): String = when (uid) {
        SHELL_UID -> "shell (2000) — the path under test"
        ROOT_UID -> "root (0) — Sui; behaviour may differ (P-5)"
        else -> "unexpected uid $uid"
    }

    fun shortUid(uid: Int): String = when (uid) {
        SHELL_UID -> "2000 (shell)"
        ROOT_UID -> "0 (root)"
        else -> "$uid"
    }

    fun installedVersion(): String? {
        val packageManager = App.instance.packageManager
        return SHIZUKU_PACKAGES.firstNotNullOfOrNull { candidate ->
            runCatching {
                packageManager.getPackageInfo(candidate, 0).versionName
            }.getOrNull()
        }
    }

    private val SHIZUKU_PACKAGES = listOf("moe.shizuku.privileged.api", "moe.shizuku.redirect")
}
