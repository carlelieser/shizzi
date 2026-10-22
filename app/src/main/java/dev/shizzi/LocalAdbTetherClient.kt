package dev.shizzi

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalAdbTetherClient(private val context: Context) : PrivilegedClient {
    override val backend = PrivilegeBackendType.LOCAL_ADB
    override var onSessionLost: (() -> Unit)? = null

    private val sequence = AtomicLong()
    private var helper: HelperEndpoint? = null

    override suspend fun runProbes(attemptTethering: Boolean): String = call(
        "runProbes",
        JSONObject()
            .put("attemptTethering", attemptTethering)
            .put("availabilityTimeoutMs", 10_000),
    )

    override suspend fun checkCompatibility(): List<CapabilityResult> =
        parseCapabilities(call("checkCompatibility"))

    override suspend fun installTetheringApex(path: String): StagingOutcome = withContext(Dispatchers.IO) {
        val encoded = Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)
        parseStagingOutcome(call("installApex", JSONObject().put("base64", encoded)))
    }

    override suspend fun rebootDevice(): String = call("reboot")

    override suspend fun start(logging: Boolean, vpnMode: VpnMode): String = call(
        "start",
        JSONObject().put("logging", logging).put("vpnMode", vpnMode.name),
    )

    override fun setLogging(enabled: Boolean) {
        val endpoint = helper ?: return
        Thread {
            runCatching { rpc(endpoint, "setLogging", JSONObject().put("enabled", enabled)) }
        }.start()
    }

    override suspend fun stop(): String = call("stop")

    override suspend fun clearLog(): String? = runCatching { call("clearLog"); null }
        .getOrElse { it.message ?: "could not reach local ADB helper" }

    override suspend fun releaseOrphanedDownstream(): String? = runCatching { stop(); null }
        .getOrElse { "could not reach local ADB helper to drop the hotspot: ${it.message}" }

    override suspend fun status(): String = call("status")

    override fun unbind() {
        // The helper intentionally survives Activity recreation and is reused by the session service.
    }

    override fun unbindAndStopDaemon() {
        val endpoint = helper ?: return
        Thread { runCatching { rpc(endpoint, "shutdown") } }.start()
        helper = null
    }

    private suspend fun call(op: String, args: JSONObject = JSONObject()): String =
        withContext(Dispatchers.IO) {
            val endpoint = ensureHelper()
            try {
                rpc(endpoint, op, args)
            } catch (failure: Throwable) {
                helper = null
                onSessionLost?.invoke()
                throw failure
            }
        }

    private suspend fun ensureHelper(): HelperEndpoint {
        helper?.let { existing ->
            if (runCatching { rpc(existing, "contract") }.getOrNull()?.toIntOrNull() == TetherService.CONTRACT_VERSION) {
                return existing
            }
            helper = null
        }

        LocalAdbManager.connect(context).getOrThrow()
        val endpoint = HelperEndpoint(randomPort(), randomToken())
        launchHelper(endpoint)

        // app_process pays a cold-start cost the first time it loads the APK
        // (on-device dexopt), which can easily run past ten seconds; poll well
        // past that before giving up so a slow start is not read as a failure.
        repeat(HELPER_CONNECT_RETRIES) {
            delay(HELPER_CONNECT_DELAY_MS)
            val contract = runCatching { rpc(endpoint, "contract") }.getOrNull()?.toIntOrNull()
            if (contract == TetherService.CONTRACT_VERSION) {
                helper = endpoint
                return endpoint
            }
        }

        // The helper writes its own crash output to the device log; surface its
        // tail so "did not start" carries the actual reason (missing class,
        // port bind failure, SELinux denial, ...).
        val logTail = helperLogTail().let { tail -> tail.takeIf { it.isNotBlank() }?.let { "\n$it" } }.orEmpty()
        error("Local ADB helper did not start.$logTail")
    }

    private suspend fun helperLogTail(): String = withContext(Dispatchers.IO) {
        runCatching { LocalAdbShell.execute(context, "tail -n 15 /data/local/tmp/shizzi-local-adb.log 2>&1") }
            .getOrNull()
            ?.trim()
            .orEmpty()
    }

    private fun launchHelper(endpoint: HelperEndpoint) {
        val app = context.applicationInfo
        val command = buildString {
            append("CLASSPATH=").append(shellQuote(app.sourceDir)).append(' ')
            append("LD_LIBRARY_PATH=").append(shellQuote(app.nativeLibraryDir)).append(' ')
            append("nohup app_process /system/bin dev.shizzi.LocalAdbHelperMain ")
            append("--port ").append(endpoint.port).append(' ')
            append("--token ").append(shellQuote(endpoint.token)).append(' ')
            append(">/data/local/tmp/shizzi-local-adb.log 2>&1 </dev/null &")
        }
        LocalAdbShell.execute(context, command)
    }

    private fun rpc(endpoint: HelperEndpoint, op: String, args: JSONObject = JSONObject()): String {
        Socket(InetAddress.getLoopbackAddress(), endpoint.port).use { socket ->
            socket.soTimeout = RPC_TIMEOUT_MS
            val request = JSONObject()
                .put("id", sequence.incrementAndGet())
                .put("token", endpoint.token)
                .put("op", op)
                .put("args", args)
            val writer = socket.getOutputStream().bufferedWriter()
            writer.write(request.toString())
            writer.newLine()
            writer.flush()
            val response = JSONObject(socket.getInputStream().bufferedReader().readLine())
            check(response.optBoolean("ok")) { response.optString("error", "Local ADB RPC failed") }
            return response.opt("result")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
        }
    }

    private fun randomPort(): Int = ServerSocket(0).use { it.localPort }

    private fun randomToken(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private data class HelperEndpoint(val port: Int, val token: String)

    private companion object {
        const val RPC_TIMEOUT_MS = 65_000
        const val HELPER_CONNECT_RETRIES = 60
        const val HELPER_CONNECT_DELAY_MS = 200L
    }
}

private object LocalAdbShell {
    fun execute(context: Context, command: String): String {
        val manager = LocalAdbConnectionManager.get(context)
        val stream = manager.openStream("shell:$command")
        return stream.use { adbStream ->
            adbStream.openInputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(SHELL_READ_BUFFER_BYTES)

                while (true) {
                    val read = try {
                        input.read(buffer)
                    } catch (failure: IOException) {
                        // libadb-android signals the end of a shell session by
                        // closing the stream: AdbStream.read() throws
                        // IOException("Stream closed.") instead of returning -1
                        // whenever the daemon's CLSE lands while the reader is
                        // blocked. Our launch command runs detached and produces
                        // no output, so that path is the normal exit here; only
                        // a message match is folded into EOF, everything else
                        // still surfaces as a real failure.
                        if (failure.message == STREAM_CLOSED_MESSAGE) break else throw failure
                    }

                    if (read == -1) break
                    output.write(buffer, 0, read)
                }

                output.toString("UTF-8")
            }
        }
    }

    private const val SHELL_READ_BUFFER_BYTES = 8 * 1024
    private const val STREAM_CLOSED_MESSAGE = "Stream closed."
}
