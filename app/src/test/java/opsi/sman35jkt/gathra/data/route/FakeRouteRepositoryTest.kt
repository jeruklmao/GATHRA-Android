package opsi.sman35jkt.gathra.data.route

import kotlinx.coroutines.runBlocking
import opsi.sman35jkt.gathra.core.map.TestRoutePoints
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.core.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRouteRepositoryTest {

    private val carRequest = RouteRequest(
        origin = TestRoutePoints.origin,
        destination = TestRoutePoints.destination,
        travelMode = TravelMode.CAR,
    )

    @Test
    fun `returns exactly two stable route alternatives`() = runBlocking {
        val routes = FakeRouteRepository(loadingDelayMillis = 0).getRoutes(carRequest)

        assertEquals(2, routes.size)
        assertEquals(
            listOf("gathra-demo-primary", "gathra-demo-alternative"),
            routes.map { it.id },
        )
        assertTrue(routes.first().isRecommended)
        assertTrue(routes.drop(1).none { it.isRecommended })
    }

    @Test
    fun `alternatives have distinct geometries and summaries`() = runBlocking {
        val routes = FakeRouteRepository(loadingDelayMillis = 0).getRoutes(carRequest)

        assertNotEquals(routes[0].geometry, routes[1].geometry)
        assertNotEquals(routes[0].summary.distanceMeters, routes[1].summary.distanceMeters)
        assertNotEquals(routes[0].summary.etaMinutes, routes[1].summary.etaMinutes)
    }

    @Test
    fun `motorcycle mode produces lower ETA than car mode`() = runBlocking {
        val repository = FakeRouteRepository(loadingDelayMillis = 0)
        val carRoutes = repository.getRoutes(carRequest)
        val motorcycleRoutes = repository.getRoutes(
            carRequest.copy(travelMode = TravelMode.MOTORCYCLE),
        )

        carRoutes.zip(motorcycleRoutes).forEach { (car, motorcycle) ->
            assertTrue(
                "${motorcycle.id} should be faster for motorcycle demo data",
                motorcycle.summary.etaMinutes < car.summary.etaMinutes,
            )
        }
    }

    @Test
    fun `isolated failure mode throws a route data error`() {
        val repository = FakeRouteRepository(
            loadingDelayMillis = 0,
            failureMode = FakeRouteFailureMode.ALWAYS_FAIL,
        )

        assertThrows(FakeRouteDataException::class.java) {
            runBlocking {
                repository.getRoutes(carRequest)
            }
        }
    }
}
