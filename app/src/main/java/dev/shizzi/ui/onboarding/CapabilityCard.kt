package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.Capability
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

/** Sized like a settings row's trailing glyph, which is the same job. */
private val MarkSize = 20.dp

/** Matches the mark, so the row does not change height when the check lands. */
private val SpinnerSize = 16.dp

/** How one capability's check is going. */
enum class CapabilityStatus { LOADING, SUCCESS, FAILURE }

/**
 * One capability: what it is, why it matters, and how it went.
 *
 * The description is what the row is for. "Prefer test networks" names a method
 * and tells a user nothing, so each row says what the device would be unable to
 * do without it.
 */
@Composable
fun CapabilityCard(capability: Capability, status: CapabilityStatus, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .brutalSurface(fill = ShizziTheme.colors.surface)
            .padding(ShizziTheme.spacing.lg),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
        ) {
            Text(
                text = titleFor(capability),
                style = ShizziTheme.typography.subheading,
                color = ShizziTheme.colors.onSurface,
            )

            Text(
                text = descriptionFor(capability),
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )

            // Only when it failed: the evidence for a capability that is
            // present is the app working, and printing it on every row would
            // bury the one line that matters.
            if (status == CapabilityStatus.FAILURE) {
                CapabilityDetail(detail)
            }
        }

        StatusMark(status)
    }
}

/**
 * The verbatim reason, in the log face — this is a resolution failure quoted
 * from the platform, not copy, and setting it as prose would claim otherwise.
 */
@Composable
private fun CapabilityDetail(detail: String) {
    Text(
        text = detail,
        style = ShizziTheme.typography.log,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = ShizziTheme.spacing.xs),
    )
}

/**
 * Reserved at the mark's size in every state, so a row does not resize as its
 * spinner is replaced by a result.
 */
@Composable
private fun StatusMark(status: CapabilityStatus) {
    val colors = ShizziTheme.colors

    Box(
        modifier = Modifier.size(MarkSize),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            CapabilityStatus.LOADING -> CircularProgressIndicator(
                color = colors.onSurfaceMuted,
                strokeWidth = 2.dp,
                modifier = Modifier.size(SpinnerSize),
            )

            CapabilityStatus.SUCCESS -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Supported",
                tint = colors.primary,
                modifier = Modifier.size(MarkSize),
            )

            CapabilityStatus.FAILURE -> Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Not supported",
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(MarkSize),
            )
        }
    }
}

private fun titleFor(capability: Capability): String = when (capability) {
    Capability.TEST_NETWORK -> "Test network API"
    Capability.PREFER_TEST_NETWORKS -> "Prefer test networks"
}

/**
 * What the device would be unable to do, rather than what the API is called.
 * The two are separate capabilities with separate floors — a device can have
 * the first and not the second — so neither description implies the other.
 */
private fun descriptionFor(capability: Capability): String = when (capability) {
    Capability.TEST_NETWORK ->
        "Lets the app create the tunnel your hotspot traffic travels through."

    Capability.PREFER_TEST_NETWORKS ->
        "Lets the app route the hotspot through that tunnel. Added in Android 13."
}
