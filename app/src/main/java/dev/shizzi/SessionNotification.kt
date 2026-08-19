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
            .setContentTitle(titleFor(state.status, isStopping))
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
     * ever referred to was an implementation detail. Whether traffic is actually
     * tunnelled is now stated in the body, and only when it is true.
     *
     * ERROR and READY share a title because the fact is the same in both — the
     * session is over. Which of the two it was is what the body carries.
     */
    private fun titleFor(status: UiStatus, isStopping: Boolean): String = when (status) {
        UiStatus.CONNECTED -> "Sharing this connection"
        UiStatus.LOADING -> if (isStopping) "Ending the session…" else "Starting the session…"
        UiStatus.ERROR -> "Session ended"
        UiStatus.READY -> "Session ended"
    }

    /**
     * What the notification says under its title.
     *
     * An error outranks everything: it is the only text here a user may need to
     * act on. Otherwise a live session describes the path its clients' traffic
     * takes, which is the one thing the notification can say that the screen
     * cannot, since this is what stays visible with the app closed.
     */
    private fun bodyFor(state: SessionUiState, isStopping: Boolean): String = when {
        state.lastError.isNotEmpty() -> state.lastError
        state.status == UiStatus.LOADING -> loadingBody(isStopping)
        state.status == UiStatus.CONNECTED -> connectedBody(state.isVpnBound)
        else -> state.detail
    }

    private fun loadingBody(isStopping: Boolean): String =
        if (isStopping) "Turning the hotspot off…" else "Turning the hotspot on…"

    /**
     * The absence of a VPN is stated, not merely left unsaid.
     *
     * Saying nothing is what lets someone who normally runs a VPN assume it is
     * carrying their clients' traffic when it is not — the same silent fallback
     * the watchdogs exist to prevent, in wording rather than in routing. Naming
     * it costs one clause.
     */
    private fun connectedBody(isVpnBound: Boolean): String = when {
        isVpnBound -> "Connected devices are going out through your VPN."
        else -> "Connected devices are going out through this phone's network, not a VPN."
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
    }
}
