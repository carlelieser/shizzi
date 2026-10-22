package dev.shizzi

import android.service.quicksettings.Tile

enum class TileAction { START, STOP, OPEN_APP, NONE }

data class TileRender(
    val state: Int,
    val subtitle: String,
    val action: TileAction,
)

object SessionTile {

    const val LABEL = "Shizzi"

    fun render(session: SessionUiState, privilege: PrivilegeState, isStopping: Boolean): TileRender {
        if (privilege !is PrivilegeState.Ready) return unavailable(privilege)

        return when (session.status) {
            UiStatus.LOADING -> TileRender(
                state = Tile.STATE_UNAVAILABLE,
                subtitle = if (isStopping) str(R.string.stopping) else str(R.string.starting),
                action = TileAction.NONE,
            )

            UiStatus.CONNECTED -> TileRender(
                state = Tile.STATE_ACTIVE,
                subtitle = describeConnected(session),
                action = TileAction.STOP,
            )

            UiStatus.ERROR -> TileRender(
                state = Tile.STATE_INACTIVE,
                subtitle = str(R.string.tap_to_retry),
                action = TileAction.START,
            )

            UiStatus.READY -> TileRender(
                state = Tile.STATE_INACTIVE,
                subtitle = str(R.string.tap_to_start_sharing),
                action = TileAction.START,
            )
        }
    }

    private fun unavailable(privilege: PrivilegeState) = TileRender(
        state = Tile.STATE_UNAVAILABLE,
        subtitle = describe(privilege),
        action = TileAction.OPEN_APP,
    )

    private fun describe(privilege: PrivilegeState): String = when (privilege) {
        is PrivilegeState.Ready -> ""
        is PrivilegeState.SetupRequired -> str(R.string.provider_setup_required)
        is PrivilegeState.Connecting -> str(R.string.local_adb_connecting)
        is PrivilegeState.Unsupported -> str(R.string.local_adb_unsupported)
        is PrivilegeState.Error -> str(R.string.local_adb_error, privilege.message)
    }

    private fun describeConnected(session: SessionUiState): String = when (session.clientCount) {
        0 -> str(R.string.no_connected_devices)
        1 -> str(R.string.one_device_traffic, Traffic.format(session.traffic.down))
        else -> str(R.string.value_devices_value, session.clientCount, Traffic.format(session.traffic.down))
    }
}
