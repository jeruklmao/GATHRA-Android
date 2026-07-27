package opsi.sman35jkt.gathra.data.navigation

import opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample
import kotlin.math.max

data class NavigationLocationFilterConfig(
    val staleAfterMillis: Long = 15_000L,
    val futureToleranceMillis: Long = 2_000L,
    val maximumAccuracyMeters: Double = 250.0,
    val maximumPlausibleSpeedMetersPerSecond: Double = 80.0,
    val minimumJumpAllowanceMeters: Double = 120.0,
)

class NavigationLocationFilter(
    private val config: NavigationLocationFilterConfig = NavigationLocationFilterConfig(),
) {
    fun isAcceptable(
        candidate: NavigationLocationSample,
        previousAccepted: NavigationLocationSample?,
        nowElapsedRealtimeMillis: Long,
    ): Boolean {
        if (
            nowElapsedRealtimeMillis - candidate.elapsedRealtimeMillis >
            config.staleAfterMillis
        ) return false
        if (
            candidate.elapsedRealtimeMillis - nowElapsedRealtimeMillis >
            config.futureToleranceMillis
        ) return false
        if (candidate.accuracyMeters > config.maximumAccuracyMeters) return false
        if (previousAccepted == null) return true
        if (candidate.elapsedRealtimeMillis <= previousAccepted.elapsedRealtimeMillis) return false

        val elapsedSeconds = (
            candidate.elapsedRealtimeMillis - previousAccepted.elapsedRealtimeMillis
            ) / 1_000.0
        val allowedDistance = max(
            config.minimumJumpAllowanceMeters,
            elapsedSeconds * config.maximumPlausibleSpeedMetersPerSecond +
                candidate.accuracyMeters + previousAccepted.accuracyMeters,
        )
        return GeoMath.distanceMeters(previousAccepted.point, candidate.point) <= allowedDistance
    }
}
