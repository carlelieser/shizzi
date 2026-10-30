package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckTimerTest {

    private val interval = 300L

    private fun elapsedMs(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun `runs a full check once the interval passes`() {
        val timer = CheckTimer(interval)

        assertEquals(CheckKind.FULL, timer.await())
    }

    @Test
    fun `a wake runs a downstream check without waiting`() {
        val timer = CheckTimer(interval)
        timer.wake()

        val waited = elapsedMs { assertEquals(CheckKind.DOWNSTREAM, timer.await()) }

        assertTrue("waited ${waited}ms", waited < interval / 2)
    }

    @Test
    fun `wakes during a check fold into one`() {
        val timer = CheckTimer(interval)
        repeat(3) { timer.wake() }

        assertEquals(CheckKind.DOWNSTREAM, timer.await())
        assertEquals(CheckKind.FULL, timer.await())
    }

    @Test
    fun `a wake does not push the full check back`() {
        val timer = CheckTimer(interval)
        Thread {
            Thread.sleep(interval * 2 / 3)
            timer.wake()
        }.start()

        val waited = elapsedMs {
            assertEquals(CheckKind.DOWNSTREAM, timer.await())
            assertEquals(CheckKind.FULL, timer.await())
        }

        assertTrue("waited ${waited}ms", waited < interval * 3 / 2)
    }
}
