package dev.shizzi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

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

    private fun titleFor(state: SessionUiState, isStopping: Boolean): String =
        when (state.status) {
            UiStatus.CONNECTED -> connectedTitle(state.isVpnBound)
            UiStatus.LOADING -> if (isStopping) "Cleaning up…" else "Getting ready…"
            UiStatus.ERROR -> "Session ended"
            UiStatus.READY -> "Session ended"
        }

    private fun connectedTitle(isVpnBound: Boolean): String = when {
        isVpnBound -> "Connected · VPN"
        else -> "Connected"
    }

    private fun bodyFor(state: SessionUiState, isStopping: Boolean): String = when {
        state.lastError.isNotEmpty() -> userFacingError(state.lastError)
        state.status == UiStatus.LOADING -> loadingBody(isStopping)
        state.status == UiStatus.CONNECTED -> connectedBody(state)
        else -> "Not sharing"
    }

    private fun userFacingError(raw: String): String = when {
        raw.contains(EXCEPTION_MARKER) -> "Something went wrong. Check the app for details."
        else -> raw
    }

    private fun loadingBody(isStopping: Boolean): String =
        if (isStopping) "Turning the hotspot off…" else "Turning the hotspot on…"

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

        const val EXCEPTION_MARKER = "Exception"

        const val DOWN_ARROW = "\u2193"
        const val UP_ARROW = "\u2191"
    }
}
