package dev.shizzi

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rendered, not filtered — the log shows everything. */
enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String,
)

/**
 * The session log, written from two processes into two files.
 *
 * The events worth reading happen in the shell process, and the app cannot read
 * logcat without a permission it does not hold — so the shell writes to a file.
 * It has to be two files: /data/local/tmp is shell-owned mode 0771, so the app
 * can read what the shell creates there but cannot write it at all.
 *
 * Both processes share a clock, so sorting [merged] by timestamp restores the
 * true order.
 */
object SessionLog {

    private const val TAG = "SessionLog"

    /** Written by the shell process, world-readable so the app can read it. */
    const val SHELL_PATH = "/data/local/tmp/shizzi.log"

    /**
     * Cap and reclaim target, per file. Keeps the newest half rather than
     * emptying, which would discard exactly what a user reads after a failure.
     */
    private const val MAX_BYTES = 1_000_000L
    private const val KEEP_BYTES = 500_000L

    private val TIMESTAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Splits "<timestamp> <LEVEL> <message>" without splitting the message. */
    private val LINE = Regex("""^(\S+ \S+)\s+(INFO|WARN|ERROR)\s+(.*)$""")

    /** The shell leaves this at the world-readable path only it can create. */
    @Volatile
    private var writePath: String = SHELL_PATH

    @Volatile
    private var readPaths: List<String> = listOf(SHELL_PATH)

    /**
     * Gates [append] only. Reading stays unconditional — turning logging off
     * should stop new entries, not hide the ones already written.
     */
    @Volatile
    private var isEnabled: Boolean = true

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    /**
     * Points the app process's writes at its own storage. Called once from
     * Application.onCreate; without it the app logs nothing, silently.
     */
    fun useAppStorage(directory: File) {
        val appPath = File(directory, "session.log").absolutePath
        writePath = appPath
        readPaths = listOf(SHELL_PATH, appPath)
    }

    fun info(message: String) = append(LogLevel.INFO, message)
    fun warn(message: String) = append(LogLevel.WARN, message)
    fun error(message: String) = append(LogLevel.ERROR, message)

    /**
     * Mirrors to logcat before the enabled check: that channel is the
     * developer's and costs the user nothing, while the setting governs what
     * lands on their device.
     *
     * Never throws — a failed write must not take down a teardown, which is
     * the most important thing this records.
     */
    @Synchronized
    fun append(level: LogLevel, message: String) {
        Log.println(priorityOf(level), TAG, message)
        if (!isEnabled) return

        runCatching {
            val stamp = TIMESTAMP.format(Date())
            val file = File(writePath)
            val isNew = !file.exists()

            file.appendText("$stamp ${level.name.padEnd(5)} $message\n")

            // The app reads the shell's file. Relying on the spawning
            // process's umask would make that an accident.
            if (isNew) file.setReadable(true, false)

            truncateIfLarge(writePath)
        }.onFailure { failure ->
            Log.w(TAG, "append failed: ${failure.message}")
        }
    }

    /**
     * Both files interleaved, newest first. Sorting the raw string works
     * because the format is fixed-width; unparseable lines carry an empty
     * timestamp and sort to the end rather than being dropped.
     */
    fun merged(): List<LogEntry> = readPaths
        .flatMap(::readFile)
        .sortedByDescending { it.timestamp }

    /**
     * Empties every file this process can write; the other half is
     * [ITetherService.clearLog]'s job.
     *
     * Truncates rather than deletes so the shell's file keeps the readable bit
     * the app depends on — recreating it would only restore that if the next
     * append came from the shell. Failures are logged, not raised: the caller
     * cannot act on them, and the half that could be cleared was.
     */
    fun clear() {
        readPaths.forEach { path ->
            runCatching { File(path).takeIf { it.exists() }?.writeText("") }
                .onFailure { failure -> Log.w(TAG, "clear $path: ${failure.message}") }
        }
    }

    private fun readFile(path: String): List<LogEntry> = runCatching {
        File(path).takeIf { it.exists() }?.readLines()?.mapNotNull(::parse).orEmpty()
    }.getOrElse { failure ->
        // Normal before the first session, so not surfaced as an error entry.
        Log.w(TAG, "read $path: ${failure.message}")
        emptyList()
    }

    /**
     * Keeps unparseable lines: a stack-trace continuation or a line from an
     * older format is still evidence, and often the unusual output worth
     * seeing.
     */
    private fun parse(line: String): LogEntry? {
        if (line.isBlank()) return null

        val match = LINE.find(line)
            ?: return LogEntry(timestamp = "", level = LogLevel.INFO, message = line)

        val (timestamp, level, message) = match.destructured
        return LogEntry(timestamp, LogLevel.valueOf(level), message)
    }

    /**
     * Drops the oldest half once a file passes the cap. Reads only the tail —
     * this runs on the thread that just logged.
     */
    private fun truncateIfLarge(path: String) {
        val file = File(path)
        if (file.length() <= MAX_BYTES) return

        val kept = RandomAccessFile(file, "r").use { reader ->
            reader.seek(file.length() - KEEP_BYTES)

            // The seek lands mid-line; discard the partial one.
            reader.readLine()

            val remaining = ByteArray((reader.length() - reader.filePointer).toInt())
            reader.readFully(remaining)
            remaining
        }

        file.writeBytes(kept)
    }

    private fun priorityOf(level: LogLevel): Int = when (level) {
        LogLevel.INFO -> Log.INFO
        LogLevel.WARN -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
    }
}
