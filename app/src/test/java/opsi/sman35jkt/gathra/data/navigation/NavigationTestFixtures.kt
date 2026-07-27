package opsi.sman35jkt.gathra.data.navigation

import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.ManeuverModifier
import opsi.sman35jkt.gathra.core.model.ManeuverType
import opsi.sman35jkt.gathra.core.model.RouteGeometry
import opsi.sman35jkt.gathra.core.model.RouteManeuver
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteStep
import opsi.sman35jkt.gathra.core.model.RouteSummary
import opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample

internal fun testRoute(): RouteOption {
    val points = listOf(
        GeoPoint(latitude = 0.0, longitude = 0.0),
        GeoPoint(latitude = 0.0, longitude = 0.001),
        GeoPoint(latitude = 0.0, longitude = 0.002),
        GeoPoint(latitude = 0.0, longitude = 0.003),
    )
    return RouteOption(
        id = "test-route",
        geometry = RouteGeometry(points),
        summary = RouteSummary(
            distanceMeters = 334,
            etaMinutes = 3,
            durationSeconds = 180,
        ),
        isRecommended = true,
        steps = listOf(
            testStep(
                index = 0,
                type = ManeuverType.DEPART,
                modifier = ManeuverModifier.STRAIGHT,
                startIndex = 0,
                endIndex = 1,
            ),
            testStep(
                index = 1,
                type = ManeuverType.TURN,
                modifier = ManeuverModifier.RIGHT,
                startIndex = 1,
                endIndex = 2,
            ),
            testStep(
                index = 2,
                type = ManeuverType.CONTINUE,
                modifier = ManeuverModifier.STRAIGHT,
                startIndex = 2,
                endIndex = 3,
            ),
            testStep(
                index = 3,
                type = ManeuverType.ARRIVE,
                modifier = ManeuverModifier.NONE,
                startIndex = 3,
                endIndex = 3,
                distanceMeters = 0,
                durationSeconds = 0,
            ),
        ),
    )
}

internal fun testStep(
    index: Int = 0,
    type: ManeuverType = ManeuverType.TURN,
    modifier: ManeuverModifier = ManeuverModifier.RIGHT,
    startIndex: Int = index,
    endIndex: Int = index + 1,
    distanceMeters: Int = 111,
    durationSeconds: Int = 60,
): RouteStep = RouteStep(
    index = index,
    instruction = when (type) {
        ManeuverType.ARRIVE -> "Anda telah tiba"
        ManeuverType.DEPART -> "Mulai mengikuti rute"
        else -> "Belok kanan"
    },
    streetName = if (type == ManeuverType.ARRIVE) "" else "Jalan Uji",
    distanceMeters = distanceMeters,
    durationSeconds = durationSeconds,
    maneuver = RouteManeuver(
        type = type,
        modifier = modifier,
        bearingBefore = null,
        bearingAfter = null,
    ),
    geometryStartIndex = startIndex,
    geometryEndIndex = endIndex,
)

internal fun location(
    latitude: Double,
    longitude: Double,
    elapsedRealtimeMillis: Long,
    accuracyMeters: Double = 5.0,
    bearingDegrees: Double? = null,
    speedMetersPerSecond: Double? = null,
): NavigationLocationSample = NavigationLocationSample(
    point = GeoPoint(latitude, longitude),
    accuracyMeters = accuracyMeters,
    bearingDegrees = bearingDegrees,
    speedMetersPerSecond = speedMetersPerSecond,
    elapsedRealtimeMillis = elapsedRealtimeMillis,
    epochTimeMillis = 1_700_000_000_000L + elapsedRealtimeMillis,
)

