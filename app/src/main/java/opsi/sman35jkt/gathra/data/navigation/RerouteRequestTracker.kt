package opsi.sman35jkt.gathra.data.navigation

/**
 * Monotonic generation guard that prevents a stale reroute response from
 * replacing a newer route.
 */
class RerouteRequestTracker {
    private var generation = 0L

    fun beginRequest(): Long {
        generation++
        return generation
    }

    fun isCurrent(requestGeneration: Long): Boolean = requestGeneration == generation

    fun invalidate() {
        generation++
    }
}

