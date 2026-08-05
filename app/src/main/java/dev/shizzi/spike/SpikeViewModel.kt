package dev.shizzi.spike

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Single source of UI truth. Operations serialize through [SpikeUiState.isBusy]
 * so the button cannot be re-entered while a privileged call is in flight (R7.6).
 */
class SpikeViewModel : ViewModel() {

    private val controller = ProbeController()
    private val internalState = MutableStateFlow(SpikeUiState())
    val state: StateFlow<SpikeUiState> = internalState.asStateFlow()

    init {
        refreshShizukuState()
        controller.onSessionLost = ::reportSessionLost
    }

    /**
     * Reports a session that ended without the user stopping it.
     *
     * Arrives on a binder thread, so the state update has to be thread-safe;
     * MutableStateFlow.update is. Showing CONNECTED after the shell process is
     * gone is worse than showing an error: the user has no way to tell that
     * tethered clients have silently fallen back to the phone's own upstream.
     */
    private fun reportSessionLost() {
        internalState.update { current ->
            current.copy(
                isBusy = false,
                status = UiStatus.ERROR,
                detail = "",
                interfaceName = "",
                lastError = "Session ended: the Shizuku service stopped. " +
                    "Check Shizuku is running, then press Start.",
            )
        }
    }

    fun refreshShizukuState() {
        internalState.update { it.copy(shizukuState = ShizukuGate.currentState()) }
    }

    fun requestPermission() {
        ShizukuGate.requestPermission()
    }

    fun setDebugLogging(enabled: Boolean) {
        internalState.update { it.copy(isDebugLogging = enabled) }
    }

    /** One button: starts when idle, stops when connected. */
    fun toggle() {
        when (internalState.value.status) {
            UiStatus.CONNECTED -> execute { controller.stop() }
            else -> execute { controller.start(internalState.value.isDebugLogging) }
        }
    }

    fun runProbes() {
        execute { controller.runProbes(true) }
    }

    /**
     * Runs one privileged operation, rejecting re-entry while busy.
     *
     * Failures land in [SpikeUiState.lastError] verbatim (R7.5) rather than
     * being reduced to a generic message.
     */
    private fun execute(operation: suspend () -> String) {
        if (internalState.value.isBusy) return
        internalState.update {
            it.copy(isBusy = true, status = UiStatus.LOADING, lastError = "")
        }

        viewModelScope.launch {
            val outcome = runCatching { operation() }
            internalState.update { current -> current.applyOutcome(outcome) }
            refreshShizukuState()
        }
    }

    /**
     * Folds a privileged call's result into the rendered state.
     *
     * A thrown exception and a status reporting ERROR are different failures —
     * a dead binder versus the session refusing to come up — so both are
     * surfaced rather than collapsed into one message.
     */
    private fun SpikeUiState.applyOutcome(outcome: Result<String>): SpikeUiState {
        val failure = outcome.exceptionOrNull()
        if (failure != null) {
            return copy(
                isBusy = false,
                status = UiStatus.ERROR,
                lastError = "${failure.javaClass.simpleName}: ${failure.message}",
            )
        }

        val parsed = runCatching { JSONObject(outcome.getOrDefault("{}")) }.getOrNull()
        val sessionState = parsed?.optString("state").orEmpty()
        val sessionDetail = parsed?.optString("detail").orEmpty()

        return copy(
            isBusy = false,
            status = statusFor(sessionState),
            detail = sessionDetail,
            interfaceName = parsed?.optString("interface").orEmpty().takeIf { it != "null" }.orEmpty(),
            lastError = if (sessionState == "ERROR") sessionDetail else "",
        )
    }

    private fun statusFor(sessionState: String): UiStatus = when (sessionState) {
        "ACTIVE" -> UiStatus.CONNECTED
        "ERROR" -> UiStatus.ERROR
        "STARTING" -> UiStatus.LOADING
        else -> UiStatus.READY
    }

    override fun onCleared() {
        controller.unbind()
        super.onCleared()
    }
}
