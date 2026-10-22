package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.CompatibilityState
import dev.shizzi.R
import dev.shizzi.isCompatible
import dev.shizzi.str
import dev.shizzi.ui.theme.ShizziTheme

private val VerdictIconSize = 120.dp
private val CheckingIconSize = 72.dp

@Composable
fun CompatibilityVerdict(state: CompatibilityState) {
    val colors = ShizziTheme.colors
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            state is CompatibilityState.Checking || state is CompatibilityState.Idle -> Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = str(R.string.checking_compatibility),
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(CheckingIconSize),
            )
            state.isCompatible -> Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = str(R.string.this_device_is_compatible),
                tint = colors.primary,
                modifier = Modifier.size(VerdictIconSize),
            )
            else -> Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = str(R.string.this_device_is_not_compatible),
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(VerdictIconSize),
            )
        }
    }
}
