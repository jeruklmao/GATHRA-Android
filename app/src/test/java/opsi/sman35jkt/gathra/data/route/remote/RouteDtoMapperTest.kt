package opsi.sman35jkt.gathra.data.route.remote

import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.ManeuverType
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.core.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RouteDtoMapperTest {

    @Test
    fun `request preserves domain coordinates and asks for one alternative`() {
        val dto = RouteDtoMapper.toRequest(request)

        assertEquals(-6.2, dto.origin.latitude, 0.0)
        assertEquals(106.8167, dto.origin.longitude, 0.0)
        assertEquals("CAR", dto.travelMode)
        assertEquals(1, dto.alternatives)
    }

    @Test
    fun `response maps GeoJSON longitude latitude order and rounds ETA up`() {
        val routes = RouteDtoMapper.toDomain(validResponse(), request)

        assertEquals(2, routes.size)
        assertEquals(
            GeoPoint(latitude = -6.2, longitude = 106.8167),
            routes.first().geometry.points.first(),
        )
        assertEquals(
            GeoPoint(latitude = -6.1754, longitude = 106.8272),
            routes.first().geometry.points.last(),
        )
        assertEquals(12, routes.first().summary.etaMinutes)
        assertEquals(676, routes.first().summary.durationSeconds)
        assertEquals(13, routes.last().summary.etaMinutes)
        assertEquals(2, routes.first().steps.size)
        assertEquals(ManeuverType.DEPART, routes.first().steps.first().maneuver.type)
        assertEquals(90, routes.first().steps.first().maneuver.bearingAfter)
        assertEquals(ManeuverType.ARRIVE, routes.first().steps.last().maneuver.type)
    }

    @Test
    fun `response rejects a non LineString geometry`() {
        val response = validResponse().let { value ->
            value.copy(
                routes = value.routes.mapIndexed { index, route ->
                    if (index == 0) {
                        route.copy(geometry = route.geometry.copy(type = "Polygon"))
                    } else {
                        route
                    }
                },
            )
        }

        assertThrows(InvalidRouteResponseException::class.java) {
            RouteDtoMapper.toDomain(response, request)
        }
    }

    @Test
    fun `response rejects out of range coordinates`() {
        val response = validResponse().let { value ->
            value.copy(
                routes = value.routes.mapIndexed { index, route ->
                    if (index == 0) {
                        route.copy(
                            geometry = route.geometry.copy(
                                coordinates = listOf(
                                    listOf(206.0, -6.2),
                                    listOf(106.8272, -6.1754),
                                ),
                            ),
                        )
                    } else {
                        route
                    }
                },
            )
        }

        assertThrows(InvalidRouteResponseException::class.java) {
            RouteDtoMapper.toDomain(response, request)
        }
    }

    @Test
    fun `response rejects duplicate route IDs`() {
        val response = validResponse().let { value ->
            value.copy(
                routes = value.routes.map { it.copy(id = "same-route") },
            )
        }

        assertThrows(InvalidRouteResponseException::class.java) {
            RouteDtoMapper.toDomain(response, request)
        }
    }

    @Test
    fun `response rejects an unknown manoeuvre enum`() {
        val response = validResponse().let { value ->
            value.copy(
                routes = value.routes.mapIndexed { index, route ->
                    if (index == 0) {
                        route.copy(
                            steps = route.steps.mapIndexed { stepIndex, step ->
                                if (stepIndex == 0) {
                                    step.copy(
                                        manoeuvre = step.manoeuvre.copy(
                                            type = "GRAPH_HOPPER_PRIVATE_SIGN",
                                        ),
                                    )
                                } else {
                                    step
                                }
                            },
                        )
                    } else {
                        route
                    }
                },
            )
        }

        assertThrows(InvalidRouteResponseException::class.java) {
            RouteDtoMapper.toDomain(response, request)
        }
    }

    @Test
    fun `response rejects a step interval outside the geometry`() {
        val response = validResponse().let { value ->
            value.copy(
                routes = value.routes.mapIndexed { index, route ->
                    if (index == 0) {
                        route.copy(
                            steps = route.steps.mapIndexed { stepIndex, step ->
                                if (stepIndex == 0) {
                                    step.copy(geometryEndIndex = 99)
                                } else {
                                    step
                                }
                            },
                        )
                    } else {
                        route
                    }
                },
            )
        }

        assertThrows(InvalidRouteResponseException::class.java) {
            RouteDtoMapper.toDomain(response, request)
        }
    }

    @Test
    fun `response requires the final navigation step to arrive`() {
        val response = validResponse().let { value ->
            value.copy(
                routes = value.routes.mapIndexed { index, route ->
                    if (index == 0) {
                        route.copy(
                            steps = route.steps.mapIndexed { stepIndex, step ->
                                if (stepIndex == route.steps.lastIndex) {
                                    step.copy(
                                        manoeuvre = step.manoeuvre.copy(
                                            type = "CONTINUE",
                                            modifier = "STRAIGHT",
                                        ),
                                    )
                                } else {
                                    step
                                }
                            },
                        )
                    } else {
                        route
                    }
                },
            )
        }

        assertThrows(InvalidRouteResponseException::class.java) {
            RouteDtoMapper.toDomain(response, request)
        }
    }

    private fun validResponse() = RoutePreviewResponseDto(
        requestId = "request-1",
        routes = listOf(
            RouteResponseDto(
                id = "route-primary",
                isRecommended = true,
                geometry = GeoJsonLineStringDto(
                    type = "LineString",
                    coordinates = listOf(
                        listOf(106.8167, -6.2),
                        listOf(106.8210, -6.19),
                        listOf(106.8272, -6.1754),
                    ),
                ),
                summary = RouteSummaryResponseDto(
                    distanceMeters = 4_128,
                    durationSeconds = 676,
                ),
                steps = validSteps(lastGeometryIndex = 2),
            ),
            RouteResponseDto(
                id = "route-alternative",
                isRecommended = false,
                geometry = GeoJsonLineStringDto(
                    type = "LineString",
                    coordinates = listOf(
                        listOf(106.8167, -6.2),
                        listOf(106.8139, -6.1887),
                        listOf(106.8272, -6.1754),
                    ),
                ),
                summary = RouteSummaryResponseDto(
                    distanceMeters = 4_583,
                    durationSeconds = 731,
                ),
                steps = validSteps(lastGeometryIndex = 2),
            ),
        ),
        metadata = RouteMetadataDto(
            travelMode = "CAR",
            requestedAlternatives = 1,
            returnedAlternatives = 1,
        ),
    )

    private fun validSteps(lastGeometryIndex: Int) = listOf(
        RouteStepResponseDto(
            index = 0,
            instruction = "Mulai mengikuti rute",
            streetName = "Jalan Demo",
            distanceMeters = 4_128,
            durationSeconds = 676,
            manoeuvre = RouteManeuverResponseDto(
                type = "DEPART",
                modifier = "STRAIGHT",
                bearingBefore = null,
                bearingAfter = 90,
            ),
            geometryStartIndex = 0,
            geometryEndIndex = lastGeometryIndex,
        ),
        RouteStepResponseDto(
            index = 1,
            instruction = "Anda telah tiba",
            streetName = "",
            distanceMeters = 0,
            durationSeconds = 0,
            manoeuvre = RouteManeuverResponseDto(
                type = "ARRIVE",
                modifier = "NONE",
                bearingBefore = 90,
                bearingAfter = null,
            ),
            geometryStartIndex = lastGeometryIndex,
            geometryEndIndex = lastGeometryIndex,
        ),
    )

    private val request = RouteRequest(
        origin = GeoPoint(latitude = -6.2, longitude = 106.8167),
        destination = GeoPoint(latitude = -6.1754, longitude = 106.8272),
        travelMode = TravelMode.CAR,
    )
}
