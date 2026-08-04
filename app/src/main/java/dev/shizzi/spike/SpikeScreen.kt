package dev.shizzi.spike

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** One screen, no navigation (R7). */
@Composable
fun SpikeScreen(
    state: SpikeUiState,
    onRequestPermission: () -> Unit,
    onRunProbes: (Boolean) -> Unit,
    onTeardown: () -> Unit,
) {
    var shouldAttemptTethering by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Shizuku Tether — Viability Spike", style = MaterialTheme.typography.headlineSmall)

        ShizukuStatusCard(state.shizukuState, state.isBusy, onRequestPermission)

        TetheringOptionRow(
            isChecked = shouldAttemptTethering,
            isEnabled = !state.isBusy,
            onCheckedChange = { shouldAttemptTethering = it },
        )

        ProbeActionRow(
            isEnabled = state.shizukuState is ShizukuState.Ready && !state.isBusy,
            onRunProbes = { onRunProbes(shouldAttemptTethering) },
            onTeardown = onTeardown,
        )

        if (state.lastError.isNotBlank()) ErrorCard(state.lastError)
        if (state.report.isNotBlank()) ReportCard(state.report)
    }
}

@Composable
private fun ShizukuStatusCard(
    shizukuState: ShizukuState,
    isBusy: Boolean,
    onRequestPermission: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Shizuku", style = MaterialTheme.typography.titleMedium)
            Text(describeState(shizukuState), style = MaterialTheme.typography.bodyMedium)

            if (shizukuState is ShizukuState.PermissionRequired) {
                Button(onClick = onRequestPermission, enabled = !isBusy) {
                    Text("Grant permission")
                }
            }
        }
    }
}

/** R1.2 / P-1..P-5: every state names its own remedy rather than failing blank. */
private fun describeState(shizukuState: ShizukuState): String = when (shizukuState) {
    is ShizukuState.NotInstalled ->
        "Not installed. Install Shizuku v11+ from GitHub or the Play Store, then start it."

    is ShizukuState.NotRunning ->
        "Installed but not running. Start Shizuku (wireless debugging or ADB), then reopen."

    is ShizukuState.PermissionRequired ->
        "Running, permission not granted."

    is ShizukuState.Ready ->
        "Ready — ${ShizukuGate.describeUid(shizukuState.uid)}"

    is ShizukuState.UnsupportedPlatform ->
        "Unsupported: API ${shizukuState.sdkInt}. setPreferTestNetworks needs API " +
            "${ShizukuGate.FEATURE_MIN_API}+. Probes are disabled (P-4)."
}

@Composable
private fun TetheringOptionRow(
    isChecked: Boolean,
    isEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = isChecked, onCheckedChange = onCheckedChange, enabled = isEnabled)
        Text(
            "Read tethering upstream (enable the hotspot first)",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProbeActionRow(
    isEnabled: Boolean,
    onRunProbes: () -> Unit,
    onTeardown: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRunProbes, enabled = isEnabled) { Text("Run probes") }
        OutlinedButton(onClick = onTeardown, enabled = isEnabled) { Text("Teardown") }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * Renders the report as monospaced, horizontally scrollable text: dumpsys
 * excerpts are wide and wrapping them makes them unreadable.
 */
@Composable
private fun ReportCard(report: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Report", style = MaterialTheme.typography.titleMedium)
            Text(
                text = report,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}
