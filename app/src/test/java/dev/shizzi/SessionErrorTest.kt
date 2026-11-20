package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionErrorTest {

    @Test
    fun `translates errors the app raises itself`() {
        assertEquals(
            UiText.Resource(R.string.usb_cable_required),
            SessionError.describe(UsbDownstream.NO_USB_CABLE),
        )
        assertEquals(
            UiText.Resource(R.string.vpn_disconnected_session_stopped),
            SessionError.describe(SessionError.VPN_LOST),
        )
    }

    @Test
    fun `tells apart session ends that may leave the hotspot on`() {
        assertEquals(
            UiText.Resource(R.string.session_end_shizuku_stopped),
            SessionError.describe(SessionEnd.SHIZUKU_STOPPED.message(isHotspotPossiblyOn = false)),
        )
        assertEquals(
            UiText.Resource(R.string.session_end_restart_failed_hotspot_on),
            SessionError.describe(SessionEnd.RESTART_FAILED.message(isHotspotPossiblyOn = true)),
        )
    }

    @Test
    fun `passes other errors through as written`() {
        assertEquals(UiText.Verbatim("datapath: bind failed"), SessionError.describe("datapath: bind failed"))
    }
}
