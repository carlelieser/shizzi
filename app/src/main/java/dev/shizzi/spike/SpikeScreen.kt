package dev.shizzi.spike

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Teeth on the settings gear. */
private const val GEAR_TEETH = 8

/** Colour for each status, kept next to the label so they cannot disagree. */
private fun statusColor(status: UiStatus): Color = when (status) {
    UiStatus.READY -> Color(0xFF9E9E9E)
    UiStatus.LOADING -> Color(0xFFFFA726)
    UiStatus.CONNECTED -> Color(0xFF43A047)
    UiStatus.ERROR -> Color(0xFFE53935)
}

/** Why the button is unavailable, in the user's terms rather than the API's. */
private fun describeShizuku(state: ShizukuState): String = when (state) {
    is ShizukuState.NotInstalled -> "Shizuku is not installed"
    is ShizukuState.NotRunning -> "Shizuku is installed but not running"
    is ShizukuState.PermissionRequired -> "Shizuku permission required"
    is ShizukuState.UnsupportedPlatform -> "Needs Android 13 or newer (this is API ${state.sdkInt})"
    is ShizukuState.Ready -> ""
}

private fun statusLabel(status: UiStatus): String = when (status) {
    UiStatus.READY -> "Ready"
    UiStatus.LOADING -> "Loading"
    UiStatus.CONNECTED -> "Connected"
    UiStatus.ERROR -> "Error"
}

@Composable
fun SpikeScreen(
    state: SpikeUiState,
    settings: Settings,
    onToggle: () -> Unit,
    onRequestPermission: () -> Unit,
    onSetDebugLogging: (Boolean) -> Unit,
    onRunProbes: () -> Unit,
) {
    var isShowingSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isShowingSettings -> SettingsPage(
                isDebugLogging = settings.isDebugLogging,
                onSetDebugLogging = onSetDebugLogging,
                onRunProbes = onRunProbes,
                onBack = { isShowingSettings = false },
            )

            else -> MainPage(
                state = state,
                onToggle = onToggle,
                onRequestPermission = onRequestPermission,
                onOpenSettings = { isShowingSettings = true },
            )
        }
    }
}

@Composable
private fun MainPage(
    state: SpikeUiState,
    onToggle: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        SettingsButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StatusIndicator(state.status)
            Spacer(Modifier.height(48.dp))
            ToggleButton(state, onToggle)
            Spacer(Modifier.height(24.dp))
            StatusDetail(state, onRequestPermission)
        }
    }
}

/** The centered status indicator: a dot and its label. */
@Composable
private fun StatusIndicator(status: UiStatus) {
    val color by animateColorAsState(statusColor(status), label = "statusColor")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape).background(color),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = statusLabel(status),
            fontSize = 22.sp,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ToggleButton(state: SpikeUiState, onToggle: () -> Unit) {
    val isConnected = state.status == UiStatus.CONNECTED

    Button(
        onClick = onToggle,
        enabled = state.canStart || isConnected,
        modifier = Modifier.width(200.dp).height(56.dp),
        colors = when {
            isConnected -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            )

            else -> ButtonDefaults.buttonColors()
        },
    ) {
        Text(
            text = if (isConnected) "Stop" else "Start",
            fontSize = 18.sp,
        )
    }
}

/**
 * Detail under the button: the active interface, a Shizuku prompt, or the
 * verbatim error (R7.5).
 */
@Composable
private fun StatusDetail(state: SpikeUiState, onRequestPermission: () -> Unit) {
    if (state.shizukuState is ShizukuState.PermissionRequired) {
        TextButton(onClick = onRequestPermission) { Text("Grant Shizuku permission") }
        return
    }

    val message = when {
        state.lastError.isNotEmpty() -> state.lastError
        state.shizukuState !is ShizukuState.Ready -> describeShizuku(state.shizukuState)
        state.interfaceName.isNotEmpty() -> "via ${state.interfaceName}"
        else -> state.detail
    }

    if (message.isNotEmpty()) {
        Text(
            text = message,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Gear icon drawn directly rather than pulling in material-icons-extended,
 * which is a large artifact to add for a single glyph.
 */
@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    IconButton(onClick = onClick, modifier = modifier) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val toothLength = size.minDimension * 0.20f
            val ringRadius = size.minDimension * 0.28f
            val strokeWidth = size.minDimension * 0.13f

            repeat(GEAR_TEETH) { index ->
                val angle = (PI * 2 / GEAR_TEETH * index).toFloat()
                val inner = ringRadius + strokeWidth * 0.1f
                val outer = inner + toothLength
                drawLine(
                    color = tint,
                    start = center + Offset(cos(angle) * inner, sin(angle) * inner),
                    end = center + Offset(cos(angle) * outer, sin(angle) * outer),
                    strokeWidth = strokeWidth * 0.75f,
                    cap = StrokeCap.Round,
                )
            }

            drawCircle(
                color = tint,
                radius = ringRadius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

@Composable
private fun SettingsPage(
    isDebugLogging: Boolean,
    onSetDebugLogging: (Boolean) -> Unit,
    onRunProbes: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(8.dp))
            Text("Settings", fontSize = 20.sp)
        }

        Spacer(Modifier.height(24.dp))

        SettingsToggle(
            title = "Debug logging",
            subtitle = "Write session reports to /data/local/tmp for off-device inspection",
            isChecked = isDebugLogging,
            onCheckedChange = onSetDebugLogging,
        )

        Spacer(Modifier.height(8.dp))

        SettingsAction(
            title = "Run diagnostics",
            subtitle = "Runs the full probe sequence and writes a report",
            onClick = onRunProbes,
        )
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, fontSize = 16.sp)
        Text(
            subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
