package dev.shizzi

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocalAdbPairingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var discoveryJob: Job? = null
    private var pairingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> cancelPairing()
            ACTION_SUBMIT -> submitPairingCode(intent)
            else -> startDiscovery()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        LocalAdbManager.setPairingState(LocalAdbPairingState.Searching)
        startForeground(NOTIFICATION_ID, searchingNotification())

        discoveryJob = scope.launch {
            runCatching { LocalAdbManager.discoverPairingEndpoint(this@LocalAdbPairingService) }
                .onSuccess {
                    postNotification(serviceFoundNotification())
                }
                .onFailure { failure ->
                    LocalAdbManager.setPairingState(
                        LocalAdbPairingState.Error(
                            failure.message ?: getString(R.string.local_adb_discovery_timeout),
                        ),
                    )
                    showTerminalNotification(
                        getString(R.string.local_adb_pairing_failed_title),
                        failure.message ?: getString(R.string.local_adb_discovery_timeout),
                    )
                }
        }
    }

    private fun submitPairingCode(intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_PAIRING_CODE)?.toString()?.trim().orEmpty()

        if (code.length != 6 || !code.all(Char::isDigit)) {
            postNotification(
                serviceFoundNotification(getString(R.string.local_adb_pairing_code_invalid)),
            )
            return
        }

        if (pairingJob?.isActive == true) return
        startForeground(NOTIFICATION_ID, pairingNotification())
        pairingJob = scope.launch {
            LocalAdbManager.pairDiscovered(this@LocalAdbPairingService, code)
                .onSuccess {
                    showTerminalNotification(
                        getString(R.string.local_adb_ready_notification_title),
                        getString(R.string.local_adb_ready_notification_body),
                    )
                }
                .onFailure { failure ->
                    showTerminalNotification(
                        getString(R.string.local_adb_pairing_failed_title),
                        failure.message ?: getString(R.string.local_adb_pairing_failed_body),
                    )
                }
        }
    }

    private fun cancelPairing() {
        discoveryJob?.cancel()
        pairingJob?.cancel()
        LocalAdbManager.clearPendingEndpoint(this)
        LocalAdbManager.setPairingState(LocalAdbPairingState.Canceled)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun searchingNotification(): Notification = baseNotification()
        .setContentTitle(getString(R.string.local_adb_searching_notification_title))
        .setContentText(getString(R.string.local_adb_searching_notification_body))
        .setOngoing(true)
        .addAction(cancelAction())
        .build()

    private fun serviceFoundNotification(error: String? = null): Notification {
        val input = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel(getString(R.string.pairing_code))
            .build()
        val submit = NotificationCompat.Action.Builder(
            R.drawable.ic_tile_tethering,
            getString(R.string.enter_pairing_code),
            servicePendingIntent(ACTION_SUBMIT, REQUEST_SUBMIT, mutable = true),
        ).addRemoteInput(input).build()

        return baseNotification()
            .setContentTitle(getString(R.string.local_adb_found_notification_title))
            .setContentText(error ?: getString(R.string.local_adb_found_notification_body))
            .setOngoing(true)
            .addAction(submit)
            .addAction(cancelAction())
            .build()
    }

    private fun pairingNotification(): Notification = baseNotification()
        .setContentTitle(getString(R.string.local_adb_pairing_notification_title))
        .setContentText(getString(R.string.local_adb_pairing_notification_body))
        .setOngoing(true)
        .addAction(cancelAction())
        .build()

    private fun showTerminalNotification(title: String, body: String) {
        val notification = baseNotification()
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent())
            .build()
        stopForeground(STOP_FOREGROUND_DETACH)
        postNotification(notification)
        stopSelf()
    }

    /**
     * Posts a notification only while the POST_NOTIFICATIONS permission is still
     * granted. Pairing cannot start before the permission is granted, but Android
     * lets the user revoke it at any time, and notify() would throw a
     * SecurityException on API 33+ in that case.
     */
    private fun postNotification(notification: Notification) {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            SessionLog.warn("skipping pairing notification: POST_NOTIFICATIONS revoked mid-pairing")
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun baseNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_tile_tethering)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(appPendingIntent())

    private fun cancelAction() = NotificationCompat.Action.Builder(
        R.drawable.ic_tile_tethering,
        getString(R.string.action_cancel),
        servicePendingIntent(ACTION_CANCEL, REQUEST_CANCEL),
    ).build()

    private fun appPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String, requestCode: Int, mutable: Boolean = false): PendingIntent {
        val mutability = if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, LocalAdbPairingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.local_adb_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.local_adb_notification_channel_description)
            },
        )
    }

    companion object {
        const val ACTION_START = "dev.shizzi.localadb.START_PAIRING"
        const val ACTION_SUBMIT = "dev.shizzi.localadb.SUBMIT_PAIRING_CODE"
        const val ACTION_CANCEL = "dev.shizzi.localadb.CANCEL_PAIRING"
        private const val CHANNEL_ID = "local_adb_pairing"
        private const val NOTIFICATION_ID = 0x41DB
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val REQUEST_OPEN_APP = 401
        private const val REQUEST_SUBMIT = 402
        private const val REQUEST_CANCEL = 403
    }
}
