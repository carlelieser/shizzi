package dev.shizzi.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerShizukuListeners()

        setContent {
            val isDark = isSystemInDarkTheme()

            // The activity draws edge-to-edge, so the system bar icons are the
            // app's responsibility: without this they stay light-on-light in
            // the light theme and are invisible.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !isDark
            }

            MaterialTheme(
                colorScheme = if (isDark) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    val state by viewModel.state.collectAsState()
                    SpikeScreen(
                        state = state,
                        onToggle = viewModel::toggle,
                        onRequestPermission = viewModel::requestPermission,
                        onSetDebugLogging = viewModel::setDebugLogging,
                        onRunProbes = viewModel::runProbes,
                    )
                }
            }
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
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }
}
