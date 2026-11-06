package dev.shizzi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Calls [onChange] when the tethered interfaces change or a USB cable comes or
 * goes, so the session reacts at once instead of at its next scheduled check.
 * Either source may be unavailable; the scheduled check still covers it.
 */
class TetheringEvents(private val context: Context, private val onChange: () -> Unit) {

    private var callback: Any? = null
    private var receiver: BroadcastReceiver? = null

    fun register() {
        callback = runCatching { registerTetheringCallback() }
            .onFailure { SessionLog.warn("tethering events unavailable, polling instead: ${it.message}") }
            .getOrNull()

        receiver = runCatching { registerUsbReceiver() }
            .onFailure { SessionLog.warn("usb events unavailable, polling instead: ${it.message}") }
            .getOrNull()
    }

    fun unregister() {
        callback?.let { registered ->
            runCatching { tetheringManager().unregister(registered) }
        }
        receiver?.let { registered -> runCatching { context.unregisterReceiver(registered) } }
        callback = null
        receiver = null
    }

    private fun registerTetheringCallback(): Any {
        val callbackClass = Class.forName(CALLBACK_CLASS)
        val proxy = Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { self, method, args ->
            when (method.name) {
                "onTetheredInterfacesChanged" -> onChange()
                "hashCode" -> return@newProxyInstance System.identityHashCode(self)
                "equals" -> return@newProxyInstance self === args?.firstOrNull()
                "toString" -> return@newProxyInstance "TetheringEvents"
            }
            null
        }

        tetheringManager().register(callbackClass, proxy)
        return proxy
    }

    private fun registerUsbReceiver(): BroadcastReceiver {
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = onChange()
        }
        val filter = IntentFilter(USB_STATE_ACTION)

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

            else -> context.registerReceiver(usbReceiver, filter)
        }
        return usbReceiver
    }

    private fun tetheringManager(): ManagerHandle =
        ManagerHandle(context.getSystemService("tethering") ?: error("tethering service unavailable"))

    private class ManagerHandle(private val manager: Any) {

        fun register(callbackClass: Class<*>, callback: Any) {
            manager.javaClass
                .getMethod("registerTetheringEventCallback", Executor::class.java, callbackClass)
                .invoke(manager, Executor { command -> command.run() }, callback)
        }

        fun unregister(callback: Any) {
            val callbackClass = Class.forName(CALLBACK_CLASS)
            manager.javaClass
                .getMethod("unregisterTetheringEventCallback", callbackClass)
                .invoke(manager, callback)
        }
    }

    private companion object {
        const val CALLBACK_CLASS = "android.net.TetheringManager\$TetheringEventCallback"
        const val USB_STATE_ACTION = "android.hardware.usb.action.USB_STATE"
    }
}
