package opsi.sman35jkt.gathra.core.map

import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import opsi.sman35jkt.gathra.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class FloodMapFeatureTest {
    @Test
    fun `GeoJSON properties preserve risk freshness and routing multiplier`() {
        val cases = listOf(
            Triple(FloodHazardLevel.LOW, FloodHazardFreshness.FRESH, 1.0),
            Triple(FloodHazardLevel.UNKNOWN, FloodHazardFreshness.FRESH, 0.35),
            Triple(FloodHazardLevel.UNKNOWN, FloodHazardFreshness.STALE, 0.05),
            Triple(FloodHazardLevel.UNKNOWN, FloodHazardFreshness.NO_TELEMETRY, 0.0),
        )

        cases.forEachIndexed { index, (risk, freshness, multiplier) ->
            val feature = floodFeatureCollection(
                snapshot(hazard(index.toString(), risk, freshness, multiplier)),
            ).features().orEmpty().single()

            assertEquals(risk.name, feature.getStringProperty("riskLevel"))
            assertEquals(freshness.name, feature.getStringProperty("freshness"))
            assertEquals(multiplier, feature.getNumberProperty("routingMultiplier").toDouble(), 0.0)
        }
    }

    @Test
    fun `UNKNOWN fresh is neutral and stale lifecycle states remain visible as uncertain`() {
        assertEquals(
            "UNKNOWN",
            hazard("fresh-unknown", FloodHazardLevel.UNKNOWN, FloodHazardFreshness.FRESH, 1.0)
                .floodMapVisualState(),
        )
        assertEquals(
            "STALE",
            hazard("stale", FloodHazardLevel.UNKNOWN, FloodHazardFreshness.STALE, 1.0)
                .floodMapVisualState(),
        )
        assertEquals(
            "NO_TELEMETRY",
            hazard(
                "no-telemetry",
                FloodHazardLevel.UNKNOWN,
                FloodHazardFreshness.NO_TELEMETRY,
                1.0,
            ).floodMapVisualState(),
        )
    }

    private fun snapshot(hazard: FloodHazardPolygon) = FloodHazardSnapshot(
        snapshotId = "snapshot-test",
        generatedAtEpochMillis = 1_000L,
        validUntilEpochMillis = null,
        source = FloodHazardSource.SENSOR,
        hazards = listOf(hazard),
    )

    private fun hazard(
        id: String,
        level: FloodHazardLevel,
        freshness: FloodHazardFreshness,
        multiplier: Double,
    ) = FloodHazardPolygon(
        id = id,
        level = level,
        rings = listOf(
            listOf(
                GeoPoint(-6.2, 106.8),
                GeoPoint(-6.2, 106.81),
                GeoPoint(-6.19, 106.81),
                GeoPoint(-6.2, 106.8),
            ),
        ),
        confidence = null,
        description = null,
        observedAtEpochMillis = null,
        validUntilEpochMillis = null,
        source = FloodHazardSource.SENSOR,
        sourceNodeIds = listOf("GTH-10003BD4BCFC"),
        routingMultiplier = multiplier,
        reasonCodes = emptyList(),
        freshness = freshness,
    )
}
