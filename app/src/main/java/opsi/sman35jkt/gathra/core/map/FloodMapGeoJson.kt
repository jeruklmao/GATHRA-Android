package opsi.sman35jkt.gathra.core.map

import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import opsi.sman35jkt.gathra.core.model.GeoPoint

internal fun floodFeatureCollection(snapshot: FloodHazardSnapshot): FeatureCollection {
    val features = snapshot.hazards.mapNotNull { hazard ->
        val polygonPoints = hazard.rings.map { ring ->
            ring.filter(GeoPoint::isFloodRenderable).map { point ->
                Point.fromLngLat(point.longitude, point.latitude)
            }
        }
        if (polygonPoints.isEmpty() || polygonPoints.first().size < 3) {
            null
        } else {
            val feature = Feature.fromGeometry(
                Polygon.fromLngLats(polygonPoints),
                null,
                hazard.id,
            )
            feature.addStringProperty("riskLevel", hazard.level.name)
            feature.addStringProperty(
                "freshness",
                hazard.freshness?.name ?: "UNSPECIFIED",
            )
            feature.addNumberProperty("routingMultiplier", hazard.routingMultiplier)
            feature.addStringProperty(
                FLOOD_VISUAL_STATE_PROPERTY,
                hazard.floodMapVisualState(),
            )
            feature
        }
    }
    return FeatureCollection.fromFeatures(features.toTypedArray())
}

internal fun FloodHazardPolygon.floodMapVisualState(): String = when (freshness) {
    FloodHazardFreshness.STALE -> "STALE"
    FloodHazardFreshness.NO_TELEMETRY -> "NO_TELEMETRY"
    FloodHazardFreshness.FRESH -> level.freshVisualState()
    null -> if (source == FloodHazardSource.SENSOR) {
        "UNSPECIFIED_SENSOR"
    } else {
        level.freshVisualState()
    }
}

private fun FloodHazardLevel.freshVisualState(): String = when (this) {
    FloodHazardLevel.LOW -> "LOW"
    FloodHazardLevel.MEDIUM -> "MEDIUM"
    FloodHazardLevel.HIGH -> "HIGH"
    FloodHazardLevel.BLOCKED -> "BLOCKED"
    FloodHazardLevel.UNKNOWN -> "UNKNOWN"
}

private fun GeoPoint.isFloodRenderable(): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0

internal const val FLOOD_VISUAL_STATE_PROPERTY = "visualState"
