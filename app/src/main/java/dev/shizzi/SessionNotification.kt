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
            UiStatus.CONNECTED -> connectedTitle(state)
            UiStatus.LOADING -> if (isStopping) L10n.string(R.string.notification_cleaning_up) else L10n.string(R.string.notification_getting_ready)
            UiStatus.ERROR -> L10n.string(R.string.notification_session_ended)
            UiStatus.READY -> L10n.string(R.string.notification_session_ended)
        }

    private fun connectedTitle(state: SessionUiState): String = when {
        state.isVpnBypassed -> L10n.string(R.string.notification_connected_vpn_ignored)
        state.isVpnBound -> L10n.string(R.string.notification_connected_vpn)
        else -> L10n.string(R.string.status_connected)
    }

    private fun bodyFor(state: SessionUiState, isStopping: Boolean): String = when {
        state.lastError.isNotEmpty() -> userFacingError(state.lastError)
        state.status == UiStatus.LOADING -> loadingBody(isStopping)
        state.status == UiStatus.CONNECTED -> connectedBody(state)
        else -> L10n.string(R.string.notification_not_sharing)
    }

    private fun userFacingError(raw: String): String = when {
        raw.contains(EXCEPTION_MARKER) -> L10n.string(R.string.notification_generic_error)
        else -> raw
    }

    private fun loadingBody(isStopping: Boolean): String =
        if (isStopping) L10n.string(R.string.notification_turning_hotspot_off) else L10n.string(R.string.notification_turning_hotspot_on)

    private fun connectedBody(state: SessionUiState): String = when (state.clientCount) {
        0 -> L10n.string(R.string.notification_no_devices)
        else -> "${deviceCount(state.clientCount)} · " +
            "$DOWN_ARROW ${Traffic.format(state.traffic.down)} · " +
            "$UP_ARROW ${Traffic.format(state.traffic.up)}"
    }

    private fun deviceCount(clients: Int): String = when (clients) {
        else -> L10n.quantity(R.plurals.notification_device_count, clients, clients)
    }

    fun describeLoss(problem: String?): String = when (problem) {
        null -> L10n.string(R.string.notification_shizuku_loss)

        else -> L10n.string(R.string.notification_session_loss_problem, problem)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopAction(): Notification.Action = Notification.Action.Builder(
        null,
        L10n.string(R.string.stop),
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
            L10n.string(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = L10n.string(R.string.notification_channel_description) }

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
