package dev.shizzi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.shizzi.ui.theme.Appearance
import dev.shizzi.ui.theme.ShizziTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val viewModel: SessionViewModel by viewModels()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, granted ->
        viewModel.refreshPrivilegeState()
        viewModel.refreshPermissions()
        onShizukuResult(granted == PackageManager.PERMISSION_GRANTED)
    }
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { viewModel.refreshPrivilegeState() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { viewModel.refreshPrivilegeState() }

    private var requested: AppPermission? = null
    private var isChaining = false
    private val asked = mutableSetOf<AppPermission>()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
        onPermissionResult()
    }

    // Wireless Debugging pairing shows the six-digit code inside a notification,
    // so pairing must not start before the notification permission is granted.
    private var showPairingNotificationDialog by mutableStateOf(false)
    private val pairingNotificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
        // When the request was denied, beginLocalAdbPairing surfaces the existing
        // "notification permission required" error instead of starting silently.
        viewModel.beginLocalAdbPairing()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerShizukuListeners()

        setContent {
            val settings by viewModel.settings.collectAsState()
            val loaded = settings ?: return@setContent
            ShizziTheme(Appearance(loaded.theme, loaded.design, loaded.accent)) {
                val colors = ShizziTheme.colors
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !colors.isDark
                        isAppearanceLightNavigationBars = !colors.isDark
                    }
                }
                Surface(color = colors.background) {
                    val state by viewModel.state.collectAsState()
                    val diagnostics by viewModel.diagnosticsState.collectAsState()
                    val compatibility by viewModel.compatibilityState.collectAsState()
                    val permissions by viewModel.permissionState.collectAsState()
                    ShizziApp(
                        state = AppState(state, loaded, diagnostics, permissions),
                        onboarding = OnboardingEntry(
                            compatibility,
                            viewModel::checkCompatibility,
                            viewModel::downloadTetheringApex,
                            viewModel::installTetheringApex,
                            viewModel::rebootDevice,
                            viewModel::completeOnboarding,
                        ),
                        actions = AppActions(
                            onToggle = viewModel::toggle,
                            onCancel = viewModel::cancel,
                            onRequestPermission = viewModel::requestPermission,
                            onRequestAllPermissions = ::requestAllPermissions,
                            onGrantPermission = ::grantPermission,
                            onSetPrivilegeBackend = viewModel::setPrivilegeBackend,
                            onShizukuAction = viewModel::actOnShizuku,
                            onStartLocalAdbPairing = ::startLocalAdbPairingWithNotificationCheck,
                            onCancelLocalAdbPairing = viewModel::cancelLocalAdbPairing,
                            onSetTheme = viewModel::setTheme,
                            onSetDesign = viewModel::setDesign,
                            onSetAccent = viewModel::setAccent,
                            onSetLogging = viewModel::setLogging,
                            onSetVpnMode = viewModel::setVpnMode,
                            onRunProbes = viewModel::runProbes,
                            onCancelProbes = viewModel::cancelProbes,
                            onDismissDiagnostics = viewModel::dismissDiagnostics,
                            onClearLog = viewModel::clearLog,
                            onRestartOnboarding = viewModel::restartOnboarding,
                            onSetAutomation = viewModel::setAutomation,
                            onRegenerateAutomationToken = viewModel::regenerateAutomationToken,
                        ),
                    )

                    if (showPairingNotificationDialog) {
                        PairingNotificationDialog(
                            onConfirm = {
                                showPairingNotificationDialog = false
                                val name = AppPermission.NOTIFICATIONS.manifestName
                                if (name != null) pairingNotificationLauncher.launch(name)
                            },
                            onDismiss = { showPairingNotificationDialog = false },
                        )
                    }

                    val activityContext = LocalContext.current
                    val lastCrash = remember { CrashReport.read(activityContext) }
                    if (lastCrash != null) {
                        CrashReportDialog(
                            report = lastCrash,
                            onDismiss = { CrashReport.clear(activityContext) },
                        )
                    }
                }
            }
        }
        holdFirstFrameUntilSettingsLoad()
    }

    private fun holdFirstFrameUntilSettingsLoad() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (viewModel.settings.value == null) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    private fun requestAllPermissions() {
        isChaining = true
        requestNextOutstanding()
    }

    private fun requestNextOutstanding() {
        val backend = viewModel.settings.value?.privilegeBackend ?: PrivilegeBackendType.SHIZUKU
        val privilege = viewModel.state.value.privilegeState
        if (privilege !is PrivilegeState.Ready) {
            when (backend) {
                PrivilegeBackendType.SHIZUKU -> {
                    val shizuku = viewModel.state.value.shizukuState
                    if (shizuku !is ShizukuState.PermissionRequired) stopChain()
                    viewModel.actOnShizuku()
                }
                PrivilegeBackendType.LOCAL_ADB -> stopChain() // Pairing requires the code field in the UI.
            }
            return
        }

        val outstanding = viewModel.permissionState.value.firstOrNull { !it.isGranted }?.permission
        if (outstanding == null) { stopChain(); return }
        grantPermission(outstanding)
    }

    private fun grantPermission(permission: AppPermission) {
        val name = permission.manifestName
        if (name == null || isDialogSuppressed(permission)) {
            stopChain(); viewModel.openPermissionSettings(permission); return
        }
        requested = permission
        asked += permission
        permissionLauncher.launch(name)
    }

    private fun onPermissionResult() {
        val permission = requested ?: return
        requested = null
        if (!isChaining) return
        if (viewModel.isPermissionGranted(permission)) requestNextOutstanding() else stopChain()
    }

    private fun onShizukuResult(isGranted: Boolean) {
        if (!isChaining) return
        if (isGranted) requestNextOutstanding() else stopChain()
    }

    private fun stopChain() { isChaining = false }
    private fun isDialogSuppressed(permission: AppPermission): Boolean {
        val name = permission.manifestName ?: return false
        return permission in asked && !shouldShowRequestPermissionRationale(name)
    }

    /**
     * Entry point for the "Open Wireless Debugging" button. Pairing reports its
     * six-digit code through a notification, so the notification permission is
     * requested (behind an in-app rationale) before pairing ever starts.
     */
    private fun startLocalAdbPairingWithNotificationCheck() {
        val notifications = AppPermission.NOTIFICATIONS
        if (!notifications.isApplicable || viewModel.isPermissionGranted(notifications)) {
            viewModel.beginLocalAdbPairing()
            return
        }
        showPairingNotificationDialog = true
    }

    private fun registerShizukuListeners() {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPrivilegeState()
        viewModel.refreshPermissions()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }
}

@Composable
private fun PairingNotificationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.pairing_notification_permission_title)) },
        text = { Text(str(R.string.pairing_notification_permission_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(str(R.string.action_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(str(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.crash_report_title)) },
        text = {
            Text(
                text = report,
                style = ShizziTheme.typography.log,
                color = ShizziTheme.colors.onSurfaceMuted,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(report))
                onDismiss()
            }) { Text(str(R.string.crash_report_copy)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(str(R.string.crash_report_close)) }
        },
    )
}
