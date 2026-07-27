package opsi.sman35jkt.gathra.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryMatcherTest {
    private val matcher = RouteGeometryMatcher(testRoute().geometry)

    @Test
    fun `projects location onto route segment rather than nearest vertex`() {
        val match = matcher.match(
            location(
                latitude = 0.0001,
                longitude = 0.0005,
                elapsedRealtimeMillis = 1_000L,
            ),
        )

        assertEquals(0, match.segmentIndex)
        assertEquals(0.5, match.segmentFraction, 0.02)
        assertEquals(0.0, match.matchedLocation.latitude, 0.00001)
        assertEquals(0.0005, match.matchedLocation.longitude, 0.00001)
        assertEquals(11.1, match.distanceFromRouteMeters, 0.8)
        assertEquals(55.6, match.distanceAlongRouteMeters, 1.0)
    }

    @Test
    fun `continuity prevents a large backward progress jump`() {
        val forward = matcher.match(
            location(
                latitude = 0.0,
                longitude = 0.0025,
                elapsedRealtimeMillis = 1_000L,
            ),
        )
        val noisyBackward = matcher.match(
            location(
                latitude = 0.0,
                longitude = 0.0002,
                elapsedRealtimeMillis = 2_000L,
            ),
            previousMatch = forward,
        )

        assertTrue(
            noisyBackward.distanceAlongRouteMeters >=
                forward.distanceAlongRouteMeters - 20.0,
        )
    }

    @Test
    fun `reliable movement bearing participates in candidate matching`() {
        val bearingAwareMatcher = RouteGeometryMatcher(
            geometry = opsi.sman35jkt.gathra.core.model.RouteGeometry(
                listOf(
                    opsi.sman35jkt.gathra.core.model.GeoPoint(0.0, -0.001),
                    opsi.sman35jkt.gathra.core.model.GeoPoint(0.0, 0.001),
                    opsi.sman35jkt.gathra.core.model.GeoPoint(0.001, 0.0),
                    opsi.sman35jkt.gathra.core.model.GeoPoint(-0.001, 0.0),
                ),
            ),
        )

        val match = bearingAwareMatcher.match(
            location(
                latitude = 0.0,
                longitude = 0.0,
                elapsedRealtimeMillis = 1_000L,
                bearingDegrees = 180.0,
                speedMetersPerSecond = 5.0,
            ),
        )

        assertEquals(2, match.segmentIndex)
    }
}

