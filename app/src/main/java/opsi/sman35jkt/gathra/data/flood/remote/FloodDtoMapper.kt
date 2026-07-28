package opsi.sman35jkt.gathra.data.flood.remote

import android.os.Build
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import opsi.sman35jkt.gathra.core.model.GeoPoint
import java.time.Instant

fun FloodHazardsResponseDto.toDomain(): FloodHazardSnapshot {
    val domainHazards = features.mapNotNull { feature ->
        feature.toDomainOrNull()
    }
    return FloodHazardSnapshot(
        snapshotId = snapshotId,
        generatedAtEpochMillis = parseIsoToEpochMillis(generatedAt) ?: System.currentTimeMillis(),
        validUntilEpochMillis = validUntil?.let(::parseIsoToEpochMillis),
        source = mapFloodSource(source),
        hazards = domainHazards,
    )
}

fun FloodHazardFeatureDto.toDomainOrNull(): FloodHazardPolygon? {
    val geom = geometry ?: return null
    if (geom.type != "Polygon" || geom.coordinates.isEmpty()) return null

    val validatedRings = geom.coordinates.mapNotNull { ringCoords ->
        val points = ringCoords.mapNotNull { coord ->
            if (coord.size < 2) return@mapNotNull null
            val lon = coord[0]
            val lat = coord[1]
            if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                GeoPoint(latitude = lat, longitude = lon)
            } else {
                null
            }
        }
        if (points.size < 3) return@mapNotNull null
        val closedPoints = if (points.first() != points.last()) {
            points + points.first()
        } else {
            points
        }
        closedPoints
    }

    if (validatedRings.isEmpty()) return null

    val props = properties
    return FloodHazardPolygon(
        id = id,
        level = mapFloodLevel(props?.riskLevel),
        rings = validatedRings,
        confidence = props?.confidence?.takeIf { it in 0.0..1.0 },
        description = props?.description,
        observedAtEpochMillis = props?.observedAt?.let(::parseIsoToEpochMillis),
        validUntilEpochMillis = props?.validUntil?.let(::parseIsoToEpochMillis),
        source = mapFloodSource(props?.source),
        sourceNodeIds = props?.sourceNodeIds.orEmpty(),
    )
}

fun mapFloodLevel(raw: String?): FloodHazardLevel = when (raw?.uppercase()) {
    "LOW" -> FloodHazardLevel.LOW
    "MEDIUM" -> FloodHazardLevel.MEDIUM
    "HIGH" -> FloodHazardLevel.HIGH
    "BLOCKED" -> FloodHazardLevel.BLOCKED
    else -> FloodHazardLevel.UNKNOWN
}

fun mapFloodSource(raw: String?): FloodHazardSource = when (raw?.uppercase()) {
    "SIMULATED" -> FloodHazardSource.SIMULATED
    "SENSOR" -> FloodHazardSource.SENSOR
    else -> FloodHazardSource.UNKNOWN
}

private fun parseIsoToEpochMillis(rawIso: String): Long? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Instant.parse(rawIso).toEpochMilli()
        } else {
            null
        }
    }.getOrNull()
