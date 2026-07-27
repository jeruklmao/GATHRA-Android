package opsi.sman35jkt.gathra.domain.navigation

import opsi.sman35jkt.gathra.core.model.GeoPoint

data class NavigationProgress(
    val matchedLocation: GeoPoint,
    val distanceFromRouteMeters: Double,
    val travelledDistanceMeters: Double,
    val remainingDistanceMeters: Double,
    val remainingDurationSeconds: Long,
    val currentStepIndex: Int,
    val distanceToNextManoeuvreMeters: Double,
    val matchedSegmentIndex: Int,
    val isOffRoute: Boolean,
    val shouldReroute: Boolean,
    val isArrived: Boolean,
)

