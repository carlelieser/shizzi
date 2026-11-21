package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionErrorTest {

    private fun resource(id: Int) = UiText.Resource(id)

    @Test
    fun `translates errors the app raises itself`() {
        assertEquals(resource(R.string.usb_cable_required), SessionError.describe(UsbDownstream.NO_USB_CABLE))
        assertEquals(
            resource(R.string.vpn_disconnected_session_stopped),
            SessionError.describe(SessionError.VPN_LOST),
        )
    }

    @Test
    fun `tells apart session ends that may leave the hotspot on`() {
        assertEquals(
            resource(R.string.session_end_shizuku_stopped),
            SessionError.describe(SessionEnd.SHIZUKU_STOPPED.message(isHotspotPossiblyOn = false)),
        )
        assertEquals(
            resource(R.string.session_end_restart_failed_hotspot_on),
            SessionError.describe(SessionEnd.RESTART_FAILED.message(isHotspotPossiblyOn = true)),
        )
    }

    @Test
    fun `recognises start failures inside an exception description`() {
        assertEquals(
            resource(R.string.session_error_vpn_required),
            SessionError.describe("IllegalStateException: ${SessionError.VPN_REQUIRED}"),
        )
        assertEquals(
            resource(R.string.session_error_hotspot_not_started),
            SessionError.describe("IllegalStateException: ${SessionError.DOWNSTREAM_NOT_STARTED} WIFI did not start"),
        )
        assertEquals(
            resource(R.string.session_error_upstream_not_verified),
            SessionError.describe("IllegalStateException: ${SessionError.UPSTREAM_NOT_VERIFIED} expected only tun0"),
        )
    }

    @Test
    fun `explains an upstream the watchdog gave up on`() {
        val tolerance = UpstreamTolerance("tun0") {}
        val drifted = listOf("wlan0")
        tolerance.judge(UpstreamReading.DRIFTED, drifted)
        val problem = tolerance.judge(UpstreamReading.DRIFTED, drifted)!!

        assertEquals(resource(R.string.session_error_upstream_lost), SessionError.describe(problem))
    }

    @Test
    fun `warns when teardown left a downstream up`() {
        val stillTethered = DownstreamReading.Tethered(listOf("rndis0")).problem!!
        val released = releaseDownstreamWith(stop = { true }, findTethered = { stillTethered })!!

        assertEquals(resource(R.string.session_error_teardown_incomplete), SessionError.describe(released))
        assertEquals(
            resource(R.string.session_error_teardown_incomplete),
            SessionError.describe(DownstreamReading.Unknown.problem!!),
        )
    }

    @Test
    fun `adds the hotspot warning to a problem whose teardown was incomplete`() {
        val raw = "${SessionError.VPN_LOST}${SessionError.TEARDOWN_INCOMPLETE}downstream still tethered: [wlan1]"

        assertEquals(
            UiText.Resource(
                R.string.session_error_hotspot_may_be_on,
                listOf(resource(R.string.vpn_disconnected_session_stopped)),
            ),
            SessionError.describe(raw),
        )
    }

    @Test
    fun `never shows unrecognised errors as written`() {
        assertEquals(
            resource(R.string.session_error_generic),
            SessionError.describe("IllegalStateException: datapath: bind failed"),
        )
    }
}
