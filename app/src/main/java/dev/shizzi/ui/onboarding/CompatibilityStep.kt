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

/**
 * The capabilities, then the verdict they add up to.
 *
 * Scrolls, because a device failing both checks carries two quoted resolution
 * failures and that exceeds a short screen.
 */
@Composable
fun CompatibilityStep(state: CompatibilityState) {
    // Scrolls only when a failure's quoted detail is on screen. Scrolling
    // always would leave the column unbounded, and the verdict below it could
    // not take a weight — which is what centres it in the gap above the dots.
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

/**
 * Centres the verdict in whatever is left between the cards and the footer.
 *
 * Falls back to wrapping its content while the column scrolls, where there is
 * no bounded height to take a fraction of.
 */
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

/**
 * Why the check could not run, under the rows it left unanswered.
 *
 * Distinct from a capability's own detail: this is the app failing to reach the
 * privileged process, so it belongs to the run rather than to either row.
 */
@Composable
private fun CheckFailure(problem: String) {
    Text(
        text = problem,
        style = ShizziTheme.typography.log,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = ShizziTheme.spacing.lg),
    )
}

/**
 * Idle renders as loading rather than a fourth state: the check starts with the
 * step, so the gap before the first result is the only time this is seen.
 */
private fun statusFor(state: CompatibilityState, capability: Capability): CapabilityStatus =
    when (state) {
        is CompatibilityState.Complete -> when {
            state.resultFor(capability)?.isPresent == true -> CapabilityStatus.SUCCESS
            else -> CapabilityStatus.FAILURE
        }

        is CompatibilityState.Failed -> CapabilityStatus.FAILURE
        else -> CapabilityStatus.LOADING
    }

/**
 * Empty unless the run answered: a failed run's reason is the run's, and
 * repeating it under both rows would state it twice.
 */
private fun detailFor(state: CompatibilityState, capability: Capability): String = when (state) {
    is CompatibilityState.Complete -> state.resultFor(capability)?.detail.orEmpty()
    else -> ""
}

private fun CompatibilityState.Complete.resultFor(capability: Capability): CapabilityResult? =
    results.firstOrNull { it.capability == capability }
