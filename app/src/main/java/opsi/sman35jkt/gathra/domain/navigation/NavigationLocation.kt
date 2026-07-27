package opsi.sman35jkt.gathra.domain.navigation

import opsi.sman35jkt.gathra.core.model.GeoPoint

/**
 * Framework-independent location input used by the navigation engine.
 */
data class NavigationLocationSample(
    val point: GeoPoint,
    val accuracyMeters: Double,
    val bearingDegrees: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val elapsedRealtimeMillis: Long,
    val epochTimeMillis: Long,
    val isApproximate: Boolean = false,
) {
    init {
        require(accuracyMeters.isFinite() && accuracyMeters >= 0.0) {
            "Location accuracy must be finite and non-negative."
        }
        require(elapsedRealtimeMillis >= 0L) {
            "Location elapsed-realtime timestamp cannot be negative."
        }
        require(epochTimeMillis >= 0L) {
            "Location epoch timestamp cannot be negative."
        }
        require(bearingDegrees == null || bearingDegrees.isFinite() && bearingDegrees in 0.0..<360.0) {
            "Location bearing must be between 0 (inclusive) and 360 (exclusive)."
        }
        require(
            speedMetersPerSecond == null ||
                speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0.0,
        ) {
            "Location speed must be finite and non-negative."
        }
    }
}

data class RouteMatch(
    val matchedLocation: GeoPoint,
    val segmentIndex: Int,
    val segmentFraction: Double,
    val distanceFromRouteMeters: Double,
    val distanceAlongRouteMeters: Double,
    val elapsedRealtimeMillis: Long,
) {
    init {
        require(segmentIndex >= 0)
        require(segmentFraction in 0.0..1.0)
        require(distanceFromRouteMeters >= 0.0)
        require(distanceAlongRouteMeters >= 0.0)
    }
}
