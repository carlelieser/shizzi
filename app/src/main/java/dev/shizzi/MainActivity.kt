package dev.shizzi

import android.content.pm.PackageManager
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
        Shizuku.OnRequestPermissionResultListener { _, granted ->
            viewModel.refreshShizukuState()
            viewModel.refreshPermissions()
            onShizukuResult(isGranted = granted == PackageManager.PERMISSION_GRANTED)
        }

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener { viewModel.refreshShizukuState() }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener { viewModel.refreshShizukuState() }

    private var requested: AppPermission? = null

    private var isChaining = false

    private val asked = mutableSetOf<AppPermission>()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions()
            onPermissionResult()
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
                    val diagnostics by viewModel.diagnosticsState.collectAsState()
                    val compatibility by viewModel.compatibilityState.collectAsState()
                    val permissions by viewModel.permissionState.collectAsState()

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
                            onRequestAllPermissions = ::requestAllPermissions,
                            onGrantPermission = ::grantPermission,
                            onShizukuAction = viewModel::actOnShizuku,
                            onSetTheme = viewModel::setTheme,
                            onSetLogging = viewModel::setLogging,
                            onRunProbes = viewModel::runProbes,
                            onDismissDiagnostics = viewModel::dismissDiagnostics,
                            onClearLog = viewModel::clearLog,
                            onRestartOnboarding = viewModel::restartOnboarding,
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

    private fun requestAllPermissions() {
        isChaining = true
        requestNextOutstanding()
    }

    private fun requestNextOutstanding() {
        if (requestShizuku()) return

        val outstanding = viewModel.permissionState.value.firstOrNull { !it.isGranted }
        val permission = outstanding?.permission

        if (permission == null) {
            isChaining = false
            return
        }

        grantPermission(permission)
    }

    // Only PermissionRequired reports back through the Shizuku listener; the other
    // states hand off to another app, so the chain ends there.
    private fun requestShizuku(): Boolean {
        val state = viewModel.state.value.shizukuState
        if (state is ShizukuState.Ready) return false

        if (state !is ShizukuState.PermissionRequired) stopChain()

        viewModel.actOnShizuku()
        return true
    }

    private fun grantPermission(permission: AppPermission) {
        if (isDialogSuppressed(permission)) {
            viewModel.openPermissionSettings(permission)
            return
        }

        requested = permission
        asked += permission
        permissionLauncher.launch(permission.manifestName)
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

    private fun stopChain() {
        isChaining = false
    }

    // shouldShowRequestPermissionRationale is also false before the first ask, so
    // only a permission this process has already requested can be suppressed.
    private fun isDialogSuppressed(permission: AppPermission): Boolean {
        if (permission !in asked) return false

        return !shouldShowRequestPermissionRationale(permission.manifestName)
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
