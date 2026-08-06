package dev.shizzi

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val controller = TetherClient()

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

    /**
     * The in-flight start, so a stop can interrupt it.
     *
     * Without this a cancel runs the teardown concurrently with the start it
     * means to abandon, and the two interleave: a device test showed the
     * downstream confirmed down at 22:34:06 and the start bringing a *new* TUN
     * up at 22:34:07, one second later. Teardown has to be the last thing that
     * happens, so the start is cancelled and awaited before it runs.
     */
    private var startJob: Job? = null

    /**
     * Counts intents, so a superseded one cannot publish state.
     *
     * A cancelled start is still inside a blocking binder call and will return
     * a result after the user has already been shown an idle screen. Folding
     * that late result in would flip the screen back to an error the user
     * cancelled their way out of, so each attempt captures the generation it
     * belongs to and drops its result if it is no longer the current one.
     */
    private var generation = 0

    /**
     * Serialises privileged work, so a start and a teardown never overlap.
     *
     * Cancelling returns the screen to idle immediately and drains the teardown
     * behind it, which means the user can press Start again while the previous
     * teardown is still running. Without this the new session's setup and the
     * old session's teardown interleave and the teardown sweeps away a TUN that
     * belongs to the session the user is currently waiting on.
     */
    private val sessionLock = Mutex()

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
        val attempt = ++generation
        internalState.update {
            it.copy(isBusy = true, status = UiStatus.LOADING, lastError = "", detail = "")
        }

        startJob = scope.launch {
            // Read from the store rather than from UI state: the service can
            // be started from the notification with no screen alive to have
            // populated it.
            val isDebugLogging = settingsStore().settings.first().isDebugLogging

            // Waits out a teardown still draining from a previous cancel, so
            // that teardown cannot sweep away what this start is about to build.
            val outcome = sessionLock.withLock {
                if (attempt != generation) return@launch
                runCatching { controller.start(isDebugLogging) }
            }
            if (attempt != generation) return@launch

            internalState.update { current -> current.applyOutcome(outcome) }
            publishState()
        }
    }

    private fun settingsStore(): SettingsStore =
        (application as App).settingsStore

    /**
     * Stops the session and then itself.
     *
     * The screen goes idle immediately rather than when the teardown returns.
     * Stopping is not a request that can be refused — the session is going away
     * whatever the privileged side reports — so making the user watch a spinner
     * until a binder call completes shows them a decision that has already been
     * made. The teardown drains behind the idle screen.
     *
     * Bumping the generation first is what makes that safe: an in-flight start
     * is still inside a blocking call and will return afterward, and without it
     * that late result would repaint the screen the user just cleared.
     *
     * stopSelf only after the teardown returns: dropping the foreground state
     * first would let the process be reclaimed mid-teardown, which is the leak
     * this service exists to prevent.
     */
    private fun stopSession() {
        val stopped = ++generation

        // Captured here, not inside the coroutine. Reading the field later
        // reads whatever start is current *then*: a Start/Cancel/Start burst
        // had the cancel's teardown join the second start and tear down the
        // session the user had just asked for.
        val abandoned = startJob
        startJob = null

        internalState.update {
            it.asStopped()
        }

        scope.launch {
            // Abandon an in-flight start first, and wait for it to actually
            // leave. Tearing down alongside a running start lets the start
            // create a TUN after the teardown has already swept for one.
            abandoned?.cancelAndJoin()

            // The outcome is logged rather than rendered: the screen is already
            // idle, and a teardown that reports trouble needs the log, not a
            // toast contradicting a state the user asked for.
            sessionLock.withLock {
                runCatching { controller.stop() }
                    .onFailure { SessionLog.error("teardown failed: ${it.message}") }

                // The daemon holds the TUN's file descriptor, so the interface
                // survives in the kernel until the process holding it exits.
                // Terminating it is what actually destroys the TUN.
                //
                // Unconditional, and safe because a start binds inside this
                // same lock: anything newer is still waiting here and will bind
                // a fresh daemon once this releases. Skipping termination when a
                // newer request had arrived is what left an orphan behind per
                // stop-then-start, each one holding a TUN that tethering could
                // later select and fail the next session against.
                controller.unbindAndStopDaemon()
            }

            // Only if nothing has been asked of the service since. A start that
            // arrived while this teardown was draining is the current
            // generation, and stopping the service would kill it.
            if (stopped == generation) stopSelf()
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
        private const val ACTION_STOP = "dev.shizzi.STOP_SESSION"

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
        private val sessionState = MutableStateFlow(SessionUiState())

        val liveState: StateFlow<SessionUiState> = sessionState.asStateFlow()

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
