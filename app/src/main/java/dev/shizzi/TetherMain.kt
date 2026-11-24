package dev.shizzi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import java.lang.reflect.Method
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Entry point of the helper when it is started over adb instead of by Shizuku:
 *
 *   CLASSPATH=<base.apk> setsid app_process -Djava.library.path=<apk>!/lib/<abi> /system/bin \
 *       --nice-name=dev.shizzi:adb dev.shizzi.TetherMain <package> <secret>
 *
 * It runs as the shell user (uid 2000) like the Shizuku user service does and builds the very same
 * [TetherService]. What Shizuku does for free (handing the Binder to the app) is done here with an
 * explicit broadcast, repeated while no app process is attached, so the helper can outlive the app
 * the way a daemon user service does.
 */
object TetherMain {

    private const val RESEND_MS = 3_000L
    private const val NOBODY_CAME_MS = 90_000L
    private const val ORPHAN_IDLE_MS = 10 * 60_000L
    private const val POLL_MS = 1_000L

    private var lastClientSeen = SystemClock.elapsedRealtime()

    /** True while an app process holds the helper (set by [TetherService.attach]). */
    @Volatile
    var isAttached = false
        private set

    @Volatile
    var hasEverBeenAttached = false
        private set

    fun onAttached(client: IBinder) {
        isAttached = true
        hasEverBeenAttached = true
        runCatching {
            client.linkToDeath({
                isAttached = false
                lastClientSeen = SystemClock.elapsedRealtime()
            }, 0)
        }.onFailure { isAttached = false }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) {
            System.err.println("usage: TetherMain <package> <secret>")
            exitProcess(2)
        }
        val packageName = args[0]
        val secret = args[1]

        Looper.prepareMainLooper()
        val service = try {
            TetherService()
        } catch (failure: Throwable) {
            System.err.println("cannot create the service: $failure")
            failure.printStackTrace()
            exitProcess(1)
        }

        thread(name = "shizzi-adb-supervisor", isDaemon = true) {
            supervise(service, packageName, secret)
        }
        println("shizzi helper up, uid ${Process.myUid()}, pid ${Process.myPid()}")
        Looper.loop()
    }

    private fun supervise(service: TetherService, packageName: String, secret: String) {
        val started = SystemClock.elapsedRealtime()
        var lastSent = 0L
        while (true) {
            Thread.sleep(POLL_MS)
            val now = SystemClock.elapsedRealtime()

            if (isAttached) continue

            if (now - lastSent >= RESEND_MS) {
                lastSent = now
                runCatching { sendBinderToApp(service, packageName, secret) }
                    .onFailure { System.err.println("binder broadcast failed: $it") }
            }

            val waitedFor = if (hasEverBeenAttached) now - lastClientSeen else now - started
            val limit = if (hasEverBeenAttached) ORPHAN_IDLE_MS else NOBODY_CAME_MS
            // A running hotspot is never abandoned just because the app is gone.
            if (waitedFor > limit && !service.hasActiveSession()) {
                System.err.println("no app attached for ${waitedFor / 1000}s, exiting")
                exitProcess(0)
            }
        }
    }

    /**
     * A bare app_process has no ApplicationThread registered with ActivityManager, so
     * Context.sendBroadcast() is rejected ("Unable to find app for caller") or dropped. Go to
     * IActivityManager directly with a null caller, exactly like `am broadcast` does as shell,
     * and keep the Context only as the fallback.
     *
     * Not FLAG_INCLUDE_STOPPED_PACKAGES: an app the user force-stopped must stay stopped.
     */
    private fun sendBinderToApp(service: TetherService, packageName: String, secret: String) {
        val intent = Intent(AdbHelperRegistry.ACTION).apply {
            component = ComponentName(packageName, "$packageName.AdbHelperReceiver")
            putExtra(AdbHelperRegistry.EXTRA_BUNDLE, Bundle().apply {
                putBinder(AdbHelperRegistry.KEY_BINDER, service)
            })
            putExtra(AdbHelperRegistry.EXTRA_SECRET, secret)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        try {
            broadcastAsShell(intent)
            return
        } catch (failure: Throwable) {
            System.err.println("IActivityManager broadcast failed: ${rootMessage(failure)}")
        }
        service.shell().sendBroadcast(intent)
    }

    private fun broadcastAsShell(intent: Intent) {
        val amBinder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java).invoke(null, "activity") as IBinder?
            ?: throw IllegalStateException("the activity service is unavailable")
        val amClass = Class.forName("android.app.IActivityManager")
        val am = Class.forName("android.app.IActivityManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, amBinder)

        val target: Method = amClass.methods.firstOrNull { it.name == "broadcastIntentWithFeature" }
            ?: throw NoSuchMethodException("broadcastIntentWithFeature")

        // Signatures differ per release (12: 14 args, 13: 15, 14+: 16) but share this shape:
        // (caller, feature, Intent, type, resultTo, int code, String data, Bundle map,
        //  String[]..., int appOp, Bundle options, boolean serialized, boolean sticky, int userId)
        val types = target.parameterTypes
        val intCount = types.count { it == Int::class.javaPrimitiveType }
        var intIndex = 0
        val args = arrayOfNulls<Any>(types.size)
        types.forEachIndexed { index, type ->
            args[index] = when (type) {
                Intent::class.java -> intent
                Int::class.javaPrimitiveType -> {
                    val value = when (intIndex) {
                        intCount - 1 -> -2      // USER_CURRENT
                        0 -> 0                  // result code
                        else -> -1              // OP_NONE
                    }
                    intIndex++
                    value
                }
                Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        }
        target.invoke(am, *args)
    }

    private fun rootMessage(failure: Throwable): String {
        var cause: Throwable = failure
        while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }
}
