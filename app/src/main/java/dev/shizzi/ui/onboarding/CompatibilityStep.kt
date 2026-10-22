package dev.shizzi.ui.onboarding

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.Capability
import dev.shizzi.CapabilityResult
import dev.shizzi.CompatibilityState
import dev.shizzi.R
import dev.shizzi.isOnFixPath
import dev.shizzi.reportedResults
import dev.shizzi.str
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.themedSurface
import kotlinx.coroutines.delay

@Composable
fun CompatibilityStep(state: CompatibilityState) {
    val isOverflowing = state is CompatibilityState.Failed
    var slowCheck by remember { mutableStateOf(false) }
    var showTroubleshooting by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        slowCheck = false
        if (state is CompatibilityState.Checking) {
            delay(TROUBLESHOOTING_DELAY_MS)
            slowCheck = true
        }
    }

    // The wizard's step content is already a vertically scrollable column;
    // adding another scrollable here would nest them and crash measurement
    // with infinite height constraints as soon as the verdict overflows.
    Column(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.lg)) {
            Capability.entries.forEach { capability ->
                CapabilityCard(
                    capability = capability,
                    status = statusFor(state, capability),
                    detail = detailFor(state, capability),
                )
            }

            if (slowCheck || state is CompatibilityState.Failed) {
                OutlinedButton(
                    onClick = { showTroubleshooting = !showTroubleshooting },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (showTroubleshooting) {
                            str(R.string.hide_troubleshooting)
                        } else {
                            str(R.string.show_troubleshooting)
                        },
                    )
                }
            }

            if (showTroubleshooting) {
                TroubleshootingPanel(state)
            }
        }

        if (state is CompatibilityState.Failed) {
            CheckFailure(state.problem)
        }

        VerdictBand(state = state, isOverflowing = isOverflowing)
    }
}

@Composable
private fun TroubleshootingPanel(state: CompatibilityState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .themedSurface(fill = ShizziTheme.colors.surfaceContainer)
            .padding(ShizziTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        Text(str(R.string.troubleshooting), style = ShizziTheme.typography.subheading)
        Text(str(R.string.compatibility_troubleshooting_intro), style = ShizziTheme.typography.body, color = ShizziTheme.colors.onSurfaceMuted)
        Text(str(R.string.compatibility_troubleshooting_privilege), style = ShizziTheme.typography.body, color = ShizziTheme.colors.onSurfaceMuted)
        Text(str(R.string.compatibility_troubleshooting_shizuku), style = ShizziTheme.typography.body, color = ShizziTheme.colors.onSurfaceMuted)
        Text(str(R.string.compatibility_troubleshooting_wireless), style = ShizziTheme.typography.body, color = ShizziTheme.colors.onSurfaceMuted)
        Text(str(R.string.compatibility_troubleshooting_android16), style = ShizziTheme.typography.body, color = ShizziTheme.colors.onSurfaceMuted)
        Text(
            str(
                R.string.compatibility_device_info,
                Build.MANUFACTURER,
                Build.MODEL,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
            ),
            style = ShizziTheme.typography.log,
            color = ShizziTheme.colors.onSurfaceMuted,
        )
        if (state is CompatibilityState.Failed) {
            Text(state.problem, style = ShizziTheme.typography.log, color = ShizziTheme.colors.onSurfaceMuted)
        }
    }
}

@Composable
private fun ColumnScope.VerdictBand(state: CompatibilityState, isOverflowing: Boolean) {
    val sizing = if (isOverflowing) Modifier.padding(top = ShizziTheme.spacing.xxxl) else Modifier.weight(1f)
    val placement = if (state.isOnFixPath) Alignment.BottomCenter else Alignment.Center

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(sizing)
            .padding(top = ShizziTheme.spacing.lg),
        contentAlignment = placement,
    ) {
        if (state.isOnFixPath) FixPathCard(state) else CompatibilityVerdict(state)
    }
}

@Composable
private fun FixPathCard(state: CompatibilityState) {
    when (state) {
        is CompatibilityState.Fixable,
        is CompatibilityState.Downloading,
        is CompatibilityState.DownloadFailed,
        -> TetheringProviderDownloadCard(state = state, hasNetwork = hasValidatedNetwork())
        else -> TetheringProviderInstallCard(state)
    }
}

@Composable
private fun CheckFailure(problem: String) {
    Text(
        text = problem,
        style = ShizziTheme.typography.log,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = ShizziTheme.spacing.lg),
    )
}

private fun statusFor(state: CompatibilityState, capability: Capability): CapabilityStatus =
    when {
        state.resultFor(capability)?.isPresent == true -> CapabilityStatus.SUCCESS
        state.resultFor(capability) != null -> CapabilityStatus.FAILURE
        state is CompatibilityState.Failed -> CapabilityStatus.FAILURE
        else -> CapabilityStatus.LOADING
    }

private fun detailFor(state: CompatibilityState, capability: Capability): String =
    state.resultFor(capability)?.detail.orEmpty()

private fun CompatibilityState.resultFor(capability: Capability): CapabilityResult? =
    reportedResults.firstOrNull { it.capability == capability }

private const val TROUBLESHOOTING_DELAY_MS = 3_000L
