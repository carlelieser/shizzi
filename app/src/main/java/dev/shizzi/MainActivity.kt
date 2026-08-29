package dev.shizzi

import android.Manifest
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import dev.shizzi.ui.theme.ShizziTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels()

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> viewModel.refreshShizukuState() }

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener { viewModel.refreshShizukuState() }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener { viewModel.refreshShizukuState() }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerShizukuListeners()

        setContent {
            val settings by viewModel.settings.collectAsState()
            val loaded = settings ?: return@setContent

            ShizziTheme(choice = loaded.theme) {
                val colors = ShizziTheme.colors

                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !colors.isDark
                }

                Surface(color = colors.background) {
                    val state by viewModel.state.collectAsState()
                    val permissions by viewModel.permissionState.collectAsState()
                    val diagnostics by viewModel.diagnosticsState.collectAsState()
                    val compatibility by viewModel.compatibilityState.collectAsState()

                    ShizziApp(
                        state = AppState(
                            session = state,
                            settings = loaded,
                            diagnostics = diagnostics,
                            permissions = permissions,
                        ),
                        onboarding = OnboardingEntry(
                            compatibility = compatibility,
                            onCheckCompatibility = viewModel::checkCompatibility,
                            onDownloadTetheringApex = viewModel::downloadTetheringApex,
                            onInstallTetheringApex = viewModel::installTetheringApex,
                            onRebootDevice = viewModel::rebootDevice,
                            onComplete = viewModel::completeOnboarding,
                        ),
                        actions = AppActions(
                            onToggle = viewModel::toggle,
                            onCancel = viewModel::cancel,
                            onRequestPermission = viewModel::requestPermission,
                            onSetTheme = viewModel::setTheme,
                            onSetLogging = viewModel::setLogging,
                            onRunProbes = viewModel::runProbes,
                            onDismissDiagnostics = viewModel::dismissDiagnostics,
                            onClearLog = viewModel::clearLog,
                            onRestartOnboarding = viewModel::restartOnboarding,
                            onSetExternalControl = viewModel::setExternalControl,
                            onSetExternalControlToken = viewModel::setExternalControlToken,
                            onRegenerateExternalControlToken =
                                viewModel::regenerateExternalControlToken,
                            onGrantPermission = ::grantPermission,
                        ),
                    )
                }
            }
        }

        holdFirstFrameUntilSettingsLoad()
    }

    private fun holdFirstFrameUntilSettingsLoad() {
        val content = findViewById<View>(android.R.id.content)

        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (viewModel.settings.value == null) return false

                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
    }

    private fun requestNotificationPermission() {
        if (!AppPermission.NOTIFICATIONS.isApplicable) return
        if (PermissionInspector(this).isGranted(AppPermission.NOTIFICATIONS)) return

        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun grantPermission(permission: AppPermission) {
        when (permission) {
            AppPermission.NOTIFICATIONS -> requestNotificationPermission()
            else -> viewModel.openPermissionSettings(permission)
        }
    }

    private fun registerShizukuListeners() {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshShizukuState()
        viewModel.refreshPermissions()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }
}
