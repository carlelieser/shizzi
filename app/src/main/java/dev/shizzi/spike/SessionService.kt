package dev.shizzi.spike

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps the app process alive for as long as a session is up.
 *
 * The privileged work happens in the Shizuku shell process, not here. What
 * this owns is the *watching* of it: the death recipient that drops an
 * orphaned hotspot lives in this process, and a cached process can be reclaimed
 * at any moment, taking that recipient with it. A device test showed the shell
 * process dying leaves the hotspot tethered with clients routed over cellular,
 * so the thing that notices has to outlive the UI.
 *
 * The session therefore belongs to the service rather than to a ViewModel: an
 * Activity's lifetime ends when the user swipes the app away, which is exactly
 * when the leak would otherwise go unnoticed.
 */
class SessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private val controller = ProbeController()

    /** The companion's flow, so the UI sees updates from before onCreate ran. */
    private val internalState get() = sessionState

    /**
     * Whether this service is tearing down.
     *
     * LOADING covers both directions, so without this the notification
     * published during teardown reads "Starting…" — the opposite of what is
     * happening, which is what a device test showed on the Stop action.
     */
    private var isStopping = false

    override fun onCreate() {
        super.onCreate()
        controller.onSessionLost = ::handleSessionLost
        liveService = this
    }

    /**
     * Enters the foreground before doing anything else.
     *
     * Android kills a service that does not post its notification within a few
     * seconds of being started, and both paths below can take longer than that
     * — a start restarts the downstream and waits for upstream selection, a
     * stop waits on teardown.
     *
     * The text describes the path being taken rather than assuming a start:
     * posting "Starting…" on the way down told the user the opposite of what
     * was happening.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isStopping = intent?.action == ACTION_STOP
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                status = UiStatus.LOADING,
                text = if (isStopping) "Dropping the hotspot…" else "Bringing the tunnel up…",
                isStopping = isStopping,
            ),
        )

        when {
            isStopping -> stopSession()
            else -> startSession()
        }
        return START_NOT_STICKY
    }

    /** Not a bound service: the UI observes [liveState] instead. */
    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSession() {
        if (internalState.value.isBusy) return
        internalState.update { it.copy(isBusy = true, status = UiStatus.LOADING, lastError = "") }

        scope.launch {
            // Read from the store rather than from UI state: the service can
            // be started from the notification with no screen alive to have
            // populated it.
            val isDebugLogging = settingsStore().settings.first().isDebugLogging

            val outcome = runCatching { controller.start(isDebugLogging) }
            internalState.update { current -> current.applyOutcome(outcome) }
            publishState()
        }
    }

    private fun settingsStore(): SettingsStore =
        (application as SpikeApplication).settingsStore

    /**
     * Stops the session and then itself.
     *
     * stopSelf only after the teardown returns: dropping the foreground state
     * first would let the process be reclaimed mid-teardown, which is the leak
     * this service exists to prevent.
     */
    private fun stopSession() {
        internalState.update { it.copy(isBusy = true, status = UiStatus.LOADING) }

        scope.launch {
            val outcome = runCatching { controller.stop() }
            internalState.update { current -> current.applyOutcome(outcome) }
            stopSelf()
        }
    }

    /**
     * Drops the hotspot left behind by a shell process that died.
     *
     * Arrives on a binder thread. The teardown matters more than the message:
     * without it the hotspot stays up and tethering reverts to cellular with
     * clients still attached.
     */
    private fun handleSessionLost() {
        // Not a user-initiated stop; the ERROR title applies, not "Stopping…".
        isStopping = false
        SessionLog.error("shell process died; recovering any downstream it left up")

        internalState.update {
            it.copy(
                isBusy = false,
                status = UiStatus.ERROR,
                interfaceName = "",
                lastError = "Session ended unexpectedly — dropping the hotspot…",
            )
        }
        publishState()

        scope.launch {
            val problem = runCatching { controller.releaseOrphanedDownstream() }
                .getOrElse { failure -> "teardown failed: ${failure.message}" }

            when (problem) {
                null -> SessionLog.info("orphan recovery: hotspot dropped")
                else -> SessionLog.error("orphan recovery failed: $problem")
            }

            internalState.update { current -> current.copy(lastError = describeLoss(problem)) }
            publishState()
            stopSelf()
        }
    }

    private fun describeLoss(problem: String?): String = when (problem) {
        null -> "Session ended: the Shizuku service stopped. " +
            "The hotspot was turned off. Press Start to reconnect."

        else -> "Session ended and the hotspot may still be on: $problem — " +
            "turn it off in Settings."
    }

    /** Mirrors the current state into the notification. */
    private fun publishState() {
        val state = internalState.value
        val text = when {
            state.lastError.isNotEmpty() -> state.lastError
            state.interfaceName.isNotEmpty() -> "Clients routing through ${state.interfaceName}"
            else -> state.detail
        }

        notificationManager().notify(
            NOTIFICATION_ID,
            buildNotification(state.status, text, isStopping),
        )
    }

    private fun buildNotification(
        status: UiStatus,
        text: String,
        isStopping: Boolean = false,
    ): Notification {
        createChannel()

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(titleFor(status, isStopping))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppIntent())
            .setOngoing(status != UiStatus.ERROR)
            .addAction(stopAction())
            .build()
    }

    /** [isStopping] disambiguates LOADING, which covers both directions. */
    private fun titleFor(status: UiStatus, isStopping: Boolean): String = when (status) {
        UiStatus.CONNECTED -> "Tethering protected"
        UiStatus.LOADING -> if (isStopping) "Stopping…" else "Starting…"
        UiStatus.ERROR -> "Tethering stopped"
        UiStatus.READY -> "Ready"
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopAction(): Notification.Action = Notification.Action.Builder(
        null,
        "Stop",
        PendingIntent.getService(
            this,
            1,
            Intent(this, SessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        ),
    ).build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tethering session",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shown while a protected tethering session is active" }

        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        controller.onSessionLost = null
        controller.unbind()
        scope.cancel()
        liveService = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "tethering-session"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "dev.shizzi.spike.STOP_SESSION"

        /**
         * Session state, for the UI to observe.
         *
         * Exists whether or not a service is running, and deliberately so: the
         * UI subscribes the instant the user presses Start, while
         * startForegroundService is still asynchronous and onCreate has not
         * run. A flow that only appeared with the service left the screen
         * subscribing to nothing and stuck on LOADING while the session was
         * already ACTIVE.
         */
        private val sessionState = MutableStateFlow(SpikeUiState())

        val liveState: StateFlow<SpikeUiState> = sessionState.asStateFlow()

        private var liveService: SessionService? = null

        /** Whether a session is currently running. */
        val isRunning: Boolean get() = liveService != null

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SessionService::class.java))
        }

        fun stop(context: Context) {
            context.startForegroundService(
                Intent(context, SessionService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
