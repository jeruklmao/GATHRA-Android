package opsi.sman35jkt.gathra.core.model

enum class FloodRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    BLOCKED,
    UNKNOWN,
    NOT_EVALUATED,
}

data class RouteFloodRisk(
    val level: FloodRiskLevel,
    val score: Double,
    val intersectsBlockedArea: Boolean,
    val affectedDistanceMeters: Int,
    val confidence: Double?,
    val reasonCodes: List<String>,
    val evaluatedAtEpochMillis: Long?,
    val validUntilEpochMillis: Long?,
    val hazardSnapshotId: String?,
) {
    init {
        require(score in 0.0..1.0) {
            "Risk score must be between 0.0 and 1.0."
        }
        require(affectedDistanceMeters >= 0) {
            "Affected distance cannot be negative."
        }
        require(confidence == null || confidence in 0.0..1.0) {
            "Confidence must be between 0.0 and 1.0 if present."
        }
    }
}

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
    val durationSeconds: Int = etaMinutes * 60,
) {
    init {
        require(distanceMeters > 0) {
            "Route distance must be positive."
        }
        require(etaMinutes > 0) {
            "Route ETA must be positive."
        }
        require(durationSeconds > 0) {
            "Route duration must be positive."
        }
    }
}

enum class ManeuverType {
    DEPART,
    CONTINUE,
    TURN,
    SLIGHT_TURN,
    SHARP_TURN,
    U_TURN,
    ROUNDABOUT,
    EXIT_ROUNDABOUT,
    MERGE,
    FORK,
    ARRIVE,
    UNKNOWN,
}

enum class ManeuverModifier {
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    U_TURN,
    NONE,
}

data class RouteManeuver(
    val type: ManeuverType,
    val modifier: ManeuverModifier,
    val bearingBefore: Int?,
    val bearingAfter: Int?,
) {
    init {
        require(bearingBefore == null || bearingBefore in 0..359) {
            "Bearing before must be between 0 and 359 degrees."
        }
        require(bearingAfter == null || bearingAfter in 0..359) {
            "Bearing after must be between 0 and 359 degrees."
        }
    }
}

data class RouteStep(
    val index: Int,
    val instruction: String,
    val streetName: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val maneuver: RouteManeuver,
    val geometryStartIndex: Int,
    val geometryEndIndex: Int,
) {
    init {
        require(index >= 0) {
            "Route step index cannot be negative."
        }
        require(instruction.isNotBlank()) {
            "Route step instruction cannot be blank."
        }
        require(distanceMeters >= 0) {
            "Route step distance cannot be negative."
        }
        require(durationSeconds >= 0) {
            "Route step duration cannot be negative."
        }
        require(geometryStartIndex >= 0 && geometryEndIndex >= geometryStartIndex) {
            "Route step geometry interval is invalid."
        }
    }
}

data class RouteOption(
    val id: String,
    val geometry: RouteGeometry,
    val summary: RouteSummary,
    val isRecommended: Boolean = false,
    val risk: RouteFloodRisk? = null,
    val steps: List<RouteStep> = emptyList(),
) {
    init {
        require(id.isNotBlank()) {
            "A route option must have a stable, non-blank ID."
        }
        if (steps.isNotEmpty()) {
            require(steps.map(RouteStep::index) == steps.indices.toList()) {
                "Route steps must have contiguous ordered indices."
            }
            require(steps.first().geometryStartIndex == 0) {
                "The first route step must start at the first geometry point."
            }
            require(
                steps.zipWithNext().all { (previous, next) ->
                    next.geometryStartIndex == previous.geometryEndIndex &&
                        next.geometryEndIndex >= previous.geometryEndIndex
                },
            ) {
                "Route step geometry intervals must be contiguous and ordered."
            }
            require(steps.all { it.geometryEndIndex < geometry.points.size }) {
                "Route step geometry intervals must stay within the route geometry."
            }
            require(
                steps.last().maneuver.type == ManeuverType.ARRIVE &&
                    steps.last().geometryEndIndex == geometry.points.lastIndex,
            ) {
                "The final route step must arrive at the final geometry point."
            }
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
