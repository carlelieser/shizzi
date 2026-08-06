package dev.shizzi.spike

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import dev.shizzi.spike.ui.theme.ShizziTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: SpikeViewModel by viewModels()

    /** R1.3: permission arrives via this listener, never auto-requested at launch. */
    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> viewModel.refreshShizukuState() }

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener { viewModel.refreshShizukuState() }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener { viewModel.refreshShizukuState() }

    /**
     * Asked for once at launch, and never blocking.
     *
     * The service runs either way — the notification is how Android lets the
     * process stay alive, not something the session depends on. A denied
     * permission costs the user visibility, not function.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerShizukuListeners()
        requestNotificationPermission()

        setContent {
            val settings by viewModel.settings.collectAsState()
            val loaded = settings ?: return@setContent

            ShizziTheme(choice = loaded.theme) {
                val colors = ShizziTheme.colors

                // The activity draws edge-to-edge, so the system bar icons are
                // the app's responsibility: without this they stay
                // light-on-light in the light theme and are invisible. Keyed
                // on the resolved theme rather than the system one, since the
                // setting can override it.
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !colors.isDark
                }

                Surface(color = colors.background) {
                    val state by viewModel.state.collectAsState()
                    SpikeScreen(
                        state = state,
                        settings = loaded,
                        actions = AppActions(
                            onToggle = viewModel::toggle,
                            onCancel = viewModel::cancel,
                            onRequestPermission = viewModel::requestPermission,
                            onSetTheme = viewModel::setTheme,
                            onSetDebugLogging = viewModel::setDebugLogging,
                            onRunProbes = viewModel::runProbes,
                        ),
                    )
                }
            }
        }

        // After setContent: the listener attaches to the view setContent
        // installs, which does not exist before it runs.
        holdFirstFrameUntilSettingsLoad()
    }

    /**
     * Suspends drawing until the stored theme is known.
     *
     * Without this the window paints under the default theme for a frame or
     * two while DataStore reads, so a user who chose Light on a dark-mode
     * phone sees a dark flash on every launch. The composition returns early
     * until settings arrive; this keeps the window from drawing that empty
     * composition.
     */
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun registerShizukuListeners() {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshShizukuState()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }
}
