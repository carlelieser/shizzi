package dev.shizzi

import android.service.quicksettings.Tile
import java.util.Locale

enum class TileAction { START, STOP, OPEN_APP, NONE }

data class TileRender(
    val state: Int,
    val subtitle: String,
    val action: TileAction,
)

interface SessionTileText {
    val stopping: String
    val starting: String
    val tapToRetry: String
    val tapToShare: String
    val shizukuNotInstalled: String
    val shizukuNotRunning: String
    val permissionRequired: String
    val noDevices: String

    fun deviceCount(count: Int, bytesDown: Long): String

    companion object {
        val English: SessionTileText = object : SessionTileText {
            override val stopping = "Stopping…"
            override val starting = "Starting…"
            override val tapToRetry = "Tap to retry"
            override val tapToShare = "Tap to share"
            override val shizukuNotInstalled = "Shizuku not installed"
            override val shizukuNotRunning = "Shizuku not running"
            override val permissionRequired = "Permission required"
            override val noDevices = "No devices"

            override fun deviceCount(count: Int, bytesDown: Long): String {
                val trafficDown = Traffic.format(bytesDown, Locale.US)

                return when (count) {
                    1 -> "1 device · $trafficDown"
                    else -> "$count devices · $trafficDown"
                }
            }
        }
    }
}

object SessionTile {

    const val LABEL = "Shizzi"

    fun render(
        session: SessionUiState,
        shizuku: ShizukuState,
        isStopping: Boolean,
        text: SessionTileText = SessionTileText.English,
    ): TileRender {
        if (shizuku !is ShizukuState.Ready) return unavailable(shizuku, text)

        return when (session.status) {
            UiStatus.LOADING -> TileRender(
                state = Tile.STATE_UNAVAILABLE,
                subtitle = if (isStopping) text.stopping else text.starting,
                action = TileAction.NONE,
            )

            UiStatus.CONNECTED -> TileRender(
                state = Tile.STATE_ACTIVE,
                subtitle = describeConnected(session, text),
                action = TileAction.STOP,
            )

            UiStatus.ERROR -> TileRender(
                state = Tile.STATE_INACTIVE,
                subtitle = text.tapToRetry,
                action = TileAction.START,
            )

            UiStatus.READY -> TileRender(
                state = Tile.STATE_INACTIVE,
                subtitle = text.tapToShare,
                action = TileAction.START,
            )
        }
    }

    private fun unavailable(shizuku: ShizukuState, text: SessionTileText) = TileRender(
        state = Tile.STATE_UNAVAILABLE,
        subtitle = describe(shizuku, text),
        action = TileAction.OPEN_APP,
    )

    private fun describe(shizuku: ShizukuState, text: SessionTileText): String = when (shizuku) {
        ShizukuState.NotInstalled -> text.shizukuNotInstalled
        ShizukuState.NotRunning -> text.shizukuNotRunning
        ShizukuState.PermissionRequired -> text.permissionRequired
        is ShizukuState.Ready -> ""
    }

    private fun describeConnected(session: SessionUiState, text: SessionTileText): String =
        when (session.clientCount) {
            0 -> text.noDevices
            else -> text.deviceCount(session.clientCount, session.traffic.down)
        }
}
