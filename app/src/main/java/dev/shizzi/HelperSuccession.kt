package dev.shizzi

import android.os.Process
import java.io.File

/** Pids of the other processes running under [processName], read from their command lines. */
fun otherHelperPids(commandLines: Map<Int, String>, ownPid: Int, processName: String): List<Int> =
    commandLines
        .filter { (pid, commandLine) -> pid != ownPid && commandLine.substringBefore('\u0000').trim() == processName }
        .keys
        .sorted()

/**
 * A helper Shizuku has just replaced spends a few seconds tearing its session
 * down after it is told to exit, and a session started meanwhile can have its
 * downstreams stopped by that teardown. Helpers from before destroy was handled
 * never exit at all. See https://github.com/carlelieser/shizzi/issues/87
 */
class HelperSuccession {

    private val ownPid = Process.myPid()
    private val processName by lazy { File("/proc/self/cmdline").readText().substringBefore('\u0000').trim() }

    /** Waits for older helpers to exit, then ends any that are still running. */
    fun retirePredecessors() {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var others = readOthers()
        while (others.isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MS)
            others = readOthers()
        }
        if (others.isEmpty()) return

        SessionLog.warn("older helper still running after ${WAIT_MS}ms; ending pids $others")
        others.forEach(Process::killProcess)
    }

    private fun readOthers(): List<Int> = otherHelperPids(readCommandLines(), ownPid, processName)

    /** Processes of other users are unreadable and cannot be helpers, so they are skipped. */
    private fun readCommandLines(): Map<Int, String> =
        File("/proc").listFiles().orEmpty()
            .mapNotNull { entry ->
                val pid = entry.name.toIntOrNull() ?: return@mapNotNull null
                val commandLine = runCatching { File(entry, "cmdline").readText() }.getOrNull()
                commandLine?.let { pid to it }
            }
            .toMap()

    private companion object {
        const val WAIT_MS = 6_000L
        const val POLL_MS = 250L
    }
}
