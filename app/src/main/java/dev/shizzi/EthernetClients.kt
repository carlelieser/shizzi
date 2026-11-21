package dev.shizzi

import java.io.File

/** One `ip neigh` entry; [mac] is null for an address that did not resolve. */
data class Neighbor(val address: String, val mac: String?, val state: String)

private val NEIGHBOR_PATTERN = Regex("""^(\S+)\s+(?:lladdr\s+(\S+)\s+)?(?:router\s+)?([A-Z]+)\s*$""")

private val PRESENT_STATES = setOf("REACHABLE", "STALE", "DELAY", "PROBE")

fun parseNeighbors(output: String): List<Neighbor> =
    output.lineSequence()
        .mapNotNull { line -> NEIGHBOR_PATTERN.find(line.trim()) }
        .map { match ->
            val (address, mac, state) = match.destructured
            Neighbor(address, mac.ifEmpty { null }, state)
        }
        .toList()

/** Devices on the link: each MAC with an address the kernel has not given up on. */
fun countPresentDevices(neighbors: List<Neighbor>): Int =
    neighbors
        .filter { neighbor -> neighbor.mac != null && neighbor.state in PRESENT_STATES }
        .mapNotNull(Neighbor::mac)
        .distinct()
        .size

/** A STALE entry outlives a device that left; one packet settles it as REACHABLE or FAILED. */
fun neighborsToProbe(neighbors: List<Neighbor>): List<Neighbor> =
    neighbors.filter { neighbor -> neighbor.state == "STALE" }

/**
 * Counts the devices behind Ethernet tethering, which may sit behind a switch.
 * Probes settle after the count, so a device that left drops within a few counts.
 */
class EthernetClients(private val inspector: UpstreamInspector = UpstreamInspector()) {

    @Volatile
    private var canProbe = true

    /** Null when a neighbor table could not be read. */
    fun count(interfaces: List<String>): Int? {
        val tables = interfaces.map { name -> name to inspector.observeNeighbors(name) }
        if (tables.any { (_, observation) -> observation.didTimeout }) return null

        val neighbors = tables.map { (name, observation) -> name to parseNeighbors(observation.rawOutput) }
        neighbors.forEach { (name, entries) -> neighborsToProbe(entries).forEach { probe(name, it.address) } }
        return countPresentDevices(neighbors.flatMap { (_, entries) -> entries })
    }

    private fun probe(interfaceName: String, address: String) {
        if (!canProbe) return

        val ping = if (':' in address) "ping6" else "ping"
        runCatching {
            ProcessBuilder(ping, "-c", "1", "-W", "1", "-I", interfaceName, address)
                .redirectErrorStream(true)
                .redirectOutput(File("/dev/null"))
                .start()
        }.onFailure { failure ->
            canProbe = false
            SessionLog.warn("ethernet client probes unavailable, counts may lag departures: ${failure.message}")
        }
    }
}
