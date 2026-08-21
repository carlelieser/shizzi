package dev.shizzi

sealed interface DiagnosticsState {

    data object Idle : DiagnosticsState

    data object Running : DiagnosticsState

    data class Complete(val report: String, val path: String) : DiagnosticsState

    data class Failed(val problem: String) : DiagnosticsState
}
