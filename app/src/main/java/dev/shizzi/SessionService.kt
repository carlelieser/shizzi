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
 * The privileged work happens in the shell process; what this owns is the
 * *watching* of it. The death recipient that drops an orphaned hotspot lives
 * here, and a cached process can be reclaimed at any moment along with it — the
 * shell dying leaves the hotspot tethered with clients on cellular.
 *
 * Hence a service and not a ViewModel: an Activity's lifetime ends when the
 * user swipes the app away, exactly when the leak would go unnoticed.
 */
class SessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private val controller = TetherClient()
    private val notification by lazy { SessionNotification(this) }
    private val statusPoller = SessionStatusPoller(scope, controller)

    /** The companion's flow, so the UI sees updates from before onCreate ran. */
    private val internalState get() = sessionState

    /**
     * LOADING covers both directions, so without this the notification reads
     * "Starting…" throughout a teardown — the opposite of what is happening.
     */
    private var isStopping = false

    /**
     * The in-flight start, so a stop can interrupt it. Cancelling without
     * awaiting lets the two interleave: the downstream was confirmed down at
     * 22:34:06 and the start brought a *new* TUN up at 22:34:07.
     */
    private var startJob: Job? = null

    /**
     * Counts intents, so a superseded one cannot publish state. A cancelled
     * start is still inside a blocking binder call and returns after the user
     * has been shown an idle screen; folding that in flips them back to an
     * error they cancelled their way out of.
     */
    private var generation = 0

    /**
     * Serialises privileged work. Cancelling idles the screen and drains the
     * teardown behind it, so the user can press Start while the old teardown
     * runs — which would otherwise sweep away the TUN of the session they are
     * waiting on.
     */
    private val sessionLock = Mutex()

    override fun onCreate() {
        super.onCreate()
        controller.onSessionLost = ::handleSessionLost
        liveService = this
    }

    /**
     * Foregrounds before anything else: Android kills a service that has not
     * posted its notification within a few seconds, and both paths below run
     * longer than that.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isStopping = intent?.action == ACTION_STOP
        startForeground(
            NOTIFICATION_ID,
            notification.build(
                SessionUiState(status = UiStatus.LOADING),
                isStopping,
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
            // From the store, not UI state: the notification can start this
            // with no screen alive to have populated it.
            val isLogging = settingsStore().settings.first().isLogging

            // Waits out a teardown still draining, which would otherwise sweep
            // away what this start is about to build.
            val outcome = sessionLock.withLock {
                if (attempt != generation) return@launch
                runCatching { controller.start(isLogging) }
            }
            if (attempt != generation) return@launch

            internalState.update { current -> current.applyOutcome(outcome) }
            publishState()
            followStatus()
        }
    }

    private fun followStatus() = statusPoller.follow(
        isConnected = { internalState.value.status == UiStatus.CONNECTED },
        onStatus = { outcome ->
            internalState.update { current -> current.applyOutcome(outcome) }
            publishState()
        },
    )

    private fun settingsStore(): SettingsStore =
        (application as App).settingsStore

    /**
     * The screen goes idle immediately, not when teardown returns: stopping is
     * not a request that can be refused, so a spinner would only make the user
     * watch a decision already made. Teardown drains behind it.
     *
     * Bumping the generation first is what makes that safe against the late
     * result of an in-flight start. stopSelf waits for teardown — dropping the
     * foreground state early lets the process be reclaimed mid-teardown, the
     * leak this service exists to prevent.
     */
    private fun stopSession() {
        val stopped = ++generation

        // Captured here, not inside the coroutine, which would read whatever
        // start is current *then*: a Start/Cancel/Start burst had the cancel's
        // teardown join the second start and tear down what the user just asked
        // for.
        val abandoned = startJob
        startJob = null

        statusPoller.stop()

        internalState.update {
            it.asStopped()
        }
        // Otherwise the notification holds onStartCommand's text for the whole
        // teardown, which outlasts a cancelAndJoin plus a privileged release.
        publishState()

        scope.launch {
            // Wait for the abandoned start to actually leave: tearing down
            // alongside one lets it create a TUN after the sweep.
            abandoned?.cancelAndJoin()

            // Logged rather than rendered — the screen is already idle, and a
            // toast here would contradict the state the user asked for.
            sessionLock.withLock {
                runCatching { controller.stop() }
                    .onFailure { SessionLog.error("teardown failed: ${it.message}") }

                // The daemon holds the TUN's fd, so terminating it is what
                // actually destroys the interface.
                //
                // Unconditional, and safe because a start binds inside this
                // same lock: anything newer waits here and binds a fresh daemon
                // after. Skipping this when a newer request had arrived left an
                // orphan per stop-then-start, each holding a TUN that tethering
                // could later select and fail the next session against.
                controller.unbindAndStopDaemon()
            }

            // A start that arrived mid-teardown is the current generation, and
            // stopping the service would kill it.
            if (stopped == generation) stopSelf()
        }
    }

    /**
     * Drops the hotspot left by a dead shell process — without it the hotspot
     * stays up and tethering reverts to cellular with clients attached.
     * Arrives on a binder thread.
     */
    private fun handleSessionLost() {
        statusPoller.stop()

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

            internalState.update { current -> current.copy(lastError = notification.describeLoss(problem)) }
            publishState()
            stopSelf()
        }
    }

    /** Mirrors the current state into the notification. */
    private fun publishState() {
        notificationManager().notify(
            NOTIFICATION_ID,
            notification.build(internalState.value, isStopping),
        )
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
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "dev.shizzi.STOP_SESSION"

        /**
         * Exists whether or not a service is running: the UI subscribes the
         * instant Start is pressed, while startForegroundService is still
         * asynchronous. A flow that appeared with the service left the screen
         * subscribed to nothing and stuck on LOADING through an ACTIVE session.
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
