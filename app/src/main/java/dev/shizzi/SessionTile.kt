package dev.shizzi

import android.service.quicksettings.Tile

enum class TileAction { START, STOP, OPEN_APP, NONE }

data class TileRender(
    val state: Int,
    val subtitle: UiText,
    val action: TileAction,
)

object SessionTile {

    fun render(session: SessionUiState, shizuku: ShizukuState, isStopping: Boolean): TileRender {
        if (shizuku !is ShizukuState.Ready) return unavailable(shizuku)

        return when (session.status) {
            UiStatus.LOADING -> TileRender(
                state = Tile.STATE_UNAVAILABLE,
                subtitle = loadingSubtitle(session, isStopping),
                action = TileAction.NONE,
            )

            UiStatus.CONNECTED -> TileRender(
                state = Tile.STATE_ACTIVE,
                subtitle = describeConnected(session),
                action = TileAction.STOP,
            )

            UiStatus.ERROR -> TileRender(
                state = Tile.STATE_INACTIVE,
                subtitle = text(R.string.tile_tap_retry),
                action = TileAction.START,
            )

            UiStatus.READY -> TileRender(
                state = Tile.STATE_INACTIVE,
                subtitle = text(R.string.tile_tap_share),
                action = TileAction.START,
            )
        }
    }

    private fun unavailable(shizuku: ShizukuState) = TileRender(
        state = Tile.STATE_UNAVAILABLE,
        subtitle = describe(shizuku),
        action = TileAction.OPEN_APP,
    )

    private fun describe(shizuku: ShizukuState): UiText = when (shizuku) {
        ShizukuState.NotInstalled -> text(R.string.tile_shizuku_not_installed)
        ShizukuState.NotRunning -> text(R.string.tile_shizuku_not_running)
        ShizukuState.PermissionRequired -> text(R.string.permission_required)
        is ShizukuState.Ready -> UiText.Verbatim("")
    }

    private fun loadingSubtitle(session: SessionUiState, isStopping: Boolean): UiText = when {
        isStopping -> text(R.string.tile_stopping)
        session.isReconnecting -> text(R.string.tile_reconnecting)
        else -> text(R.string.tile_starting)
    }

    private fun describeConnected(session: SessionUiState): UiText = when (session.clientCount) {
        0 -> text(R.string.tile_no_devices)
        else -> UiText.Plural(
            id = R.plurals.tile_device_count,
            quantity = session.clientCount,
            formatArgs = listOf(session.clientCount, Traffic.format(session.traffic.down)),
        )
    }

    private fun text(id: Int) = UiText.Resource(id)
}
