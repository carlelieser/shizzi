package dev.shizzi

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String,
)

object SessionLog {

    private const val TAG = "SessionLog"

    const val SHELL_PATH = "/data/local/tmp/shizzi.log"

    private const val MAX_BYTES = 1_000_000L
    private const val KEEP_BYTES = 500_000L

    private val TIMESTAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val LINE = Regex("""^(\S+ \S+)\s+(INFO|WARN|ERROR)\s+(.*)$""")

    @Volatile
    private var writePath: String = SHELL_PATH

    @Volatile
    private var readPaths: List<String> = listOf(SHELL_PATH)

    @Volatile
    private var isEnabled: Boolean = true

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun useAppStorage(directory: File) {
        val appPath = File(directory, "session.log").absolutePath
        writePath = appPath
        readPaths = listOf(SHELL_PATH, appPath)
    }

    fun info(message: String) = append(LogLevel.INFO, message)
    fun warn(message: String) = append(LogLevel.WARN, message)
    fun error(message: String) = append(LogLevel.ERROR, message)

    @Synchronized
    fun append(level: LogLevel, message: String) {
        Log.println(priorityOf(level), TAG, message)
        if (!isEnabled) return

        runCatching {
            val stamp = TIMESTAMP.format(Date())
            val file = File(writePath)
            val isNew = !file.exists()

            file.appendText("$stamp ${level.name.padEnd(5)} $message\n")

            if (isNew) file.setReadable(true, false)

            truncateIfLarge(writePath)
        }.onFailure { failure ->
            Log.w(TAG, "append failed: ${failure.message}")
        }
    }

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

        Log.w(TAG, "read $path: ${failure.message}")
        emptyList()
    }

    private fun parse(line: String): LogEntry? {
        if (line.isBlank()) return null

        val match = LINE.find(line)
            ?: return LogEntry(timestamp = "", level = LogLevel.INFO, message = line)

        val (timestamp, level, message) = match.destructured
        return LogEntry(timestamp, LogLevel.valueOf(level), message)
    }

    private fun truncateIfLarge(path: String) {
        val file = File(path)
        if (file.length() <= MAX_BYTES) return

        val kept = RandomAccessFile(file, "r").use { reader ->
            reader.seek(file.length() - KEEP_BYTES)

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
