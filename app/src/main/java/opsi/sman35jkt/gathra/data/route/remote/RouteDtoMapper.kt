package opsi.sman35jkt.gathra.data.route.remote

import kotlin.math.ceil
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteGeometry
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteRequest
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

            val points = route.geometry.coordinates.map { coordinate ->
                requireValid(coordinate.size == 2)
                val longitude = coordinate[0]
                val latitude = coordinate[1]
                requireValid(longitude.isFinite() && latitude.isFinite())
                requireValid(longitude in -180.0..180.0)
                requireValid(latitude in -90.0..90.0)
                GeoPoint(latitude = latitude, longitude = longitude)
            }

            RouteOption(
                id = route.id,
                geometry = RouteGeometry(points),
                summary = RouteSummary(
                    distanceMeters = route.summary.distanceMeters,
                    etaMinutes = ceil(route.summary.durationSeconds / 60.0)
                        .toInt()
                        .coerceAtLeast(1),
                ),
                isRecommended = route.isRecommended,
            )
        }
    }

    private fun requireValid(condition: Boolean) {
        if (!condition) {
            throw InvalidRouteResponseException()
        }
    }

    private const val GEOJSON_LINE_STRING = "LineString"
}

internal class InvalidRouteResponseException :
    IllegalArgumentException("The route service response violated the API contract.")
