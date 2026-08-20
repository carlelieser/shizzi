package dev.shizzi

/**
 * Where a diagnostics run is.
 *
 * Kept out of [SessionUiState] because a probe run holds no session and the two
 * overlap in time — diagnostics are startable while a session is up, and one
 * state cannot describe both.
 */
sealed interface DiagnosticsState {

    /** Nothing has run this visit, or the last result has been dismissed. */
    data object Idle : DiagnosticsState

    /** The settings screen is inert while this holds. */
    data object Running : DiagnosticsState

    /**
     * [report] is carried rather than re-read: the shell wrote it to [path]
     * under a different uid, so the app cannot read it back. Holding the
     * string is what makes an export possible at all.
     */
    data class Complete(val report: String, val path: String) : DiagnosticsState

    data class Failed(val problem: String) : DiagnosticsState
}
