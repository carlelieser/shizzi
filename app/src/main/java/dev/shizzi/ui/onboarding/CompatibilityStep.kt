package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.Capability
import dev.shizzi.CapabilityResult
import dev.shizzi.CompatibilityState
import dev.shizzi.ui.theme.ShizziTheme

@Composable
fun CompatibilityStep(state: CompatibilityState) {

    val isOverflowing = state is CompatibilityState.Failed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isOverflowing) Modifier.verticalScroll(rememberScrollState()) else Modifier,
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.lg)) {
            Capability.entries.forEach { capability ->
                CapabilityCard(
                    capability = capability,
                    status = statusFor(state, capability),
                    detail = detailFor(state, capability),
                )
            }
        }

        if (state is CompatibilityState.Failed) {
            CheckFailure(state.problem)
        }

        VerdictBand(state = state, isOverflowing = isOverflowing)
    }
}

@Composable
private fun ColumnScope.VerdictBand(state: CompatibilityState, isOverflowing: Boolean) {
    val sizing = when {
        isOverflowing -> Modifier.padding(top = ShizziTheme.spacing.xxxl)
        else -> Modifier.weight(1f)
    }

    Box(
        modifier = Modifier.fillMaxWidth().then(sizing),
        contentAlignment = Alignment.Center,
    ) {
        CompatibilityVerdict(state)
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
    when (state) {
        is CompatibilityState.Complete -> when {
            state.resultFor(capability)?.isPresent == true -> CapabilityStatus.SUCCESS
            else -> CapabilityStatus.FAILURE
        }

        is CompatibilityState.Failed -> CapabilityStatus.FAILURE
        else -> CapabilityStatus.LOADING
    }

private fun detailFor(state: CompatibilityState, capability: Capability): String = when (state) {
    is CompatibilityState.Complete -> state.resultFor(capability)?.detail.orEmpty()
    else -> ""
}

private fun CompatibilityState.Complete.resultFor(capability: Capability): CapabilityResult? =
    results.firstOrNull { it.capability == capability }
