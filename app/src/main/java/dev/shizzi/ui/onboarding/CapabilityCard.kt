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

private val MarkSize = 20.dp

private val SpinnerSize = 16.dp

enum class CapabilityStatus { LOADING, SUCCESS, FAILURE }

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

            if (status == CapabilityStatus.FAILURE) {
                CapabilityDetail(detail)
            }
        }

        StatusMark(status)
    }
}

@Composable
private fun CapabilityDetail(detail: String) {
    Text(
        text = breakableIdentifiers(detail),
        style = ShizziTheme.typography.log,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = ShizziTheme.spacing.xs),
    )
}

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

private fun descriptionFor(capability: Capability): String = when (capability) {
    Capability.TEST_NETWORK ->
        "Lets the app create the test network tunnel your hotspot traffic " +
            "travels through."

    Capability.PREFER_TEST_NETWORKS ->
        "Lets the app route the hotspot through the test network tunnel. " +
            "Added in Android 13."
}
