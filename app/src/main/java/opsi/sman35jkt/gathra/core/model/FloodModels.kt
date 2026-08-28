package opsi.sman35jkt.gathra.core.model

import androidx.compose.runtime.Immutable

/**
 * Geographic bounding box in latitude/longitude degrees.
 */
@Immutable
data class GeoBounds(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
)

/**
 * Categorised severity level for a flood hazard area.
 */
enum class FloodHazardLevel {
    LOW,
    MEDIUM,
    HIGH,
    BLOCKED,
    UNKNOWN,
}

/**
 * Origin source of flood hazard data.
 */
enum class FloodHazardSource {
    SIMULATED,
    SENSOR,
    UNKNOWN,
}

/**
 * Backend-authoritative age/availability of the sensor measurement.
 *
 * This is deliberately independent from both [FloodHazardLevel] and the
 * success or failure of the HTTP snapshot refresh.
 */
enum class FloodHazardFreshness {
    FRESH,
    STALE,
    NO_TELEMETRY,
}

/**
 * Domain representation of a flood hazard polygon overlay.
 */
@Immutable
data class FloodHazardPolygon(
    val id: String,
    val level: FloodHazardLevel,
    val rings: List<List<GeoPoint>>,
    val confidence: Double?,
    val description: String?,
    val observedAtEpochMillis: Long?,
    val validUntilEpochMillis: Long?,
    val source: FloodHazardSource,
    val sourceNodeIds: List<String>,
    val routingMultiplier: Double,
    val reasonCodes: List<String>,
    val freshness: FloodHazardFreshness?,
)

/**
 * Immutable snapshot of all active flood hazard polygons for a given version identity.
 */
@Immutable
data class FloodHazardSnapshot(
    val snapshotId: String,
    val generatedAtEpochMillis: Long,
    val validUntilEpochMillis: Long?,
    val source: FloodHazardSource,
    val hazards: List<FloodHazardPolygon>,
)
