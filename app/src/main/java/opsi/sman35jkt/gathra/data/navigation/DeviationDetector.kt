package opsi.sman35jkt.gathra.data.navigation

import kotlin.math.max

data class DeviationDetectorConfig(
    val baseThresholdMeters: Double = 35.0,
    val accuracyMultiplier: Double = 1.5,
    val requiredConsecutiveSamples: Int = 3,
    val rerouteCooldownMillis: Long = 30_000L,
)

data class DeviationDecision(
    val thresholdMeters: Double,
    val consecutiveOffRouteSamples: Int,
    val isOffRoute: Boolean,
    val shouldReroute: Boolean,
)

/**
 * Stateful debounce for noisy GPS samples. One bad fix never triggers rerouting.
 */
class DeviationDetector(
    private val config: DeviationDetectorConfig = DeviationDetectorConfig(),
) {
    private var consecutiveOffRouteSamples = 0
    private var lastRerouteAtMillis: Long? = null

    init {
        require(config.baseThresholdMeters > 0.0)
        require(config.accuracyMultiplier >= 1.0)
        require(config.requiredConsecutiveSamples > 0)
        require(config.rerouteCooldownMillis >= 0L)
    }

    fun evaluate(
        distanceFromRouteMeters: Double,
        accuracyMeters: Double,
        timestampMillis: Long,
        rerouteInProgress: Boolean = false,
    ): DeviationDecision {
        require(distanceFromRouteMeters >= 0.0 && distanceFromRouteMeters.isFinite())
        require(accuracyMeters >= 0.0 && accuracyMeters.isFinite())

        val threshold = thresholdMeters(accuracyMeters)
        if (distanceFromRouteMeters > threshold) {
            consecutiveOffRouteSamples++
        } else {
            consecutiveOffRouteSamples = 0
        }

        val isOffRoute = consecutiveOffRouteSamples >= config.requiredConsecutiveSamples
        val cooldownExpired = lastRerouteAtMillis?.let {
            timestampMillis - it >= config.rerouteCooldownMillis
        } ?: true
        val shouldReroute = isOffRoute && cooldownExpired && !rerouteInProgress
        if (shouldReroute) {
            lastRerouteAtMillis = timestampMillis
        }

        return DeviationDecision(
            thresholdMeters = threshold,
            consecutiveOffRouteSamples = consecutiveOffRouteSamples,
            isOffRoute = isOffRoute,
            shouldReroute = shouldReroute,
        )
    }

    fun thresholdMeters(accuracyMeters: Double): Double =
        max(config.baseThresholdMeters, accuracyMeters * config.accuracyMultiplier)

    fun reset(clearCooldown: Boolean = false) {
        consecutiveOffRouteSamples = 0
        if (clearCooldown) {
            lastRerouteAtMillis = null
        }
    }
}

