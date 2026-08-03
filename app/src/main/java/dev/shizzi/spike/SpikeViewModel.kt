package dev.shizzi.spike

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single source of UI truth. Operations serialize through [isBusy] so the
 * controls cannot be re-entered while a privileged call is in flight (R7.6).
 */
class SpikeViewModel : ViewModel() {

    private val controller = ProbeController()
    private val internalState = MutableStateFlow(SpikeUiState())
    val state: StateFlow<SpikeUiState> = internalState.asStateFlow()

    init {
        refreshShizukuState()
    }

    fun refreshShizukuState() {
        internalState.update { it.copy(shizukuState = ShizukuGate.currentState()) }
    }

    fun requestPermission() {
        ShizukuGate.requestPermission()
    }

    fun runProbes(attemptTethering: Boolean) {
        execute { controller.runProbes(attemptTethering) }
    }

    fun teardown() {
        execute { controller.teardown() }
    }

    /**
     * Runs one privileged operation, rejecting re-entry while busy.
     *
     * Failures land in [SpikeUiState.lastError] verbatim (R7.5) rather than
     * being reduced to a generic message.
     */
    private fun execute(operation: suspend () -> String) {
        if (internalState.value.isBusy) return
        internalState.update { it.copy(isBusy = true, lastError = "") }

        viewModelScope.launch {
            val outcome = runCatching { operation() }
            internalState.update { current ->
                current.copy(
                    isBusy = false,
                    report = outcome.getOrDefault(current.report),
                    lastError = outcome.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" }.orEmpty(),
                )
            }
            refreshShizukuState()
        }
    }

    override fun onCleared() {
        controller.unbind()
        super.onCleared()
    }
}
