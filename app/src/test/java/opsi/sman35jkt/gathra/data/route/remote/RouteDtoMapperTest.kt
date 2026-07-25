package opsi.sman35jkt.gathra.data.route.remote

import opsi.sman35jkt.gathra.core.model.GeoPoint
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
        assertEquals(13, routes.last().summary.etaMinutes)
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
            ),
        ),
        metadata = RouteMetadataDto(
            travelMode = "CAR",
            requestedAlternatives = 1,
            returnedAlternatives = 1,
        ),
    )

    private val request = RouteRequest(
        origin = GeoPoint(latitude = -6.2, longitude = 106.8167),
        destination = GeoPoint(latitude = -6.1754, longitude = 106.8272),
        travelMode = TravelMode.CAR,
    )
}
