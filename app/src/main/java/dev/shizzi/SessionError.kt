package dev.shizzi

// Session errors stay in English in SessionUiState.lastError, which automation receivers read;
// describe() translates the ones the app raises itself when they are shown to the user.
object SessionError {

    const val ENDED_UNEXPECTEDLY = "Session ended unexpectedly — dropping the hotspot…"

    const val SHIZUKU_LOST =
        "Shizuku stopped, so the session ended. The hotspot was turned off. " +
            "Start again when you are ready."

    const val VPN_LOST = "VPN disconnected. Session stopped to keep devices protected."

    private const val TEARDOWN_PREFIX = "The session ended but the hotspot may still be on: "

    private const val TEARDOWN_SUFFIX = " — turn it off in Settings."

    private val known = mapOf(
        ENDED_UNEXPECTEDLY to R.string.session_ended_unexpectedly,
        SHIZUKU_LOST to R.string.notification_shizuku_loss,
        UsbDownstream.NO_USB_CABLE to R.string.usb_cable_required,
        VPN_LOST to R.string.vpn_disconnected_session_stopped,
    )

    fun lost(teardownProblem: String?): String = when (teardownProblem) {
        null -> SHIZUKU_LOST
        else -> "$TEARDOWN_PREFIX$teardownProblem$TEARDOWN_SUFFIX"
    }

    fun describe(raw: String): UiText {
        known[raw]?.let { return UiText.Resource(it) }

        val isTeardownFailure = raw.startsWith(TEARDOWN_PREFIX) && raw.endsWith(TEARDOWN_SUFFIX)
        if (!isTeardownFailure) return UiText.Verbatim(raw)

        val problem = raw.removePrefix(TEARDOWN_PREFIX).removeSuffix(TEARDOWN_SUFFIX)
        return UiText.Resource(R.string.notification_session_loss_problem, listOf(problem))
    }
}
