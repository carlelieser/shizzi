package dev.shizzi

import android.service.quicksettings.Tile
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTileTest {

    private val ready = ShizukuState.Ready(uid = 2000, isRoot = false)

    private fun text(id: Int) = UiText.Resource(id)

    private fun devices(count: Int) = UiText.Plural(
        id = R.plurals.tile_device_count,
        quantity = count,
        formatArgs = listOf(count, Traffic.format(2_000)),
    )

    @Test
    fun `is unavailable and opens the app while shizuku is missing`() {
        val render = SessionTile.render(SessionUiState(), ShizukuState.NotInstalled, false)

        assertEquals(Tile.STATE_UNAVAILABLE, render.state)
        assertEquals(text(R.string.tile_shizuku_not_installed), render.subtitle)
        assertEquals(TileAction.OPEN_APP, render.action)
    }

    @Test
    fun `names the reason shizuku cannot be used`() {
        val stopped = SessionTile.render(SessionUiState(), ShizukuState.NotRunning, false)
        val ungranted =
            SessionTile.render(SessionUiState(), ShizukuState.PermissionRequired, false)

        assertEquals(text(R.string.tile_shizuku_not_running), stopped.subtitle)
        assertEquals(text(R.string.permission_required), ungranted.subtitle)
    }

    @Test
    fun `offers a start while idle`() {
        val render = SessionTile.render(SessionUiState(status = UiStatus.READY), ready, false)

        assertEquals(Tile.STATE_INACTIVE, render.state)
        assertEquals(text(R.string.tile_tap_share), render.subtitle)
        assertEquals(TileAction.START, render.action)
    }

    @Test
    fun `refuses a tap while a session is starting`() {
        val render = SessionTile.render(SessionUiState(status = UiStatus.LOADING), ready, false)

        assertEquals(Tile.STATE_UNAVAILABLE, render.state)
        assertEquals(text(R.string.tile_starting), render.subtitle)
        assertEquals(TileAction.NONE, render.action)
    }

    @Test
    fun `says it is stopping while tearing a session down`() {
        val render = SessionTile.render(SessionUiState(status = UiStatus.LOADING), ready, true)

        assertEquals(text(R.string.tile_stopping), render.subtitle)
        assertEquals(TileAction.NONE, render.action)
    }

    @Test
    fun `offers a stop while connected`() {
        val state = SessionUiState(status = UiStatus.CONNECTED)

        val render = SessionTile.render(state, ready, false)

        assertEquals(Tile.STATE_ACTIVE, render.state)
        assertEquals(TileAction.STOP, render.action)
    }

    @Test
    fun `counts connected devices in the subtitle`() {
        val none = SessionUiState(status = UiStatus.CONNECTED)
        val one = none.copy(clientCount = 1, traffic = Traffic(down = 2_000))
        val many = none.copy(clientCount = 3, traffic = Traffic(down = 2_000))

        assertEquals(text(R.string.tile_no_devices), SessionTile.render(none, ready, false).subtitle)
        assertEquals(devices(1), SessionTile.render(one, ready, false).subtitle)
        assertEquals(devices(3), SessionTile.render(many, ready, false).subtitle)
    }

    @Test
    fun `offers a retry after a failure`() {
        val render = SessionTile.render(SessionUiState(status = UiStatus.ERROR), ready, false)

        assertEquals(Tile.STATE_INACTIVE, render.state)
        assertEquals(text(R.string.tile_tap_retry), render.subtitle)
        assertEquals(TileAction.START, render.action)
    }
}
