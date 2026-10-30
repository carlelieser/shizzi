package dev.shizzi

import android.content.Context

/** The USB reads and requests a session makes, apart from the decisions made on them. */
interface UsbLink {

    /** Null when `dumpsys usb` could not be read. */
    fun isHostConnected(): Boolean?

    fun startTethering(): Pair<Boolean, String>
}

class AndroidUsbLink(
    private val context: Context,
    private val started: StartedDownstreams,
) : UsbLink {

    private val inspector = UpstreamInspector()

    override fun isHostConnected(): Boolean? {
        val observation = inspector.observeUsb()
        if (observation.didTimeout) return null

        return parseUsbHostConnected(observation.rawOutput)
    }

    override fun startTethering(): Pair<Boolean, String> {
        val outcome = DownstreamControl(context).startTethering(TetheringType.USB)
        if (outcome.first) started.add(TetheringType.USB)
        return outcome
    }
}

private val DEVICE_CONNECTED_PATTERN = Regex("""^\s*connected=(true|false)\s*$""", RegexOption.MULTILINE)

/** Reads whether a USB host is attached from `dumpsys usb`'s device_manager block. */
fun parseUsbHostConnected(output: String): Boolean? {
    val start = output.indexOf("device_manager={")
    if (start < 0) return null

    val end = output.indexOf("host_manager={", start).takeIf { it > start } ?: output.length
    val block = output.substring(start, end)

    return DEVICE_CONNECTED_PATTERN.find(block)?.groupValues?.get(1)?.toBooleanStrictOrNull()
}
