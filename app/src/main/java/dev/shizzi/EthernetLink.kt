package dev.shizzi

import android.content.Context

/** An Ethernet adapter counts as connected once its link is up, as the stock toggle requires. */
class AndroidEthernetLink(
    private val context: Context,
    private val started: StartedDownstreams,
) : WiredLink {

    private val inspector = UpstreamInspector()

    override val type = TetheringType.ETHERNET

    override fun isConnected(): Boolean? {
        val observation = inspector.observeEthernet()
        if (observation.didTimeout) return null

        val carriers = parseEthernetInterfaces(observation.rawOutput).map(::readCarrier)
        return combineCarriers(carriers)
    }

    override fun startTethering(): Pair<Boolean, String> = startOwnedTethering(context, started, type)

    private fun readCarrier(name: String): Boolean? {
        val observation = inspector.observeInterface(name)
        if (observation.didTimeout) return null

        return parseCarrier(observation.rawOutput)
    }
}

private val ETHERNET_PORT_PATTERN = Regex("""EthernetPort\((\S+?),""")

/** The interfaces `dumpsys ethernet` tracks or tethers. */
fun parseEthernetInterfaces(output: String): List<String> =
    ETHERNET_PORT_PATTERN.findAll(output).map { it.groupValues[1] }.distinct().toList()

/** Reads an interface's link from `ip addr show`; false once it is gone. */
fun parseCarrier(output: String): Boolean? = when {
    "does not exist" in output -> false
    "NO-CARRIER" in output -> false
    "LOWER_UP" in output -> true
    else -> null
}

/** Connected when any adapter has a link; unknown when none does but some could not be read. */
fun combineCarriers(carriers: List<Boolean?>): Boolean? = when {
    true in carriers -> true
    null in carriers -> null
    else -> false
}
