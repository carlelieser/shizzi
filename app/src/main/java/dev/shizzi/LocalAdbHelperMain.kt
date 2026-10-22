package dev.shizzi

import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.json.JSONObject

object LocalAdbHelperMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = args.toOptions()
        val port = options["port"]?.toIntOrNull() ?: error("missing --port")
        val token = options["token"] ?: error("missing --token")

        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()

        val service = TetherService()
        val server = ServerSocket(port, 8, InetAddress.getLoopbackAddress())
        thread(name = "shizzi-local-adb-rpc", isDaemon = false) {
            try {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    socket.use { client ->
                        client.soTimeout = SOCKET_TIMEOUT_MS
                        val request = client.getInputStream().bufferedReader().readLine() ?: return@use
                        val response = handle(JSONObject(request), token, service, server)
                        client.getOutputStream().bufferedWriter().use { writer ->
                            writer.write(response.toString())
                            writer.newLine()
                        }
                    }
                }
            } finally {
                runCatching { server.close() }
                Looper.getMainLooper().quitSafely()
            }
        }
        Looper.loop()
    }

    private fun handle(
        request: JSONObject,
        token: String,
        service: TetherService,
        server: ServerSocket,
    ): JSONObject {
        val id = request.optLong("id")
        if (request.optString("token") != token) return error(id, "unauthorized")

        return runCatching {
            val args = request.optJSONObject("args") ?: JSONObject()
            val result: Any? = when (request.getString("op")) {
                "contract" -> service.getContractVersion()
                "start" -> service.start(args.optBoolean("logging"), args.optString("vpnMode"))
                "stop" -> service.stop()
                "status" -> service.getStatus()
                "setLogging" -> service.setLogging(args.optBoolean("enabled")).let { "" }
                "checkCompatibility" -> service.checkCompatibility()
                "runProbes" -> service.runProbes(
                    args.optBoolean("attemptTethering"),
                    args.optInt("availabilityTimeoutMs", 10_000),
                )
                "clearLog" -> service.clearLog().let { "" }
                "reboot" -> service.rebootDevice()
                "installApex" -> installApex(service, args.getString("base64"))
                "shutdown" -> {
                    runCatching { service.stop() }
                    server.close()
                    ""
                }
                else -> error("unknown operation")
            }
            JSONObject().put("id", id).put("ok", true).put("result", result ?: JSONObject.NULL)
        }.getOrElse { failure -> error(id, "${failure.javaClass.name}: ${failure.message}") }
    }

    private fun installApex(service: TetherService, encoded: String): String {
        val file = File(TEMP_APEX)
        file.writeBytes(Base64.decode(encoded, Base64.NO_WRAP))
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use(service::installTetheringApex)
        } finally {
            file.delete()
        }
    }

    private fun error(id: Long, message: String) =
        JSONObject().put("id", id).put("ok", false).put("error", message)

    private fun Array<String>.toOptions(): Map<String, String> = buildMap {
        var index = 0
        while (index + 1 < size) {
            val key = this@toOptions[index].removePrefix("--")
            put(key, this@toOptions[index + 1])
            index += 2
        }
    }

    private const val TEMP_APEX = "/data/local/tmp/shizzi-tethering.apex"
    private const val SOCKET_TIMEOUT_MS = 60_000
}
