package dev.shizzi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Builds the notification a running session posts.
 *
 * Split from SessionService so the wording lives in one readable place rather
 * than interleaved with the concurrency the service exists to manage.
 *
 * The register throughout is the user's: a hotspot they are sharing, not a
 * tunnel, a test network, or an interface name. Those are ours, and they are
 * already in the log for anyone filing a bug.
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
     * Deliberately no longer says "protected". That claimed a security property
     * the app does not deliver: with no VPN up, traffic leaves over the physical
     * network exactly as ordinary tethering would, and the only thing the word
     * ever referred to was an implementation detail.
     *
     * ERROR and READY share a title because the fact is the same in both — the
     * session is over. Which of the two it was is what the body carries.
     */
    private fun titleFor(state: SessionUiState, isStopping: Boolean): String =
        when (state.status) {
            UiStatus.CONNECTED -> connectedTitle(state.isVpnBound)
            UiStatus.LOADING -> if (isStopping) "Cleaning up…" else "Getting ready…"
            UiStatus.ERROR -> "Session ended"
            UiStatus.READY -> "Session ended"
        }

    /**
     * The VPN fact rides in the title, where a collapsed notification shows it.
     *
     * Its absence is not stated. "Connected" alone is the honest reading of
     * ordinary tethering, and a notification that announces what is *not*
     * happening spends the one line it has on a non-event.
     */
    private fun connectedTitle(isVpnBound: Boolean): String = when {
        isVpnBound -> "Connected · VPN"
        else -> "Connected"
    }

    /**
     * What the notification says under its title.
     *
     * An error outranks everything: it is the only text here a user may need to
     * act on.
     *
     * Nothing reaches this that was not written for a user to read. The former
     * fallthrough handed [SessionUiState.detail] over verbatim, which put
     * "tethered clients routing through testtun47" — and, on a failed start, a
     * raw exception class name — on a notification. Both are diagnostics; they
     * stay in the log and the in-app toast, which is where someone filing a bug
     * looks for them.
     */
    private fun bodyFor(state: SessionUiState, isStopping: Boolean): String = when {
        state.lastError.isNotEmpty() -> userFacingError(state.lastError)
        state.status == UiStatus.LOADING -> loadingBody(isStopping)
        state.status == UiStatus.CONNECTED -> connectedBody(state)
        else -> "Not sharing"
    }

    /**
     * @return [raw] when it was written for a user, else a plain stand-in.
     *
     * A stack-trace fragment tells the user nothing they can act on and reads
     * as a crash. The detail survives in the log either way, so the notification
     * loses nothing by declining to show it.
     */
    private fun userFacingError(raw: String): String = when {
        raw.contains(EXCEPTION_MARKER) -> "Something went wrong. Check the app for details."
        else -> raw
    }

    private fun loadingBody(isStopping: Boolean): String =
        if (isStopping) "Turning the hotspot off…" else "Turning the hotspot on…"

    /**
     * What is actually happening: how many devices, and how much has moved.
     *
     * The body carries information rather than a longer restatement of the
     * title. It is also the half a user keeps looking at, since the counts are
     * the only thing on the notification that changes while a session runs.
     *
     * Before any device connects there is nothing to count, and "0 devices ·
     * ↓ 0 B" reads as a fault rather than as an empty hotspot, so that case is
     * stated plainly instead.
     *
     * No terminal period on any of these: they are fragments, and a stop after
     * a phrase that is not a sentence reads as a typo. The full sentences below
     * — the ones telling a user what to do — keep theirs.
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
         * Plain arrows, not the emoji ones.
         *
         * U+2193/U+2191 render in the notification's own text font at the
         * weight of the digits beside them. The emoji variants would be
         * substituted from the colour font and sit as coloured blobs against
         * monochrome text.
         */
        const val DOWN_ARROW = "\u2193"
        const val UP_ARROW = "\u2191"
    }
}
