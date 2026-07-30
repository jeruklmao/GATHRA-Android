package opsi.sman35jkt.gathra.data.flood.remote

import opsi.sman35jkt.gathra.data.common.parseStrictIsoTimestamp
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import opsi.sman35jkt.gathra.core.model.GeoPoint

fun FloodHazardsResponseDto.toDomain(): FloodHazardSnapshot {
    requireFloodContract(type == GEOJSON_FEATURE_COLLECTION)
    requireFloodContract(isValidIdentifier(snapshotId))
    requireFloodContract(features.size <= MAX_HAZARDS)
    val generatedAtMillis = parseStrictIsoTimestamp(generatedAt)
        ?: throw InvalidFloodResponseException()
    val validUntilMillis = validUntil?.let {
        parseStrictIsoTimestamp(it).also { parsed ->
            requireFloodContract(parsed != null)
        }
    }
    requireFloodContract(
        validUntilMillis == null || validUntilMillis > generatedAtMillis,
    )
    val domainHazards = features.map(FloodHazardFeatureDto::toDomain)
    return FloodHazardSnapshot(
        snapshotId = snapshotId,
        generatedAtEpochMillis = generatedAtMillis,
        validUntilEpochMillis = validUntilMillis,
        source = mapFloodSource(source),
        hazards = domainHazards,
    )
}

private fun FloodHazardFeatureDto.toDomain(): FloodHazardPolygon {
    requireFloodContract(type == GEOJSON_FEATURE)
    requireFloodContract(isValidIdentifier(id))
    val props = properties ?: throw InvalidFloodResponseException()
    val geom = geometry ?: throw InvalidFloodResponseException()
    requireFloodContract(geom.type == GEOJSON_POLYGON)
    requireFloodContract(geom.coordinates.isNotEmpty())

    var totalVertices = 0
    val validatedRings = geom.coordinates.map { ringCoords ->
        requireFloodContract(ringCoords.size >= MIN_RING_POINTS)
        totalVertices += ringCoords.size
        requireFloodContract(totalVertices <= MAX_POLYGON_VERTICES)
        val points = ringCoords.map { coord ->
            requireFloodContract(coord.size == 2)
            val lon = coord[0]
            val lat = coord[1]
            requireFloodContract(lat.isFinite() && lon.isFinite())
            requireFloodContract(lat in -90.0..90.0 && lon in -180.0..180.0)
            GeoPoint(latitude = lat, longitude = lon)
        }
        requireFloodContract(points.first() == points.last())
        requireFloodContract(points.dropLast(1).distinct().size >= 3)
        points
    }

    val level = mapFloodLevel(props.riskLevel)
        ?: throw InvalidFloodResponseException()
    requireFloodContract(props.confidence == null || props.confidence in 0.0..1.0)
    val observedAt = parseStrictIsoTimestamp(props.observedAt)
        ?: throw InvalidFloodResponseException()
    val hazardValidUntil = props.validUntil?.let {
        parseStrictIsoTimestamp(it).also { parsed ->
            requireFloodContract(parsed != null)
        }
    }
    requireFloodContract(
        hazardValidUntil == null || hazardValidUntil > observedAt,
    )
    requireFloodContract(
        props.description == null || props.description.length <= MAX_DESCRIPTION_LENGTH,
    )
    val sourceNodeIds = props.sourceNodeIds.orEmpty()
    requireFloodContract(sourceNodeIds.size <= MAX_SOURCE_NODE_IDS)
    requireFloodContract(sourceNodeIds.all(::isValidIdentifier))

    return FloodHazardPolygon(
        id = id,
        level = level,
        rings = validatedRings,
        confidence = props.confidence,
        description = props.description,
        observedAtEpochMillis = observedAt,
        validUntilEpochMillis = hazardValidUntil,
        source = mapFloodSource(props.source),
        sourceNodeIds = sourceNodeIds,
    )
}

fun mapFloodLevel(raw: String?): FloodHazardLevel? = when (raw?.uppercase()) {
    "LOW" -> FloodHazardLevel.LOW
    "MEDIUM" -> FloodHazardLevel.MEDIUM
    "HIGH" -> FloodHazardLevel.HIGH
    "BLOCKED" -> FloodHazardLevel.BLOCKED
    "UNKNOWN" -> FloodHazardLevel.UNKNOWN
    else -> null
}

fun mapFloodSource(raw: String?): FloodHazardSource = when (raw?.uppercase()) {
    "SIMULATED" -> FloodHazardSource.SIMULATED
    "SENSOR" -> FloodHazardSource.SENSOR
    else -> FloodHazardSource.UNKNOWN
}

private fun isValidIdentifier(value: String): Boolean =
    value.isNotBlank() &&
        value.length <= MAX_IDENTIFIER_LENGTH &&
        value.all { it.isLetterOrDigit() || it in IDENTIFIER_PUNCTUATION }

private fun requireFloodContract(condition: Boolean) {
    if (!condition) throw InvalidFloodResponseException()
}

internal class InvalidFloodResponseException :
    IllegalArgumentException("The flood service response violated the API contract.")

private const val GEOJSON_FEATURE_COLLECTION = "FeatureCollection"
private const val GEOJSON_FEATURE = "Feature"
private const val GEOJSON_POLYGON = "Polygon"
private const val MAX_HAZARDS = 50
private const val MAX_POLYGON_VERTICES = 2_000
private const val MIN_RING_POINTS = 4
private const val MAX_IDENTIFIER_LENGTH = 128
private const val MAX_DESCRIPTION_LENGTH = 500
private const val MAX_SOURCE_NODE_IDS = 100
private const val IDENTIFIER_PUNCTUATION = "._:-"
