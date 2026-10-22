package dev.shizzi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.shizzi.AppPermission
import dev.shizzi.BuildConfig
import dev.shizzi.PermissionStatus
import dev.shizzi.PrivilegeBackendType
import dev.shizzi.PrivilegeState
import dev.shizzi.R
import dev.shizzi.VpnMode
import dev.shizzi.str
import dev.shizzi.ui.theme.AccentChoice
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.HeaderHeight
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.ThemeChoice
import dev.shizzi.ui.theme.standardTween

private const val UPSTREAM_URL = "https://github.com/carlelieser/shizzi"
private const val ISSUE_URL = "https://github.com/carlelieser/shizzi/issues/new"
private const val AUTHOR_URL = "https://carlelieser.dev"

data class SettingsState(
    val backend: PrivilegeBackendType,
    val privilegeState: PrivilegeState,
    val permissions: List<PermissionStatus>,
    val theme: ThemeChoice,
    val design: DesignLanguage,
    val accent: AccentChoice,
    val isLogging: Boolean,
    val vpnMode: VpnMode,
    val isRunningDiagnostics: Boolean,
    val automation: AutomationState,
)

data class SettingsActions(
    val onSetTheme: (ThemeChoice) -> Unit,
    val onSetDesign: (DesignLanguage) -> Unit,
    val onSetAccent: (AccentChoice) -> Unit,
    val onSetLogging: (Boolean) -> Unit,
    val onSetVpnMode: (VpnMode) -> Unit,
    val onOpenLog: () -> Unit,
    val onRunProbes: () -> Unit,
    val onCancelProbes: () -> Unit,
    val onGrantPermission: (AppPermission) -> Unit,
    val onSetPrivilegeBackend: (PrivilegeBackendType) -> Unit,
    val onShizukuAction: () -> Unit,
    val onStartLocalAdbPairing: () -> Unit,
    val onCancelLocalAdbPairing: () -> Unit,
    val onRestartOnboarding: () -> Unit,
    val automation: AutomationActions,
)

@Composable
fun SettingsPage(state: SettingsState, actions: SettingsActions, toasts: ToastState, onBack: () -> Unit) {
    val listState = rememberLazyListState()

    // The header blends into the page background while the list rests at the
    // top, and picks up its surface treatment once content scrolls beneath it.
    val isScrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
    val colors = ShizziTheme.colors
    val headerColor by animateColorAsState(
        targetValue = if (isScrolled) colors.surfaceContainer else colors.background,
        animationSpec = standardTween(),
        label = "settingsHeaderColor",
    )
    val headerShadow by animateDpAsState(
        targetValue = if (isScrolled) 1.dp else 0.dp,
        animationSpec = standardTween(),
        label = "settingsHeaderShadow",
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                // The list draws edge to edge; it starts below the header and
                // scrolls beneath it, including under the status bar.
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                    HeaderHeight + ShizziTheme.spacing.md,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    ShizziTheme.spacing.xxl,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(settingsSections(state, actions, toasts), key = { it.label }) { section ->
                SettingsSection(section.label, section.content)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor)
                .statusBarsPadding(),
        ) {
            ScreenHeader(
                title = str(R.string.settings),
                onBack = onBack,
                modifier = Modifier.fillMaxWidth(),
                containerColor = headerColor,
                tonalElevation = 0.dp,
                shadowElevation = headerShadow,
            )
        }
    }
}

@Immutable
private data class SettingsSectionSpec(val label: String, val content: @Composable () -> Unit)

private fun settingsSections(state: SettingsState, actions: SettingsActions, toasts: ToastState) = listOf(
    SettingsSectionSpec(str(R.string.appearance)) {
        AppearanceSection(
            AppearanceState(state.theme, state.design, state.accent),
            AppearanceActions(actions.onSetTheme, actions.onSetDesign, actions.onSetAccent),
        )
    },
    SettingsSectionSpec(str(R.string.permissions)) {
        PermissionsSection(
            state = PermissionsSectionState(state.backend, state.privilegeState, state.permissions),
            onGrantPermission = actions.onGrantPermission,
            privilegeActions = PrivilegeAccessActions(
                actions.onSetPrivilegeBackend,
                actions.onShizukuAction,
                actions.onStartLocalAdbPairing,
                actions.onCancelLocalAdbPairing,
            ),
        )
    },
    SettingsSectionSpec(str(R.string.advanced)) {
        VpnSection(selected = state.vpnMode, onSelect = actions.onSetVpnMode)
        AutomationSection(state.automation, actions.automation, toasts)
    },
    SettingsSectionSpec(str(R.string.developer)) { DeveloperSection(state, actions) },
    SettingsSectionSpec(str(R.string.about)) { AboutSection() },
)

@Composable
private fun SettingsSection(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().widthIn(max = 860.dp).padding(bottom = ShizziTheme.spacing.lg)) {
        SectionLabel(label)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ShizziTheme.colors.surfaceContainer),
        ) {
            Column(Modifier.padding(horizontal = ShizziTheme.spacing.md)) { content() }
        }
    }
}

@Composable
private fun DeveloperSection(state: SettingsState, actions: SettingsActions) {
    SettingsToggle(SettingsText(str(R.string.record_logs)), state.isLogging, actions.onSetLogging)
    SettingsAction(SettingsText(str(R.string.view_logs)), onClick = actions.onOpenLog)
    SettingsAction(
        SettingsText(if (state.isRunningDiagnostics) str(R.string.cancel_diagnostics) else str(R.string.run_diagnostics)),
        onClick = if (state.isRunningDiagnostics) actions.onCancelProbes else actions.onRunProbes,
    )
    SettingsAction(SettingsText(str(R.string.restart_onboarding)), onClick = actions.onRestartOnboarding)
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    SettingsLabel(
        title = "Shizzi",
        subtitle = str(R.string.vvalue_material_3_expressive_localized, BuildConfig.VERSION_NAME),
        modifier = Modifier.fillMaxWidth().padding(vertical = ShizziTheme.spacing.md),
    )
    SettingsAction(SettingsText(str(R.string.upstream_project), "carlelieser/shizzi"), true) { context.openUrl(UPSTREAM_URL) }
    SettingsAction(SettingsText(str(R.string.report_an_issue)), true) { context.openUrl(ISSUE_URL) }
    SettingsAction(SettingsText(str(R.string.original_author), "carlelieser.dev"), true) { context.openUrl(AUTHOR_URL) }
}
