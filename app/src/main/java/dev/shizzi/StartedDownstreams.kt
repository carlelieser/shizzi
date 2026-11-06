package dev.shizzi

/**
 * The downstreams a session brought up, which are the only ones it stops.
 * Stopping USB tethering with nothing running can reset the USB function.
 *
 * Written from the session and watchdog threads, read by the shutdown hook.
 */
class StartedDownstreams {

    @Volatile
    var types: Set<TetheringType> = emptySet()
        private set

    @Synchronized
    fun add(type: TetheringType) {
        types = types + type
    }

    @Synchronized
    fun clear() {
        types = emptySet()
    }
}
