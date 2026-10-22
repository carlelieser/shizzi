package dev.shizzi

import android.content.Context

enum class PrivilegeBackendType { SHIZUKU, LOCAL_ADB }

sealed interface PrivilegeState {
    val backend: PrivilegeBackendType

    data class Ready(
        override val backend: PrivilegeBackendType,
        val uid: Int = 2000,
        val isRoot: Boolean = false,
    ) : PrivilegeState

    data class SetupRequired(override val backend: PrivilegeBackendType) : PrivilegeState
    data class Connecting(
        override val backend: PrivilegeBackendType,
        val detail: String = "",
    ) : PrivilegeState
    data class Unsupported(override val backend: PrivilegeBackendType) : PrivilegeState
    data class Error(override val backend: PrivilegeBackendType, val message: String) : PrivilegeState
}

interface PrivilegedClient {
    val backend: PrivilegeBackendType
    var onSessionLost: (() -> Unit)?

    suspend fun runProbes(attemptTethering: Boolean): String
    suspend fun checkCompatibility(): List<CapabilityResult>
    suspend fun installTetheringApex(path: String): StagingOutcome
    suspend fun rebootDevice(): String
    suspend fun start(logging: Boolean, vpnMode: VpnMode): String
    fun setLogging(enabled: Boolean)
    suspend fun stop(): String
    suspend fun clearLog(): String?
    suspend fun releaseOrphanedDownstream(): String?
    suspend fun status(): String
    fun unbind()
    fun unbindAndStopDaemon()
}

object PrivilegeClients {
    fun create(context: Context, backend: PrivilegeBackendType): PrivilegedClient = when (backend) {
        PrivilegeBackendType.SHIZUKU -> TetherClient()
        PrivilegeBackendType.LOCAL_ADB -> LocalAdbTetherClient(context.applicationContext)
    }
}

object PrivilegeGate {
    fun shizukuState(): PrivilegeState = when (val state = ShizukuGate.currentState()) {
        is ShizukuState.Ready -> PrivilegeState.Ready(
            backend = PrivilegeBackendType.SHIZUKU,
            uid = state.uid,
            isRoot = state.isRoot,
        )
        ShizukuState.PermissionRequired,
        ShizukuState.NotRunning,
        ShizukuState.NotInstalled -> PrivilegeState.SetupRequired(PrivilegeBackendType.SHIZUKU)
    }
}
