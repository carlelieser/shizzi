package dev.shizzi

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SessionTileService : TileService() {

    private var scope: CoroutineScope? = null

    private var isStopping = false

    private val localizedTileText = object : SessionTileText {
        override val stopping: String get() = L10n.string(R.string.tile_stopping)
        override val starting: String get() = L10n.string(R.string.tile_starting)
        override val tapToRetry: String get() = L10n.string(R.string.tile_tap_retry)
        override val tapToShare: String get() = L10n.string(R.string.tile_tap_share)
        override val shizukuNotInstalled: String
            get() = L10n.string(R.string.tile_shizuku_not_installed)
        override val shizukuNotRunning: String
            get() = L10n.string(R.string.tile_shizuku_not_running)
        override val permissionRequired: String
            get() = L10n.string(R.string.permission_required)
        override val noDevices: String get() = L10n.string(R.string.tile_no_devices)

        override fun deviceCount(count: Int, bytesDown: Long): String =
            L10n.quantity(
                R.plurals.tile_device_count,
                count,
                count,
                Traffic.format(bytesDown),
            )
    }

    override fun onStartListening() {
        super.onStartListening()

        val created = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = created

        created.launch {
            SessionService.liveState.collectLatest { publish(it) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()

        val render = currentRender()

        when (render.action) {
            TileAction.NONE -> Unit
            TileAction.OPEN_APP -> openApp()
            TileAction.START -> unlockAndRun { command(isStop = false) }
            TileAction.STOP -> unlockAndRun { command(isStop = true) }
        }
    }

    private fun command(isStop: Boolean) {
        isStopping = isStop

        runCatching {
            when {
                isStop -> SessionService.stop(this)
                else -> SessionService.start(this)
            }
        }.onFailure { failure ->
            isStopping = false
            SessionLog.error(
                "tile could not reach the session service: " +
                    "${failure.javaClass.simpleName}: ${failure.message}",
            )
        }

        publish(SessionService.liveState.value)
    }

    private fun publish(session: SessionUiState) {
        val tile = qsTile ?: return
        val render = SessionTile.render(
            session = session,
            shizuku = ShizukuGate.currentState(),
            isStopping = isStopping,
            text = localizedTileText,
        )

        if (session.status != UiStatus.LOADING) isStopping = false

        tile.state = render.state
        tile.label = SessionTile.LABEL
        tile.subtitle = render.subtitle
        tile.contentDescription = "${SessionTile.LABEL}, ${render.subtitle}"
        tile.updateTile()
    }

    private fun currentRender(): TileRender = SessionTile.render(
        session = SessionService.liveState.value,
        shizuku = ShizukuGate.currentState(),
        isStopping = isStopping,
        text = localizedTileText,
    )

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )

            else -> collapseOnLegacy(intent)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun collapseOnLegacy(intent: Intent) = startActivityAndCollapse(intent)
}
