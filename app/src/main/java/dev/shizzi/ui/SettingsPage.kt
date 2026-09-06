package dev.shizzi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.AppPermission
import dev.shizzi.PermissionStatus
import dev.shizzi.ShizukuState
import dev.shizzi.ui.theme.AccentChoice
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.ThemeChoice
import dev.shizzi.ui.theme.standardTween

private const val SOURCE_URL = "https://github.com/carlelieser/shizzi"
private const val ISSUE_URL = "https://github.com/carlelieser/shizzi/issues/new"
private const val AUTHOR_URL = "https://carlelieser.dev"

private const val BusyAlpha = 0.4f

private const val SectionStaggerMillis = 45

private val SectionRise = 12.dp

data class SettingsState(
    val shizuku: ShizukuState,
    val permissions: List<PermissionStatus>,
    val theme: ThemeChoice,
    val design: DesignLanguage,
    val accent: AccentChoice,
    val customAccents: List<Int>,
    val isLogging: Boolean,
    val isRunningDiagnostics: Boolean,
    val automation: AutomationState,
)

data class SettingsActions(
    val onSetTheme: (ThemeChoice) -> Unit,
    val onSetDesign: (DesignLanguage) -> Unit,
    val onSetAccent: (AccentChoice) -> Unit,
    val onAddCustomAccent: (Int) -> Unit,
    val onSetLogging: (Boolean) -> Unit,
    val onOpenLog: () -> Unit,
    val onRunProbes: () -> Unit,
    val onGrantPermission: (AppPermission) -> Unit,
    val onShizukuAction: () -> Unit,
    val onRestartOnboarding: () -> Unit,
    val automation: AutomationActions,
)

@Composable
fun SettingsPage(
    state: SettingsState,
    actions: SettingsActions,
    toasts: ToastState,
    onBack: () -> Unit,
) {
    val isBusy = state.isRunningDiagnostics

    val busyAlpha by animateFloatAsState(
        targetValue = if (isBusy) BusyAlpha else 1f,
        animationSpec = standardTween(),
        label = "settingsBusy",
    )

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

        ScreenHeader(title = "Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState(), enabled = !isBusy)
                .alpha(busyAlpha)
                .inert(isBusy)
                .padding(horizontal = ScreenPadding),
        ) {
            settingsSections(state, actions, toasts).forEachIndexed { index, section ->
                SettingsSection(index = index, label = section.label, content = section.content)
            }

            Spacer(Modifier.height(ShizziTheme.spacing.xxl))
        }
    }
}

@Immutable
private data class SettingsSectionSpec(
    val label: String,
    val content: @Composable () -> Unit,
)

private fun settingsSections(
    state: SettingsState,
    actions: SettingsActions,
    toasts: ToastState,
): List<SettingsSectionSpec> = listOf(
    SettingsSectionSpec("Appearance") {
        AppearanceSection(
            state = AppearanceState(
                theme = state.theme,
                design = state.design,
                accent = state.accent,
                customAccents = state.customAccents,
            ),
            actions = AppearanceActions(
                onSetTheme = actions.onSetTheme,
                onSetDesign = actions.onSetDesign,
                onSetAccent = actions.onSetAccent,
                onAddCustomAccent = actions.onAddCustomAccent,
            ),
        )
    },

    SettingsSectionSpec("Permissions") {
        PermissionsSection(
            state = PermissionsSectionState(
                shizuku = state.shizuku,
                permissions = state.permissions,
            ),
            onGrantPermission = actions.onGrantPermission,
            onShizukuAction = actions.onShizukuAction,
        )
    },

    SettingsSectionSpec("Advanced") {
        AutomationSection(
            state = state.automation,
            actions = actions.automation,
            toasts = toasts,
        )
    },

    SettingsSectionSpec("Developer") {
        DeveloperSection(isLogging = state.isLogging, actions = actions)
    },

    SettingsSectionSpec("About") { AboutSection() },
)

/** Staggers each section in on first composition so the page assembles itself. */
@Composable
private fun SettingsSection(index: Int, label: String, content: @Composable () -> Unit) {
    var hasEntered by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = tween(
            durationMillis = ShizziTheme.motion.standardMillis,
            delayMillis = index * SectionStaggerMillis,
            easing = ShizziTheme.motion.easing,
        ),
        label = "sectionEntrance",
    )

    LaunchedEffect(Unit) { hasEntered = true }

    val shift = with(LocalDensity.current) { SectionRise.toPx() }

    Column(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * shift
        },
    ) {
        SectionLabel(label)
        content()
    }
}

private fun Modifier.inert(isBusy: Boolean): Modifier = when {
    !isBusy -> this
    else -> this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                    .changes
                    .forEach { it.consume() }
            }
        }
    }
}

@Composable
private fun DeveloperSection(isLogging: Boolean, actions: SettingsActions) {
    SettingsToggle(
        label = SettingsText(title = "Logging"),
        isChecked = isLogging,
        onCheckedChange = actions.onSetLogging,
    )

    SettingsAction(
        label = SettingsText(title = "View logs"),
        onClick = actions.onOpenLog,
    )

    SettingsAction(
        label = SettingsText(title = "Run diagnostics"),
        onClick = actions.onRunProbes,
    )

    SettingsAction(
        label = SettingsText(title = "Restart onboarding"),
        onClick = actions.onRestartOnboarding,
    )
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current

    SettingsAction(
        label = SettingsText(title = "GitHub"),
        isExternal = true,
        onClick = { context.openUrl(SOURCE_URL) },
    )

    SettingsAction(
        label = SettingsText(title = "Report a bug"),
        isExternal = true,
        onClick = { context.openUrl(ISSUE_URL) },
    )

    SettingsAction(
        label = SettingsText(title = "Author", subtitle = "carlelieser.dev"),
        isExternal = true,
        onClick = { context.openUrl(AUTHOR_URL) },
    )
}
