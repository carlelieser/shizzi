package dev.shizzi

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

enum class CheckKind { FULL, DOWNSTREAM }

/**
 * Paces watchdog checks: a full check every [intervalMs], and a downstream-only
 * check whenever [wake] is called in between. Wakes that arrive during a check
 * fold into one, and they never push the next full check back.
 */
class CheckTimer(
    private val intervalMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val wakeups = Semaphore(0)
    private var fullCheckAt = clock() + intervalMs

    fun wake() = wakeups.release()

    /** Blocks until the next check is due. Throws InterruptedException if interrupted. */
    fun await(): CheckKind {
        val remaining = fullCheckAt - clock()
        val isWoken = remaining > 0 && wakeups.tryAcquire(remaining, TimeUnit.MILLISECONDS)
        wakeups.drainPermits()

        if (isWoken && clock() < fullCheckAt) return CheckKind.DOWNSTREAM

        fullCheckAt = clock() + intervalMs
        return CheckKind.FULL
    }
}
