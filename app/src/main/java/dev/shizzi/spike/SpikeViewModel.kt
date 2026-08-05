package dev.shizzi.spike

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class SpikeViewModel(application: Application) : AndroidViewModel(application) {

    /** Owned here because diagnostics are a UI action, not part of a session. */
    private val diagnostics = ProbeController()

    private val localState = MutableStateFlow(SpikeUiState())
    val state: StateFlow<SpikeUiState> = localState.asStateFlow()

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
                localState.update { local ->
                    session.copy(
                        shizukuState = local.shizukuState,
                        isDebugLogging = local.isDebugLogging,
                    )
                }
            }
        }
    }

    fun refreshShizukuState() {
        localState.update { it.copy(shizukuState = ShizukuGate.currentState()) }
    }

    fun requestPermission() {
        ShizukuGate.requestPermission()
    }

    fun setDebugLogging(enabled: Boolean) {
        localState.update { it.copy(isDebugLogging = enabled) }
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
