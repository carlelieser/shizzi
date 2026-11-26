package dev.shizzi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import dev.shizzi.ui.ScreenHeader
import dev.shizzi.ui.SectionLabel
import dev.shizzi.ui.SettingsAction
import dev.shizzi.ui.SettingsStatusRow
import dev.shizzi.ui.SettingsText
import dev.shizzi.ui.SettingsToggle
import dev.shizzi.ui.TextFieldDecoration
import dev.shizzi.ui.ThemedTextField
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import kotlin.concurrent.thread
import kotlinx.coroutines.delay

/**
 * Pairs the app with the phone's own Wireless debugging, so the privileged helper can be started
 * without Shizuku. Pairing is done either from the notification (the code is typed there while the
 * Settings dialog stays open) or by entering the port and code here.
 */
class AdbPairActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShizziTheme {
                Surface(color = ShizziTheme.colors.background) {
                    PairScreen(onBack = ::finish)
                }
            }
        }
    }
}

@Composable
private fun PairScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var paired by remember { mutableStateOf(AdbBridge.isPaired(context)) }
    var tcp by remember { mutableStateOf(AdbBridge.isTcpMode(context)) }
    var tcpRandom by remember { mutableStateOf(AdbBridge.isTcpRandom(context)) }
    var tcpPort by remember { mutableStateOf(AdbBridge.tcpPort(context).toString()) }
    var port by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    // Follows the pairing dialog's port while the notification service is running.
    LaunchedEffect(Unit) {
        while (true) {
            val found = AdbPairingService.port
            if (found > 0 && port.isEmpty()) port = found.toString()
            paired = AdbBridge.isPaired(context)
            delay(1000)
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { AdbPairingService.start(context) }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.adb_title), onBack = onBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding),
        ) {
            Spacer(Modifier.height(ShizziTheme.spacing.md))
            Text(
                text = stringResource(R.string.adb_intro),
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )

            SectionLabel(stringResource(R.string.adb_status))
            SettingsStatusRow(
                SettingsText(
                    title = stringResource(if (paired) R.string.adb_paired else R.string.adb_not_paired),
                    subtitle = if (paired) stringResource(R.string.adb_paired_hint) else "",
                ),
            )

            SectionLabel(stringResource(R.string.adb_pair_section))
            SettingsAction(
                label = SettingsText(
                    title = stringResource(R.string.adb_open_dev_settings),
                    subtitle = stringResource(R.string.adb_open_dev_settings_hint),
                ),
                isExternal = true,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
            SettingsAction(
                label = SettingsText(
                    title = stringResource(R.string.adb_pair_with_notification),
                    subtitle = stringResource(R.string.adb_pair_with_notification_hint),
                ),
                onClick = {
                    val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        AdbPairingService.start(context)
                    }
                },
            )

            Spacer(Modifier.height(ShizziTheme.spacing.md))
            ThemedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                decoration = TextFieldDecoration(
                    placeholder = stringResource(R.string.adb_port_hint),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
                ),
            )
            Spacer(Modifier.height(ShizziTheme.spacing.sm))
            ThemedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                decoration = TextFieldDecoration(
                    placeholder = stringResource(R.string.adb_code_hint),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
                ),
            )

            val pairNow = {
                val portNumber = port.toIntOrNull()
                if (portNumber == null || code.length < 6) {
                    message = context.getString(R.string.adb_enter_both)
                } else if (!busy) {
                    busy = true
                    message = context.getString(R.string.adb_pairing)
                    val app = context.applicationContext
                    val enteredCode = code
                    thread(name = "shizzi-adb-pair") {
                        val result = runCatching { AdbHelper.pair(app, portNumber, enteredCode) }
                        busy = false
                        message = result.fold(
                            onSuccess = { app.getString(R.string.adb_paired_ok) },
                            onFailure = { app.getString(R.string.adb_pair_failed, it.message ?: "") },
                        )
                        if (result.isSuccess) {
                            paired = true
                            code = ""
                        }
                    }
                }
            }
            SettingsAction(
                label = SettingsText(title = stringResource(R.string.adb_pair_now)),
                onClick = pairNow,
            )

            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    style = ShizziTheme.typography.body,
                    color = ShizziTheme.colors.onSurfaceMuted,
                    modifier = Modifier.padding(vertical = ShizziTheme.spacing.sm),
                )
            }

            SectionLabel(stringResource(R.string.adb_options))
            SettingsToggle(
                label = SettingsText(
                    title = stringResource(R.string.adb_tcp_mode),
                    subtitle = stringResource(R.string.adb_tcp_mode_hint),
                ),
                isChecked = tcp,
                onCheckedChange = {
                    tcp = it
                    AdbBridge.setTcpMode(context, it)
                },
            )

            if (tcp) {
                SettingsToggle(
                    label = SettingsText(
                        title = stringResource(R.string.adb_tcp_random),
                        subtitle = stringResource(R.string.adb_tcp_random_hint),
                    ),
                    isChecked = tcpRandom,
                    onCheckedChange = {
                        tcpRandom = it
                        AdbBridge.setTcpRandom(context, it)
                    },
                )

                if (!tcpRandom) {
                    val portValue = tcpPort.toIntOrNull()
                    ThemedTextField(
                        value = tcpPort,
                        onValueChange = {
                            tcpPort = it.filter(Char::isDigit).take(5)
                            tcpPort.toIntOrNull()?.let { value -> AdbBridge.setTcpPort(context, value) }
                        },
                        decoration = TextFieldDecoration(
                            placeholder = stringResource(R.string.adb_tcp_port_hint),
                            error = if (portValue == null || !AdbBridge.validPort(portValue)) {
                                stringResource(R.string.adb_tcp_port_invalid)
                            } else {
                                null
                            },
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
                        ),
                    )
                }
            }

            Spacer(Modifier.height(ShizziTheme.spacing.xxl))
        }
    }
}
