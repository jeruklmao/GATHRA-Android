package opsi.sman35jkt.gathra.core.map

import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import opsi.sman35jkt.gathra.core.model.GeoPoint

internal class InitialFloodCameraPolicy {
    private var consumed = false
    var isUserOwned: Boolean = false
        private set

    fun takeUserOwnership() {
        isUserOwned = true
        consumed = true
    }

    fun claim(snapshot: FloodHazardSnapshot?, cameraOwnedByRoute: Boolean): List<GeoPoint>? {
        if (consumed || cameraOwnedByRoute) return null
        val points = initialSensorCoveragePoints(snapshot)
        if (points.size < 2) return null
        consumed = true
        return points
    }
}

internal fun initialSensorCoveragePoints(snapshot: FloodHazardSnapshot?): List<GeoPoint> =
    snapshot?.hazards
        ?.filter { it.source == FloodHazardSource.SENSOR }
        ?.flatMap { it.rings.flatten() }
        ?.filter { point ->
            point.latitude.isFinite() && point.longitude.isFinite() &&
                point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
        }
        ?.distinctBy { it.latitude to it.longitude }
        .orEmpty()
