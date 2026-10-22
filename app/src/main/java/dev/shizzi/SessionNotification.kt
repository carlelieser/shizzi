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
            UiStatus.LOADING -> if (isStopping) str(R.string.cleaning_up) else str(R.string.preparing)
            UiStatus.ERROR -> str(R.string.sharing_session_ended)
            UiStatus.READY -> str(R.string.sharing_session_ended)
        }

    private fun connectedTitle(state: SessionUiState): String = when {
        state.isVpnBypassed -> str(R.string.connected_vpn_bypassed)
        state.isVpnBound -> str(R.string.connected_vpn)
        else -> str(R.string.connected)
    }

    private fun bodyFor(state: SessionUiState, isStopping: Boolean): String = when {
        state.lastError.isNotEmpty() -> userFacingError(state.lastError)
        state.status == UiStatus.LOADING -> loadingBody(isStopping)
        state.status == UiStatus.CONNECTED -> connectedBody(state)
        else -> str(R.string.not_sharing)
    }

    private fun userFacingError(raw: String): String = when {
        raw.contains(EXCEPTION_MARKER) -> str(R.string.an_error_occurred_open_the_app_for_details)
        else -> raw
    }

    private fun loadingBody(isStopping: Boolean): String =
        if (isStopping) str(R.string.turning_off_hotspot) else str(R.string.turning_on_hotspot)

    private fun connectedBody(state: SessionUiState): String = when (state.clientCount) {
        0 -> str(R.string.no_devices_connected)
        else -> "${deviceCount(state.clientCount)} · " +
            "$DOWN_ARROW ${Traffic.format(state.traffic.down)} · " +
            "$UP_ARROW ${Traffic.format(state.traffic.up)}"
    }

    private fun deviceCount(clients: Int): String = when (clients) {
        1 -> str(R.string.one_device)
        else -> str(R.string.value_devices, clients)
    }

    fun describeLoss(problem: String?): String = when (problem) {
        null -> str(R.string.privileged_helper_stopped_session_ended) +
            str(R.string.you_can_start_it_again_when_ready)

        else -> str(R.string.the_sharing_session_ended_but_the_hotspot_may, problem) +
            str(R.string.turn_it_off_in_system_settings)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopAction(): Notification.Action = Notification.Action.Builder(
        null,
        str(R.string.action_stop),
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
            str(R.string.tethering_session),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = str(R.string.shown_while_the_tethering_session_is_running) }

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
