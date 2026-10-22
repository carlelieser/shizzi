package dev.shizzi.ui

import dev.shizzi.R
import dev.shizzi.str

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ShizziTheme

private val CheckSize = 20.dp

fun designLabel(design: DesignLanguage): String = when (design) {
    DesignLanguage.NEOBRUTALISM -> "Neo-Brutalism"
    DesignLanguage.MATERIAL_EXPRESSIVE -> "Material 3"
}

private fun descriptionOf(design: DesignLanguage): String = when (design) {
    DesignLanguage.NEOBRUTALISM -> str(R.string.hard_edges_high_contrast_and_offset_shadows)
    DesignLanguage.MATERIAL_EXPRESSIVE -> str(R.string.rounded_surfaces_tonal_hierarchy_and_natural_motion)
}

@Composable
fun DesignPicker(
    selected: DesignLanguage,
    onSelect: (DesignLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    ThemedBottomSheet(onDismiss = onDismiss) {
        Text(
            text = str(R.string.design),
            style = ShizziTheme.typography.heading,
            color = ShizziTheme.colors.onSurface,
        )

        DesignLanguage.entries.forEach { design ->
            DesignOption(
                design = design,
                isSelected = design == selected,
                onSelect = {
                    onSelect(design)
                    onDismiss()
                },
            )
        }

        Spacer(Modifier.height(ShizziTheme.spacing.lg))
    }
}

@Composable
private fun DesignOption(
    design: DesignLanguage,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = ShizziTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = designLabel(design),
                style = ShizziTheme.typography.subheading,
                color = ShizziTheme.colors.onSurface,
            )

            Text(
                text = descriptionOf(design),
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )
        }

        if (!isSelected) return@Row

        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = ShizziTheme.colors.primary,
            modifier = Modifier.size(CheckSize),
        )
    }
}
