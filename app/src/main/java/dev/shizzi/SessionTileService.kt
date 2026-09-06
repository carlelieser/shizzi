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
        val render = SessionTile.render(session, ShizukuGate.currentState(), isStopping)

        if (session.status != UiStatus.LOADING) isStopping = false

        tile.state = render.state
        tile.label = SessionTile.LABEL
        tile.subtitle = render.subtitle
        tile.contentDescription = "${SessionTile.LABEL}, ${render.subtitle}"
        tile.updateTile()
    }

    private fun currentRender(): TileRender = SessionTile.render(
        SessionService.liveState.value,
        ShizukuGate.currentState(),
        isStopping,
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
