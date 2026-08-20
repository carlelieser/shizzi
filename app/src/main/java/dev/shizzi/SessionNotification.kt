package dev.shizzi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * The notification a running session posts, split from SessionService so the
 * wording is not interleaved with the concurrency.
 *
 * The register throughout is the user's — a hotspot they are sharing, not a
 * tunnel or an interface name. Those live in the log.
 */
class SessionNotification(private val context: Context) {

    fun build(state: SessionUiState, isStopping: Boolean): Notification {
        createChannel()

        val text = bodyFor(state, isStopping)

        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(titleFor(state, isStopping))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppIntent())
            .setOngoing(state.status != UiStatus.ERROR)
            .addAction(stopAction())
            .build()
    }

    /**
     * [isStopping] disambiguates LOADING, which covers both directions.
     *
     * Never says "protected": with no VPN up, traffic leaves over the physical
     * network exactly as ordinary tethering would, so the word claimed a
     * security property the app does not deliver.
     *
     * ERROR and READY share a title — the session is over either way, and the
     * body carries which.
     */
    private fun titleFor(state: SessionUiState, isStopping: Boolean): String =
        when (state.status) {
            UiStatus.CONNECTED -> connectedTitle(state.isVpnBound)
            UiStatus.LOADING -> if (isStopping) "Cleaning up…" else "Getting ready…"
            UiStatus.ERROR -> "Session ended"
            UiStatus.READY -> "Session ended"
        }

    /**
     * In the title, where a collapsed notification shows it. Its absence goes
     * unstated — announcing what is *not* happening spends the one line
     * available on a non-event.
     */
    private fun connectedTitle(isVpnBound: Boolean): String = when {
        isVpnBound -> "Connected · VPN"
        else -> "Connected"
    }

    /**
     * An error outranks everything: it is the only text here a user may need to
     * act on.
     *
     * Nothing reaches this that was not written to be read. The old fallthrough
     * handed [SessionUiState.detail] over verbatim and put "tethered clients
     * routing through testtun47" — or a raw exception name — on a notification.
     */
    private fun bodyFor(state: SessionUiState, isStopping: Boolean): String = when {
        state.lastError.isNotEmpty() -> userFacingError(state.lastError)
        state.status == UiStatus.LOADING -> loadingBody(isStopping)
        state.status == UiStatus.CONNECTED -> connectedBody(state)
        else -> "Not sharing"
    }

    /**
     * @return [raw] when it was written for a user, else a plain stand-in. A
     *   stack-trace fragment reads as a crash and offers nothing to act on; it
     *   survives in the log either way.
     */
    private fun userFacingError(raw: String): String = when {
        raw.contains(EXCEPTION_MARKER) -> "Something went wrong. Check the app for details."
        else -> raw
    }

    private fun loadingBody(isStopping: Boolean): String =
        if (isStopping) "Turning the hotspot off…" else "Turning the hotspot on…"

    /**
     * How many devices and how much has moved — the only thing on the
     * notification that changes while a session runs.
     *
     * "0 devices · ↓ 0 B" reads as a fault rather than an empty hotspot, so
     * that case is stated plainly. No terminal periods: these are fragments,
     * unlike the full sentences below that tell a user what to do.
     */
    private fun connectedBody(state: SessionUiState): String = when (state.clientCount) {
        0 -> "No devices connected"
        else -> "${deviceCount(state.clientCount)} · " +
            "$DOWN_ARROW ${Traffic.format(state.traffic.down)} · " +
            "$UP_ARROW ${Traffic.format(state.traffic.up)}"
    }

    private fun deviceCount(clients: Int): String = when (clients) {
        1 -> "1 device"
        else -> "$clients devices"
    }

    /** Phrased as what happened and what to do, not as a return value. */
    fun describeLoss(problem: String?): String = when (problem) {
        null -> "Shizuku stopped, so the session ended. The hotspot was turned off. " +
            "Start again when you are ready."

        else -> "The session ended but the hotspot may still be on: $problem — " +
            "turn it off in Settings."
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopAction(): Notification.Action = Notification.Action.Builder(
        null,
        "Stop",
        PendingIntent.getService(
            context,
            1,
            Intent(context, SessionService::class.java).setAction(SessionService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        ),
    ).build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tethering session",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shown while a tethering session is running" }

        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private companion object {
        const val CHANNEL_ID = "tethering-session"

        /** How a Kotlin exception name reads once it reaches a UI string. */
        const val EXCEPTION_MARKER = "Exception"

        /**
         * U+2193/U+2191 render in the notification's text font at the weight of
         * the digits beside them; the emoji variants come from the colour font
         * and sit as blobs against monochrome text.
         */
        const val DOWN_ARROW = "\u2193"
        const val UP_ARROW = "\u2191"
    }
}
