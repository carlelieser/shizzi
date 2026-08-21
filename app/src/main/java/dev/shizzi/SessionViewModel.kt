package dev.shizzi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shizzi.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders whatever the session service is doing, and asks it to start or stop.
 *
 * Holds no session: a ViewModel dies when the user swipes the app away, taking
 * the death recipient that drops an orphaned hotspot with it, so the session
 * belongs to [SessionService].
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    /** Owned here because diagnostics are a UI action, not part of a session. */
    private val diagnostics = TetherClient()

    private val settingsStore = getApplication<App>().settingsStore

    /**
     * Null rather than a default until the first read lands: rendering under
     * SYSTEM while the stored choice loads flashes the wrong theme, so the
     * activity holds the frame until this is non-null.
     */
    val settings: StateFlow<Settings?> = settingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private val localState = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = localState.asStateFlow()

    private val localDiagnostics = MutableStateFlow<DiagnosticsState>(DiagnosticsState.Idle)
    val diagnosticsState: StateFlow<DiagnosticsState> = localDiagnostics.asStateFlow()

    private val localCompatibility = MutableStateFlow<CompatibilityState>(CompatibilityState.Idle)
    val compatibilityState: StateFlow<CompatibilityState> = localCompatibility.asStateFlow()

    /** Guards against a second collector per onResume. */
    private var sessionCollector: Job? = null

    init {
        refreshShizukuState()
        observeSession()
    }

    /**
     * With no service running the local state stands alone and renders as idle.
     * Shizuku availability is tracked here either way — it gates the button
     * before any session exists.
     */
    private fun observeSession() {
        if (sessionCollector?.isActive == true) return

        sessionCollector = viewModelScope.launch {
            SessionService.liveState.collect { session ->
                localState.update { local -> session.copy(shizukuState = local.shizukuState) }
            }
        }
    }

    fun refreshShizukuState() {
        localState.update { it.copy(shizukuState = ShizukuGate.currentState()) }
    }

    fun requestPermission() {
        ShizukuGate.requestPermission()
    }

    /**
     * Both processes, because a running session writes most of its entries from
     * the shell — persisting alone leaves the toggle inert until the next start.
     */
    fun setLogging(enabled: Boolean) {
        SessionLog.setEnabled(enabled)
        diagnostics.setLogging(enabled)
        viewModelScope.launch { settingsStore.setLogging(enabled) }
    }

    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch { settingsStore.setTheme(choice) }
    }

    /** One button: starts when idle, stops when connected. */
    fun toggle() {
        val context = getApplication<Application>()

        when (localState.value.status) {
            UiStatus.CONNECTED -> SessionService.stop(context)
            else -> SessionService.start(context)
        }
    }

    /**
     * Routed to stop, not toggle: a cancel arrives while the status is LOADING,
     * which toggle would read as idle and answer with a second session. The
     * same teardown dismantles a half-built session and a whole one.
     *
     * Resets the screen here rather than awaiting the service — cancelling
     * cannot fail, so waiting would only show the app thinking about it.
     */
    fun cancel() {
        localState.update {
            it.asStopped()
        }
        SessionService.stop(getApplication())
    }

    /**
     * Direct rather than through the service: diagnostics tear down what they
     * create and hold no session for it to outlive.
     *
     * The result lands in [diagnosticsState], not session state. Folding it
     * through applyOutcome — which reads a *session status* — parsed a report
     * carrying no "state" field as a session gone READY, resetting the home
     * screen's status, interface, and counters while discarding the report.
     */
    fun runProbes() {
        if (localDiagnostics.value is DiagnosticsState.Running) return
        localDiagnostics.value = DiagnosticsState.Running

        viewModelScope.launch {
            localDiagnostics.value = runCatching { diagnostics.runProbes(true) }
                .fold(
                    onSuccess = { report ->
                        DiagnosticsState.Complete(report, TetherService.REPORT_PATH)
                    },
                    onFailure = { failure ->
                        DiagnosticsState.Failed(
                            "${failure.javaClass.simpleName}: ${failure.message}",
                        )
                    },
                )
            refreshShizukuState()
        }
    }

    /**
     * Resolves the two APIs the app rests on, through the shell.
     *
     * A thrown failure is [CompatibilityState.Failed] rather than a verdict of
     * incompatible: not reaching Shizuku says nothing about the device, and
     * telling a working handset it is unsupported is the worse error.
     */
    fun checkCompatibility() {
        if (localCompatibility.value is CompatibilityState.Checking) return
        localCompatibility.value = CompatibilityState.Checking

        viewModelScope.launch {
            localCompatibility.value = runCatching { diagnostics.checkCompatibility() }
                .fold(
                    onSuccess = { results -> CompatibilityState.Complete(results) },
                    onFailure = { failure ->
                        CompatibilityState.Failed(
                            "${failure.javaClass.simpleName}: ${failure.message}",
                        )
                    },
                )
        }
    }

    /** Persisted, so the wizard is a first run rather than a launch screen. */
    fun completeOnboarding() {
        viewModelScope.launch { settingsStore.setOnboardingComplete(true) }
    }

    /**
     * Sends the app back to the wizard.
     *
     * The compatibility result is dropped with it: the step re-runs its check
     * on arrival, and keeping the old verdict would show a stale COMPATIBLE
     * badge for the moment before the new run reports.
     */
    fun restartOnboarding() {
        localCompatibility.value = CompatibilityState.Idle
        viewModelScope.launch { settingsStore.setOnboardingComplete(false) }
    }

    /** Drops a finished run's result, so its toast leaves the screen. */
    fun dismissDiagnostics() {
        if (localDiagnostics.value is DiagnosticsState.Running) return
        localDiagnostics.value = DiagnosticsState.Idle
    }

    /**
     * Empties both halves, since neither process can write the other's. The
     * shell half is attempted even unbound, at the cost of a bind: that file
     * holds most of the history, and skipping it shows a "cleared" log with
     * entries still in it.
     *
     * @param onCleared runs on the main thread with null on success, else why
     *   the shell's half survived. The screen needs it to reload — the list is
     *   read once per visit.
     */
    fun clearLog(onCleared: (String?) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SessionLog.clear() }
            onCleared(diagnostics.clearLog())
        }
    }

    override fun onCleared() {
        // Only the diagnostics binding: unbinding the session's would drop the
        // death recipient that the service exists to keep registered.
        diagnostics.unbind()
        super.onCleared()
    }
}
