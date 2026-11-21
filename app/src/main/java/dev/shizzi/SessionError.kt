package dev.shizzi

// Session errors stay in English in SessionUiState.lastError, which automation receivers read;
// describe() maps them to translated text only when they are shown to the user.
object SessionError {

    const val VPN_LOST = "VPN disconnected. Session stopped to keep devices protected."

    const val VPN_REQUIRED = "followVpn: vpn mode always requires an active VPN, none is connected"

    const val DOWNSTREAM_NOT_STARTED = "restartDownstream:"

    const val UPSTREAM_NOT_VERIFIED = "verifyUpstream:"

    const val TEARDOWN_INCOMPLETE = "; teardown incomplete: "

    private const val UPSTREAM_LOST = "upstream "

    private val DOWNSTREAM_LEFT_UP = listOf(
        "downstream did not release",
        "downstream still tethered",
        "dumpsys tethering timed out",
        "could not verify downstream",
    )

    private val known = mapOf(
        WiredDownstream.NO_USB_CABLE to R.string.usb_cable_required,
        WiredDownstream.NO_ETHERNET to R.string.ethernet_connection_required,
        WiredDownstream.NO_WIRED to R.string.wired_connection_required,
        VPN_LOST to R.string.vpn_disconnected_session_stopped,
        SessionEnd.RESTART_FAILED.message(isHotspotPossiblyOn = false) to
            R.string.session_end_restart_failed,
        SessionEnd.RESTART_FAILED.message(isHotspotPossiblyOn = true) to
            R.string.session_end_restart_failed_hotspot_on,
        SessionEnd.SHIZUKU_STOPPED.message(isHotspotPossiblyOn = false) to
            R.string.session_end_shizuku_stopped,
        SessionEnd.SHIZUKU_STOPPED.message(isHotspotPossiblyOn = true) to
            R.string.session_end_shizuku_stopped_hotspot_on,
    )

    fun describe(raw: String): UiText {
        val problem = raw.substringBefore(TEARDOWN_INCOMPLETE)
        val summary = UiText.Resource(summaryOf(problem))

        return when (problem) {
            raw -> summary
            else -> UiText.Resource(R.string.session_error_hotspot_may_be_on, listOf(summary))
        }
    }

    private fun summaryOf(problem: String): Int = known[problem] ?: when {
        VPN_REQUIRED in problem -> R.string.session_error_vpn_required
        DOWNSTREAM_NOT_STARTED in problem -> R.string.session_error_hotspot_not_started
        UPSTREAM_NOT_VERIFIED in problem -> R.string.session_error_upstream_not_verified
        problem.startsWith(UPSTREAM_LOST) -> R.string.session_error_upstream_lost
        DOWNSTREAM_LEFT_UP.any(problem::startsWith) -> R.string.session_error_teardown_incomplete
        else -> R.string.session_error_generic
    }
}
