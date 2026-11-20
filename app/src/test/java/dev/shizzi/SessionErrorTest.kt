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
        assertEquals(
            UiText.Resource(R.string.session_ended_unexpectedly),
            SessionError.describe(SessionError.ENDED_UNEXPECTEDLY),
        )
    }

    @Test
    fun `reports a clean shizuku loss`() {
        assertEquals(
            UiText.Resource(R.string.notification_shizuku_loss),
            SessionError.describe(SessionError.lost(null)),
        )
    }

    @Test
    fun `carries the teardown problem into the translation`() {
        val raw = SessionError.lost("teardown failed: busy")

        assertEquals(
            UiText.Resource(R.string.notification_session_loss_problem, listOf("teardown failed: busy")),
            SessionError.describe(raw),
        )
    }

    @Test
    fun `passes other errors through as written`() {
        assertEquals(UiText.Verbatim("datapath: bind failed"), SessionError.describe("datapath: bind failed"))
    }
}
