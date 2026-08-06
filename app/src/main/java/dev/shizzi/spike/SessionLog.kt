package dev.shizzi.spike

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How severe an entry is. Rendered, not filtered — the log shows everything. */
enum class LogLevel { INFO, WARN, ERROR }

/** One parsed line, as the log screen renders it. */
data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String,
)

/**
 * The session log, written from two processes into two files.
 *
 * The events worth reading — upstream drift, watchdog strikes, teardown
 * outcomes — happen in the Shizuku shell process, and the app process cannot
 * read logcat without a privileged permission it does not hold. So the shell
 * process writes them to a file instead.
 *
 * It has to be two files rather than one. /data/local/tmp is owned by shell
 * with mode 0771: the shell process can create world-readable files there and
 * the app can read them, but the app cannot write there at all — a device
 * check returned "Permission denied" on the attempt. So each process appends
 * to a file it owns, and [merged] interleaves them for display.
 *
 * The two clocks are the same clock: both processes run on one device, so
 * sorting by timestamp restores the true order.
 */
object SessionLog {

    private const val TAG = "SessionLog"

    /** Written by the shell process, world-readable so the app can read it. */
    const val SHELL_PATH = "/data/local/tmp/shizzi.log"

    /**
     * Cap and reclaim target, applied per file.
     *
     * Truncation keeps the newest half rather than clearing: a log that
     * emptied itself at the cap would discard exactly the entries a user is
     * trying to read after something went wrong.
     */
    private const val MAX_BYTES = 1_000_000L
    private const val KEEP_BYTES = 500_000L

    private val TIMESTAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Splits "<timestamp> <LEVEL> <message>" without splitting the message. */
    private val LINE = Regex("""^(\S+ \S+)\s+(INFO|WARN|ERROR)\s+(.*)$""")

    /**
     * Where this process writes.
     *
     * Set by the app process to its private directory. The shell process
     * leaves it at the world-readable path, which only it can create.
     */
    @Volatile
    private var writePath: String = SHELL_PATH

    /** Both paths to read from, once the app has registered its own. */
    @Volatile
    private var readPaths: List<String> = listOf(SHELL_PATH)

    /**
     * Points this process's writes at [directory], for the app process.
     *
     * Called once from Application.onCreate. Without it the app would try to
     * write to the shell's directory and silently log nothing.
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
     * Appends one entry, and mirrors it to logcat.
     *
     * Never throws: a failed log write must not take down a session teardown,
     * which is the most important thing this records.
     */
    @Synchronized
    fun append(level: LogLevel, message: String) {
        Log.println(priorityOf(level), TAG, message)

        runCatching {
            val stamp = TIMESTAMP.format(Date())
            val file = File(writePath)
            val isNew = !file.exists()

            file.appendText("$stamp ${level.name.padEnd(5)} $message\n")

            // The app process reads the shell's file, so it has to be
            // world-readable. Relying on the spawning process's umask would
            // make that an accident; setting it makes it a guarantee.
            if (isNew) file.setReadable(true, false)

            truncateIfLarge(writePath)
        }.onFailure { failure ->
            Log.w(TAG, "append failed: ${failure.message}")
        }
    }

    /**
     * Both files interleaved, newest first.
     *
     * Sorted by the raw timestamp string, which is safe because the format is
     * fixed-width and lexicographically ordered. Unparseable lines carry an
     * empty timestamp and sort to the end rather than being dropped.
     */
    fun merged(): List<LogEntry> = readPaths
        .flatMap(::readFile)
        .sortedByDescending { it.timestamp }

    fun clear() {
        readPaths.forEach { path ->
            runCatching { File(path).takeIf { it.exists() }?.writeText("") }
                .onFailure { failure -> Log.w(TAG, "clear $path: ${failure.message}") }
        }
    }

    private fun readFile(path: String): List<LogEntry> = runCatching {
        File(path).takeIf { it.exists() }?.readLines()?.mapNotNull(::parse).orEmpty()
    }.getOrElse { failure ->
        // A missing or unreadable shell log is normal before the first
        // session, so this is not surfaced as an error entry.
        Log.w(TAG, "read $path: ${failure.message}")
        emptyList()
    }

    /**
     * A line that does not parse is kept, not dropped.
     *
     * Anything unparseable is still evidence — a stack-trace continuation, or
     * a line from a build that formatted differently. Dropping it would hide
     * exactly the unusual output worth seeing.
     */
    private fun parse(line: String): LogEntry? {
        if (line.isBlank()) return null

        val match = LINE.find(line)
            ?: return LogEntry(timestamp = "", level = LogLevel.INFO, message = line)

        val (timestamp, level, message) = match.destructured
        return LogEntry(timestamp, LogLevel.valueOf(level), message)
    }

    /**
     * Drops the oldest half once a file passes the cap.
     *
     * Reads only the tail rather than the whole file: at the cap that is half
     * a megabyte instead of a full one, and this runs on the thread that just
     * logged.
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
