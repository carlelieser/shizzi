package dev.shizzi

/**
 * Where a diagnostics run is, as the settings screen needs to render it.
 *
 * Separate from [SessionUiState] rather than folded into it. A probe run holds
 * no session — it acquires and releases its own resources — so reporting it
 * through the session's status would caption the home screen with the progress
 * of something that is not a session. The two also overlap in time: diagnostics
 * are startable while a session is up, and one state cannot describe both.
 */
sealed interface DiagnosticsState {

    /** Nothing has run this visit, or the last result has been dismissed. */
    data object Idle : DiagnosticsState

    /** A run is in flight. The settings screen is inert while this holds. */
    data object Running : DiagnosticsState

    /**
     * A finished run, and the file holding it.
     *
     * [report] is carried rather than re-read: the shell process wrote it to
     * [path], which the app process cannot read back — the two run under
     * different uids. Holding the string is what makes an export possible at
     * all.
     */
    data class Complete(val report: String, val path: String) : DiagnosticsState

    /** A run that could not be completed, with what went wrong. */
    data class Failed(val problem: String) : DiagnosticsState
}
