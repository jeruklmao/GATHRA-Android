package opsi.sman35jkt.gathra.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.domain.sensor.SensorCurrentState

class SensorMapFeatureTest {
    @Test fun `sensor marker geometry uses Backend detail coordinate and visual state`() {
        val feature = sensorFeatureCollection(sensor()).features().orEmpty().single()
        val point = feature.geometry() as org.maplibre.geojson.Point
        assertEquals(106.720401, point.longitude(), 0.0)
        assertEquals(-6.235149, point.latitude(), 0.0)
        assertEquals("GTH-10003BD4BCFC", feature.getStringProperty("nodeId"))
        assertEquals("FRESH", feature.getStringProperty("sensorVisualState"))

        val stale = sensorFeatureCollection(sensor(freshness = FloodHazardFreshness.STALE))
            .features().orEmpty().single()
        assertEquals("STALE", stale.getStringProperty("sensorVisualState"))
        val unknown = sensorFeatureCollection(sensor(level = FloodHazardLevel.UNKNOWN))
            .features().orEmpty().single()
        assertEquals("UNKNOWN", unknown.getStringProperty("sensorVisualState"))
    }

    @Test fun `initial sensor polygon fit is claimed once and never by later refreshes`() {
        val gate = InitialFloodCameraPolicy()
        val snapshot = snapshot()
        val first = gate.claim(snapshot, cameraOwnedByRoute = false)
        assertEquals(3, first?.size)
        assertNull(gate.claim(snapshot.copy(snapshotId = "sensor-refresh"), false))
        assertNull(gate.claim(snapshot.copy(snapshotId = "gateway-refresh"), false))
        assertTrue(first!!.none { it == GeoPoint(-6.2, 106.8) })
    }

    @Test fun `user ownership prevents a late initial fit`() {
        val gate = InitialFloodCameraPolicy()
        gate.takeUserOwnership()
        assertNull(gate.claim(snapshot(), cameraOwnedByRoute = false))
        assertTrue(gate.isUserOwned)
    }
}

private fun sensor(
    freshness: FloodHazardFreshness = FloodHazardFreshness.FRESH,
    level: FloodHazardLevel = FloodHazardLevel.LOW,
) = SensorCurrentState(
    "GTH-10003BD4BCFC", GeoPoint(-6.235149, 106.720401), 123, level,
    freshness, 1L, 1602, 31.2, 72.4, null,
)

private fun snapshot() = FloodHazardSnapshot(
    "initial", 1L, null, FloodHazardSource.SENSOR,
    listOf(
        FloodHazardPolygon(
            "sensor", FloodHazardLevel.LOW,
            listOf(listOf(
                GeoPoint(-6.235, 106.720), GeoPoint(-6.236, 106.722),
                GeoPoint(-6.234, 106.723), GeoPoint(-6.235, 106.720),
            )), null, null, 1L, 2L, FloodHazardSource.SENSOR,
            listOf("GTH-10003BD4BCFC"), 1.0, emptyList(), FloodHazardFreshness.FRESH,
        ),
    ),
)
