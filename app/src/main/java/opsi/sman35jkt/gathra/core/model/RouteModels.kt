package opsi.sman35jkt.gathra.core.model

data class RouteGeometry(
    val points: List<GeoPoint>,
) {
    init {
        require(points.size >= 2) {
            "A route geometry must contain at least two points."
        }
    }
}

data class RouteSummary(
    val distanceMeters: Int,
    val etaMinutes: Int,
) {
    init {
        require(distanceMeters > 0) {
            "Route distance must be positive."
        }
        require(etaMinutes > 0) {
            "Route ETA must be positive."
        }
    }
}

data class RouteOption(
    val id: String,
    val geometry: RouteGeometry,
    val summary: RouteSummary,
    val isRecommended: Boolean = false,
) {
    init {
        require(id.isNotBlank()) {
            "A route option must have a stable, non-blank ID."
        }
    }
}

data class RouteRequest(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val travelMode: TravelMode,
) {
    init {
        require(origin != destination) {
            "Origin and destination must be different points."
        }
    }
}
