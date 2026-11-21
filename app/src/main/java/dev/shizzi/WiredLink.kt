package dev.shizzi

import android.content.Context

/** The reads and requests a session makes about one kind of cable, apart from the decisions made on them. */
interface WiredLink {

    val type: TetheringType

    /** Whether the other end is connected; null when it could not be read. */
    fun isConnected(): Boolean?

    fun startTethering(): Pair<Boolean, String>
}

/** Starts [type] tethering and records it as the session's own. */
fun startOwnedTethering(context: Context, started: StartedDownstreams, type: TetheringType): Pair<Boolean, String> {
    val outcome = DownstreamControl(context).startTethering(type)
    if (outcome.first) started.add(type)
    return outcome
}

class AndroidUsbLink(
    private val context: Context,
    private val started: StartedDownstreams,
) : WiredLink {

    private val inspector = UpstreamInspector()

    override val type = TetheringType.USB

    override fun isConnected(): Boolean? {
        val observation = inspector.observeUsb()
        if (observation.didTimeout) return null

        return parseUsbHostConnected(observation.rawOutput)
    }

    override fun startTethering(): Pair<Boolean, String> = startOwnedTethering(context, started, type)
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
