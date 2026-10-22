package dev.shizzi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shizzi.ui.theme.AccentChoice
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ThemeChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    private var diagnosticsClient: PrivilegedClient? = null
    private var diagnosticsJob: Job? = null
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

    private val compatibility = CompatibilityController(
        getApplication(),
        client = ::currentClient,
        scope = viewModelScope,
    )
    val compatibilityState: StateFlow<CompatibilityState> = compatibility.state

    private val permissions = PermissionInspector(application)
    private val permissionRequest = PermissionRequest(application)
    private val localPermissions = MutableStateFlow(emptyList<PermissionStatus>())
    val permissionState: StateFlow<List<PermissionStatus>> = localPermissions.asStateFlow()
    private var sessionCollector: Job? = null

    init {
        refreshPrivilegeState()
        refreshPermissions()
        observeSession()
        observePrivilegeBackend()
        observeLocalAdbPairing()
    }

    private fun observePrivilegeBackend() {
        viewModelScope.launch {
            settings
                .filterNotNull()
                .map { it.privilegeBackend }
                .distinctUntilChanged()
                .collect { refreshPrivilegeState() }
        }
    }

    private fun observeSession() {
        if (sessionCollector?.isActive == true) return
        sessionCollector = viewModelScope.launch {
            SessionService.liveState.collect { session ->
                localState.update { local ->
                    session.copy(
                        privilegeState = local.privilegeState,
                        shizukuState = local.shizukuState,
                    )
                }
            }
        }
    }

    fun refreshPrivilegeState() {
        val backend = settings.value?.privilegeBackend ?: PrivilegeBackendType.SHIZUKU
        when (backend) {
            PrivilegeBackendType.SHIZUKU -> {
                val shizuku = ShizukuGate.currentState()
                localState.update { it.copy(shizukuState = shizuku, privilegeState = PrivilegeGate.shizukuState()) }
            }
            PrivilegeBackendType.LOCAL_ADB -> refreshLocalAdbState()
        }
    }

    private fun observeLocalAdbPairing() {
        viewModelScope.launch {
            LocalAdbManager.pairingState.collect { pairing ->
                if (settings.value?.privilegeBackend != PrivilegeBackendType.LOCAL_ADB) return@collect
                val context = getApplication<Application>()
                val privilege = when (pairing) {
                    LocalAdbPairingState.Idle,
                    LocalAdbPairingState.Canceled,
                    -> if (LocalAdbManager.wasPaired(context)) {
                        localState.value.privilegeState
                    } else {
                        PrivilegeState.SetupRequired(PrivilegeBackendType.LOCAL_ADB)
                    }

                    LocalAdbPairingState.Searching -> PrivilegeState.Connecting(
                        PrivilegeBackendType.LOCAL_ADB,
                        context.getString(R.string.local_adb_searching_state),
                    )
                    LocalAdbPairingState.ServiceFound -> PrivilegeState.Connecting(
                        PrivilegeBackendType.LOCAL_ADB,
                        context.getString(R.string.local_adb_service_found_state),
                    )
                    LocalAdbPairingState.Pairing -> PrivilegeState.Connecting(
                        PrivilegeBackendType.LOCAL_ADB,
                        context.getString(R.string.local_adb_pairing_state),
                    )
                    LocalAdbPairingState.Ready -> PrivilegeState.Ready(PrivilegeBackendType.LOCAL_ADB)
                    is LocalAdbPairingState.Error -> PrivilegeState.Error(
                        PrivilegeBackendType.LOCAL_ADB,
                        pairing.message,
                    )
                }
                localState.update { it.copy(privilegeState = privilege) }
            }
        }
    }

    private fun refreshLocalAdbState() {
        val context = getApplication<Application>()
        if (!LocalAdbManager.isSupported()) {
            localState.update { it.copy(privilegeState = PrivilegeState.Unsupported(PrivilegeBackendType.LOCAL_ADB)) }
            return
        }
        if (!LocalAdbManager.wasPaired(context)) {
            when (LocalAdbManager.pairingState.value) {
                LocalAdbPairingState.Searching,
                LocalAdbPairingState.ServiceFound,
                LocalAdbPairingState.Pairing,
                -> return
                else -> Unit
            }
            localState.update { it.copy(privilegeState = PrivilegeState.SetupRequired(PrivilegeBackendType.LOCAL_ADB)) }
            return
        }

        localState.update {
            it.copy(
                privilegeState = PrivilegeState.Connecting(
                    PrivilegeBackendType.LOCAL_ADB,
                    context.getString(R.string.local_adb_waiting_for_daemon),
                ),
            )
        }
        viewModelScope.launch {
            val state = LocalAdbManager.connect(context).fold(
                onSuccess = { PrivilegeState.Ready(PrivilegeBackendType.LOCAL_ADB) },
                onFailure = {
                    PrivilegeState.Error(
                        PrivilegeBackendType.LOCAL_ADB,
                        it.message ?: context.getString(R.string.local_adb_connect_failed),
                    )
                },
            )
            localState.update { it.copy(privilegeState = state) }
        }
    }

    fun beginLocalAdbPairing() {
        val context = getApplication<Application>()
        if (!LocalAdbManager.startPairing(context)) {
            refreshLocalAdbState()
            // refreshLocalAdbState falls back to the generic setup state; surface
            // the concrete pairing failure (e.g. missing notification permission)
            // so the user sees why nothing started instead of a silent no-op.
            (LocalAdbManager.pairingState.value as? LocalAdbPairingState.Error)?.let { error ->
                localState.update {
                    it.copy(privilegeState = PrivilegeState.Error(PrivilegeBackendType.LOCAL_ADB, error.message))
                }
            }
            return
        }
        localState.update {
            it.copy(
                privilegeState = PrivilegeState.Connecting(
                    PrivilegeBackendType.LOCAL_ADB,
                    context.getString(R.string.local_adb_searching_state),
                ),
            )
        }
        LocalAdbManager.openWirelessDebuggingSettings(context)
    }

    fun cancelLocalAdbPairing() {
        LocalAdbManager.cancelPairing(getApplication())
        localState.update {
            it.copy(privilegeState = PrivilegeState.SetupRequired(PrivilegeBackendType.LOCAL_ADB))
        }
    }

    fun setPrivilegeBackend(backend: PrivilegeBackendType) {
        diagnosticsClient?.unbindAndStopDaemon()
        diagnosticsClient = null
        if (backend != PrivilegeBackendType.LOCAL_ADB) {
            LocalAdbManager.cancelPairing(getApplication())
        }
        viewModelScope.launch {
            settingsStore.setPrivilegeBackend(backend)
            localState.update { it.copy(privilegeState = PrivilegeState.SetupRequired(backend)) }
            refreshPrivilegeState()
        }
    }

    fun requestPermission() = ShizukuGate.requestPermission()
    fun actOnShizuku() = ShizukuGate.remedy(localState.value.shizukuState)
    fun refreshPermissions() { localPermissions.value = permissions.observe() }
    fun isPermissionGranted(permission: AppPermission): Boolean = permissions.isGranted(permission)
    fun openPermissionSettings(permission: AppPermission) = permissionRequest.open(permission)
    fun setAutomation(isEnabled: Boolean) { viewModelScope.launch { settingsStore.setAutomationEnabled(isEnabled) } }
    fun regenerateAutomationToken() { viewModelScope.launch { settingsStore.setAutomationToken(AutomationToken.generate()) } }

    fun setLogging(enabled: Boolean) {
        SessionLog.setEnabled(enabled)
        diagnosticsClient?.setLogging(enabled)
        viewModelScope.launch { settingsStore.setLogging(enabled) }
    }
    fun setVpnMode(mode: VpnMode) { viewModelScope.launch { settingsStore.setVpnMode(mode) } }
    fun setTheme(choice: ThemeChoice) { viewModelScope.launch { settingsStore.setTheme(choice) } }
    fun setDesign(design: DesignLanguage) { viewModelScope.launch { settingsStore.setDesign(design) } }
    fun setAccent(accent: AccentChoice) { viewModelScope.launch { settingsStore.setAccent(accent) } }
    fun addCustomAccent(argb: Int) { viewModelScope.launch { settingsStore.addCustomAccent(argb) } }

    fun toggle() {
        val context = getApplication<Application>()
        if (state.value.privilegeState !is PrivilegeState.Ready) {
            refreshPrivilegeState()
            return
        }
        if (SessionService.isSessionUp) SessionService.stop(context) else SessionService.start(context)
    }

    fun cancel() {
        localState.update { it.asStopped() }
        SessionService.stop(getApplication())
    }

    fun runProbes() {
        if (diagnosticsJob?.isActive == true) return
        localDiagnostics.value = DiagnosticsState.Running
        diagnosticsJob = viewModelScope.launch {
            localDiagnostics.value = runCatching { currentClient().runProbes(true) }.fold(
                onSuccess = { DiagnosticsState.Complete(it, TetherService.REPORT_PATH) },
                onFailure = { failure ->
                    SessionLog.error("diagnostics failed in the app process: ${failure.javaClass.name}: ${failure.message}")
                    DiagnosticsState.Failed("${failure.javaClass.simpleName}: ${failure.message}")
                },
            )
            refreshPrivilegeState()
            diagnosticsJob = null
        }
    }

    fun cancelProbes() {
        diagnosticsJob?.cancel()
        diagnosticsJob = null
        diagnosticsClient?.unbindAndStopDaemon()
        diagnosticsClient = null
        localDiagnostics.value = DiagnosticsState.Idle
    }

    fun checkCompatibility() = compatibility.check()
    fun downloadTetheringApex() = compatibility.downloadApex()
    fun installTetheringApex() = compatibility.installApex()
    fun rebootDevice() = compatibility.rebootDevice()
    fun completeOnboarding() { viewModelScope.launch { settingsStore.setOnboardingComplete(true) } }
    fun restartOnboarding() { compatibility.reset(); viewModelScope.launch { settingsStore.setOnboardingComplete(false) } }
    fun dismissDiagnostics() { if (localDiagnostics.value !is DiagnosticsState.Running) localDiagnostics.value = DiagnosticsState.Idle }

    fun clearLog(onCleared: (String?) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SessionLog.clear() }
            onCleared(currentClient().clearLog())
        }
    }

    private fun currentClient(): PrivilegedClient {
        val backend = settings.value?.privilegeBackend ?: PrivilegeBackendType.SHIZUKU
        val current = diagnosticsClient
        if (current != null && current.backend == backend) return current
        current?.unbindAndStopDaemon()
        return PrivilegeClients.create(getApplication(), backend).also { diagnosticsClient = it }
    }

    override fun onCleared() {
        diagnosticsJob?.cancel()
        diagnosticsClient?.unbind()
        super.onCleared()
    }
}
