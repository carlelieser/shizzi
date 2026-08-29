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

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val diagnostics = TetherClient()

    private val settingsStore = getApplication<App>().settingsStore

    val settings: StateFlow<Settings?> = settingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private val localState = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = localState.asStateFlow()

    private val localDiagnostics = MutableStateFlow<DiagnosticsState>(DiagnosticsState.Idle)
    val diagnosticsState: StateFlow<DiagnosticsState> = localDiagnostics.asStateFlow()

    private val compatibility =
        CompatibilityController(getApplication(), diagnostics, viewModelScope)

    val compatibilityState: StateFlow<CompatibilityState> = compatibility.state

    private var sessionCollector: Job? = null

    init {
        refreshShizukuState()
        observeSession()
    }

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

    fun setLogging(enabled: Boolean) {
        SessionLog.setEnabled(enabled)
        diagnostics.setLogging(enabled)
        viewModelScope.launch { settingsStore.setLogging(enabled) }
    }

    fun setExternalControl(isEnabled: Boolean) {
        viewModelScope.launch { settingsStore.setExternalControlEnabled(isEnabled) }
    }

    fun setExternalControlToken(token: String) {
        viewModelScope.launch { settingsStore.setExternalControlToken(token) }
    }

    fun regenerateExternalControlToken() {
        setExternalControlToken(ExternalControlToken.generate())
    }

    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch { settingsStore.setTheme(choice) }
    }

    fun toggle() {
        val context = getApplication<Application>()

        when (localState.value.status) {
            UiStatus.CONNECTED -> SessionService.stop(context)
            else -> SessionService.start(context)
        }
    }

    fun cancel() {
        localState.update {
            it.asStopped()
        }
        SessionService.stop(getApplication())
    }

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

                        SessionLog.error(
                            "diagnostics failed in the app process: " +
                                "${failure.javaClass.name}: ${failure.message}",
                        )
                        DiagnosticsState.Failed(
                            "${failure.javaClass.simpleName}: ${failure.message}",
                        )
                    },
                )
            refreshShizukuState()
        }
    }

    fun checkCompatibility() = compatibility.check()

    fun downloadTetheringApex() = compatibility.downloadApex()

    fun installTetheringApex() = compatibility.installApex()

    fun rebootDevice() = compatibility.rebootDevice()

    fun completeOnboarding() {
        viewModelScope.launch { settingsStore.setOnboardingComplete(true) }
    }

    fun restartOnboarding() {
        compatibility.reset()
        viewModelScope.launch { settingsStore.setOnboardingComplete(false) }
    }

    fun dismissDiagnostics() {
        if (localDiagnostics.value is DiagnosticsState.Running) return
        localDiagnostics.value = DiagnosticsState.Idle
    }

    fun clearLog(onCleared: (String?) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SessionLog.clear() }
            onCleared(diagnostics.clearLog())
        }
    }

    override fun onCleared() {

        diagnostics.unbind()
        super.onCleared()
    }
}
