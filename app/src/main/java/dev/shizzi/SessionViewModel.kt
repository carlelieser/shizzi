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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Renders whatever the session service is doing, and asks it to start or stop.
 *
 * The session deliberately does not live here. An Activity's ViewModel dies
 * when the user swipes the app away, and with it the death recipient that
 * drops an orphaned hotspot — so the session belongs to [SessionService],
 * which outlives the UI. This holds only what the screen needs.
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    /** Owned here because diagnostics are a UI action, not part of a session. */
    private val diagnostics = TetherClient()

    private val settingsStore = getApplication<App>().settingsStore

    /**
     * Persisted settings, null until the first read completes.
     *
     * Null rather than a default: the theme has to be known before the first
     * frame, and rendering under SYSTEM while the stored choice loads flashes
     * the wrong theme. The activity holds the frame until this is non-null.
     */
    val settings: StateFlow<Settings?> = settingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private val localState = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = localState.asStateFlow()

    /**
     * The collector mirroring the service's state, if one is running.
     *
     * refreshShizukuState runs on every onResume, so without this a second
     * collector would be launched each time the user returns to the screen.
     */
    private var sessionCollector: Job? = null

    init {
        refreshShizukuState()
        observeSession()
    }

    /**
     * Mirrors the service's state into the screen while a session is running.
     *
     * The service publishes its own StateFlow; when none is running the local
     * state stands alone, which renders as idle. Shizuku availability is
     * tracked here either way, since it gates the button before any session
     * exists.
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
     * Applies the logging setting to both processes that write entries.
     *
     * The app process is set directly and the shell process through the
     * binder: a running session writes most of its entries from the shell, so
     * persisting alone would leave the toggle without effect until the next
     * start.
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
     * Abandons a start that is still in progress.
     *
     * Routed to stop rather than to toggle: toggle reads the status, and a
     * cancel arrives while that status is LOADING, which would fall through to
     * its start branch and ask for a second session. Teardown is the same path
     * a finished session takes, so a half-built one is dismantled by the code
     * that knows how to dismantle a whole one.
     *
     * The screen resets here rather than waiting for the service to publish it.
     * Cancelling is not a request that can fail, so the only thing waiting
     * would communicate is that the app is still thinking about it. The
     * teardown drains behind the reset.
     */
    fun cancel() {
        localState.update {
            it.asStopped()
        }
        SessionService.stop(getApplication())
    }

    /**
     * Runs the probe sequence directly rather than through the service.
     *
     * Diagnostics tear down everything they create and hold no session, so
     * there is nothing for the service to outlive.
     */
    fun runProbes() {
        if (localState.value.isBusy) return
        localState.update { it.copy(isBusy = true, status = UiStatus.LOADING, lastError = "") }

        viewModelScope.launch {
            val outcome = runCatching { diagnostics.runProbes(true) }
            localState.update { current -> current.applyOutcome(outcome) }
            refreshShizukuState()
        }
    }

    override fun onCleared() {
        // Only the diagnostics binding: unbinding the session's would drop the
        // death recipient that the service exists to keep registered.
        diagnostics.unbind()
        super.onCleared()
    }
}
