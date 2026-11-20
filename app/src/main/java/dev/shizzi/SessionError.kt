package dev.shizzi

// Session errors stay in English in SessionUiState.lastError, which automation receivers read;
// describe() translates the ones the app raises itself when they are shown to the user.
object SessionError {

    const val VPN_LOST = "VPN disconnected. Session stopped to keep devices protected."

    private val known = mapOf(
        UsbDownstream.NO_USB_CABLE to R.string.usb_cable_required,
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

    fun describe(raw: String): UiText = known[raw]?.let { UiText.Resource(it) } ?: UiText.Verbatim(raw)
}
