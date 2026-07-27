package opsi.sman35jkt.gathra.data.route.remote

import kotlin.math.ceil
import opsi.sman35jkt.gathra.core.model.FloodRiskLevel
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.ManeuverModifier
import opsi.sman35jkt.gathra.core.model.ManeuverType
import opsi.sman35jkt.gathra.core.model.RouteFloodRisk
import opsi.sman35jkt.gathra.core.model.RouteGeometry
import opsi.sman35jkt.gathra.core.model.RouteManeuver
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.core.model.RouteStep
import opsi.sman35jkt.gathra.core.model.RouteSummary

internal object RouteDtoMapper {
    const val REQUESTED_ALTERNATIVES = 1
    private const val MAX_GEOMETRY_POINTS = 50_000

    fun toRequest(request: RouteRequest): RoutePreviewRequestDto =
        RoutePreviewRequestDto(
            origin = GeoPointRequestDto(
                latitude = request.origin.latitude,
                longitude = request.origin.longitude,
            ),
            destination = GeoPointRequestDto(
                latitude = request.destination.latitude,
                longitude = request.destination.longitude,
            ),
            travelMode = request.travelMode.name,
            alternatives = REQUESTED_ALTERNATIVES,
        )

    fun toDomain(
        response: RoutePreviewResponseDto,
        request: RouteRequest,
    ): List<RouteOption> {
        requireValid(response.requestId.isNotBlank())
        requireValid(response.routes.size in 1..2)
        requireValid(response.metadata.travelMode == request.travelMode.name)
        requireValid(
            response.metadata.requestedAlternatives == REQUESTED_ALTERNATIVES,
        )
        requireValid(
            response.metadata.returnedAlternatives == response.routes.size - 1,
        )
        requireValid(response.routes.first().isRecommended)
        requireValid(response.routes.count { it.isRecommended } == 1)
        requireValid(response.routes.map { it.id }.toSet().size == response.routes.size)
        requireValid(
            response.routes
                .map { it.geometry.coordinates }
                .toSet()
                .size == response.routes.size,
        )

        return response.routes.map { route ->
            requireValid(route.id.isNotBlank() && route.id.length <= 128)
            requireValid(route.geometry.type == GEOJSON_LINE_STRING)
            requireValid(
                route.geometry.coordinates.size in 2..MAX_GEOMETRY_POINTS,
            )
            requireValid(route.summary.distanceMeters > 0)
            requireValid(route.summary.durationSeconds > 0)
            requireValid(route.steps.isNotEmpty())
            requireValid(route.steps.size <= MAX_ROUTE_STEPS)

            val points = route.geometry.coordinates.map { coordinate ->
                requireValid(coordinate.size == 2)
                val longitude = coordinate[0]
                val latitude = coordinate[1]
                requireValid(longitude.isFinite() && latitude.isFinite())
                requireValid(longitude in -180.0..180.0)
                requireValid(latitude in -90.0..90.0)
                GeoPoint(latitude = latitude, longitude = longitude)
            }
            val steps = route.steps.mapIndexed { expectedIndex, step ->
                requireValid(step.index == expectedIndex)
                requireValid(step.instruction.isNotBlank())
                requireValid(step.instruction.length <= MAX_INSTRUCTION_LENGTH)
                requireValid(step.streetName.length <= MAX_STREET_NAME_LENGTH)
                requireValid(step.distanceMeters >= 0)
                requireValid(step.durationSeconds >= 0)
                requireValid(step.geometryStartIndex in points.indices)
                requireValid(step.geometryEndIndex in points.indices)
                requireValid(step.geometryStartIndex <= step.geometryEndIndex)
                if (expectedIndex == 0) {
                    requireValid(step.geometryStartIndex == 0)
                } else {
                    val previous = route.steps[expectedIndex - 1]
                    requireValid(
                        step.geometryStartIndex == previous.geometryEndIndex,
                    )
                    requireValid(step.geometryEndIndex >= previous.geometryEndIndex)
                }

                val maneuverType = enumValueOrInvalid<ManeuverType>(
                    step.manoeuvre.type,
                )
                val maneuverModifier = enumValueOrInvalid<ManeuverModifier>(
                    step.manoeuvre.modifier,
                )
                requireValid(
                    step.manoeuvre.bearingBefore == null ||
                        step.manoeuvre.bearingBefore in 0..359,
                )
                requireValid(
                    step.manoeuvre.bearingAfter == null ||
                        step.manoeuvre.bearingAfter in 0..359,
                )
                RouteStep(
                    index = step.index,
                    instruction = step.instruction,
                    streetName = step.streetName,
                    distanceMeters = step.distanceMeters,
                    durationSeconds = step.durationSeconds,
                    maneuver = RouteManeuver(
                        type = maneuverType,
                        modifier = maneuverModifier,
                        bearingBefore = step.manoeuvre.bearingBefore,
                        bearingAfter = step.manoeuvre.bearingAfter,
                    ),
                    geometryStartIndex = step.geometryStartIndex,
                    geometryEndIndex = step.geometryEndIndex,
                )
            }
            requireValid(steps.last().maneuver.type == ManeuverType.ARRIVE)
            requireValid(steps.last().geometryEndIndex == points.lastIndex)

            val riskDomain = route.risk?.let { riskDto ->
                val level = FloodRiskLevel.entries.firstOrNull { it.name == riskDto.level }
                    ?: FloodRiskLevel.UNKNOWN
                requireValid(riskDto.score in 0.0..1.0)
                requireValid(riskDto.affectedDistanceMeters >= 0)
                requireValid(riskDto.confidence == null || riskDto.confidence in 0.0..1.0)

                RouteFloodRisk(
                    level = level,
                    score = riskDto.score,
                    intersectsBlockedArea = riskDto.intersectsBlockedArea,
                    affectedDistanceMeters = riskDto.affectedDistanceMeters,
                    confidence = riskDto.confidence,
                    reasonCodes = riskDto.reasonCodes,
                    evaluatedAtEpochMillis = parseIsoToEpochMillis(riskDto.evaluatedAt),
                    validUntilEpochMillis = parseIsoToEpochMillis(riskDto.validUntil),
                    hazardSnapshotId = riskDto.hazardSnapshotId,
                )
            }

            RouteOption(
                id = route.id,
                geometry = RouteGeometry(points),
                summary = RouteSummary(
                    distanceMeters = route.summary.distanceMeters,
                    etaMinutes = ceil(route.summary.durationSeconds / 60.0)
                        .toInt()
                        .coerceAtLeast(1),
                    durationSeconds = route.summary.durationSeconds,
                ),
                isRecommended = route.isRecommended,
                risk = riskDomain,
                steps = steps,
            )
        }
    }

    private fun parseIsoToEpochMillis(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            runCatching { java.time.Instant.parse(isoString).toEpochMilli() }.getOrNull()
        } else {
            null
        }
    }

    private fun String?.isNullOrBlank(): Boolean = this == null || this.isBlank()

    private inline fun <reified T : Enum<T>> enumValueOrInvalid(value: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: throw InvalidRouteResponseException()

    private fun requireValid(condition: Boolean) {
        if (!condition) {
            throw InvalidRouteResponseException()
        }
    }

    private const val GEOJSON_LINE_STRING = "LineString"
    private const val MAX_ROUTE_STEPS = 10_000
    private const val MAX_INSTRUCTION_LENGTH = 500
    private const val MAX_STREET_NAME_LENGTH = 200
}

internal class InvalidRouteResponseException :
    IllegalArgumentException("The route service response violated the API contract.")
