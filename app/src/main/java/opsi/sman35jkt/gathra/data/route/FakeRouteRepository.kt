package opsi.sman35jkt.gathra.data.route

import kotlinx.coroutines.delay
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteGeometry
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.core.model.RouteSummary
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.domain.route.RouteRepository
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class FakeRouteFailureMode {
    NONE,
    ALWAYS_FAIL,
}

class FakeRouteDataException(
    message: String = "Demo route data is unavailable.",
) : IllegalStateException(message)

/**
 * Deterministic, network-free route data for the route-preview milestone.
 */
class FakeRouteRepository(
    private val loadingDelayMillis: Long = DEFAULT_LOADING_DELAY_MILLIS,
    private val failureMode: FakeRouteFailureMode = FakeRouteFailureMode.NONE,
) : RouteRepository {

    init {
        require(loadingDelayMillis >= 0) {
            "Loading delay cannot be negative."
        }
    }

    override suspend fun getRoutes(request: RouteRequest): List<RouteOption> {
        // delay is cancellable, including while a newer route request supersedes this one.
        delay(loadingDelayMillis)

        if (failureMode == FakeRouteFailureMode.ALWAYS_FAIL) {
            throw FakeRouteDataException()
        }

        val primaryGeometry = createGeometry(
            origin = request.origin,
            destination = request.destination,
            bendFactor = PRIMARY_BEND_FACTOR,
        )
        val alternativeGeometry = createGeometry(
            origin = request.origin,
            destination = request.destination,
            bendFactor = ALTERNATIVE_BEND_FACTOR,
        )

        return listOf(
            createRouteOption(
                id = PRIMARY_ROUTE_ID,
                geometry = primaryGeometry,
                travelMode = request.travelMode,
                isRecommended = true,
                speedAdjustment = 1.0,
            ),
            createRouteOption(
                id = ALTERNATIVE_ROUTE_ID,
                geometry = alternativeGeometry,
                travelMode = request.travelMode,
                isRecommended = false,
                speedAdjustment = ALTERNATIVE_SPEED_ADJUSTMENT,
            ),
        )
    }

    private fun createRouteOption(
        id: String,
        geometry: RouteGeometry,
        travelMode: TravelMode,
        isRecommended: Boolean,
        speedAdjustment: Double,
    ): RouteOption {
        val distanceMeters = geometry.points
            .zipWithNext(::distanceMeters)
            .sum()
            .roundToInt()
            .coerceAtLeast(MINIMUM_DISTANCE_METERS)
        val metersPerMinute = when (travelMode) {
            TravelMode.CAR -> CAR_METERS_PER_MINUTE
            TravelMode.MOTORCYCLE -> MOTORCYCLE_METERS_PER_MINUTE
        } * speedAdjustment
        val etaMinutes = ceil(distanceMeters / metersPerMinute)
            .toInt()
            .coerceAtLeast(
                when (travelMode) {
                    TravelMode.CAR -> MINIMUM_CAR_ETA_MINUTES
                    TravelMode.MOTORCYCLE -> MINIMUM_MOTORCYCLE_ETA_MINUTES
                },
            )

        return RouteOption(
            id = id,
            geometry = geometry,
            summary = RouteSummary(
                distanceMeters = distanceMeters,
                etaMinutes = etaMinutes,
            ),
            isRecommended = isRecommended,
        )
    }

    private fun createGeometry(
        origin: GeoPoint,
        destination: GeoPoint,
        bendFactor: Double,
    ): RouteGeometry {
        val latitudeDelta = destination.latitude - origin.latitude
        val longitudeDelta = destination.longitude - origin.longitude
        val points = ROUTE_FRACTIONS.map { fraction ->
            val bend = sin(PI * fraction) * bendFactor
            GeoPoint(
                latitude = origin.latitude + latitudeDelta * fraction - longitudeDelta * bend,
                longitude = origin.longitude + longitudeDelta * fraction + latitudeDelta * bend,
            )
        }
        return RouteGeometry(points)
    }

    private fun distanceMeters(
        first: GeoPoint,
        second: GeoPoint,
    ): Double {
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val latitudeDelta = secondLatitude - firstLatitude
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val haversine = sin(latitudeDelta / 2).let { it * it } +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2).let { it * it }
        return EARTH_RADIUS_METERS * 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    private companion object {
        const val DEFAULT_LOADING_DELAY_MILLIS = 550L
        const val PRIMARY_ROUTE_ID = "gathra-demo-primary"
        const val ALTERNATIVE_ROUTE_ID = "gathra-demo-alternative"
        const val PRIMARY_BEND_FACTOR = 0.10
        const val ALTERNATIVE_BEND_FACTOR = -0.28
        const val ALTERNATIVE_SPEED_ADJUSTMENT = 0.90
        const val CAR_METERS_PER_MINUTE = 475.0
        const val MOTORCYCLE_METERS_PER_MINUTE = 600.0
        const val MINIMUM_DISTANCE_METERS = 100
        const val MINIMUM_CAR_ETA_MINUTES = 3
        const val MINIMUM_MOTORCYCLE_ETA_MINUTES = 2
        const val EARTH_RADIUS_METERS = 6_371_000.0

        val ROUTE_FRACTIONS = listOf(0.0, 0.22, 0.48, 0.74, 1.0)
    }
}
