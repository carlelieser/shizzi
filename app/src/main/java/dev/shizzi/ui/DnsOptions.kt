package dev.shizzi.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.shizzi.DnsProvider
import dev.shizzi.R
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ShizziTheme

private val LogoSize = 24.dp

private val RadioSize = 20.dp

private val RadioStroke = 2.dp

@DrawableRes
private fun DnsProvider.logo(): Int = when (this) {
    DnsProvider.CLOUDFLARE -> R.drawable.ic_dns_cloudflare
    DnsProvider.GOOGLE -> R.drawable.ic_dns_google
    DnsProvider.QUAD9 -> R.drawable.ic_dns_quad9
    DnsProvider.OPENDNS -> R.drawable.ic_dns_opendns
    DnsProvider.ADGUARD -> R.drawable.ic_dns_adguard
}

data class DnsOptionText(val primary: String, val secondary: String)

@Composable
fun DnsProviderOption(provider: DnsProvider, isSelected: Boolean, onSelect: () -> Unit) {
    DnsOption(
        text = DnsOptionText(provider.label, provider.ipv4.joinToString(", ")),
        isSelected = isSelected,
        onSelect = onSelect,
    ) {
        Icon(
            painter = painterResource(provider.logo()),
            contentDescription = null,
            tint = ShizziTheme.colors.onSurface,
            modifier = Modifier.size(LogoSize),
        )
    }
}

@Composable
fun DnsOption(
    text: DnsOptionText,
    isSelected: Boolean,
    onSelect: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = ShizziTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.lg),
    ) {
        Box(modifier = Modifier.size(LogoSize), contentAlignment = Alignment.Center) { leading() }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text.primary,
                style = ShizziTheme.typography.subheading,
                color = ShizziTheme.colors.onSurface,
            )
            Text(
                text = text.secondary,
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )
        }

        ThemedRadio(isSelected = isSelected)
    }
}

/** A radio mark that is square in Neobrutalism and round in Material Expressive. */
@Composable
private fun ThemedRadio(isSelected: Boolean) {
    val colors = ShizziTheme.colors
    val isBrutal = ShizziTheme.design == DesignLanguage.NEOBRUTALISM
    val ring = if (isSelected) colors.primary else colors.onSurfaceMuted
    val edge = if (isBrutal) colors.border else ring

    Canvas(modifier = Modifier.size(RadioSize)) {
        val stroke = RadioStroke.toPx()
        drawMark(color = edge, inset = stroke / 2f, style = Stroke(width = stroke), isRound = !isBrutal)

        if (isSelected) drawMark(color = colors.primary, inset = stroke * 2f, style = Fill, isRound = !isBrutal)
    }
}

private fun DrawScope.drawMark(color: Color, inset: Float, style: DrawStyle, isRound: Boolean) {
    val side = size.minDimension - inset * 2f
    val corner = if (isRound) CornerRadius(side / 2f) else CornerRadius.Zero

    drawRoundRect(color, Offset(inset, inset), Size(side, side), corner, style = style)
}
