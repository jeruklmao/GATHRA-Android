package opsi.sman35jkt.gathra.data.flood

import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import opsi.sman35jkt.gathra.core.model.GeoBounds
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.domain.flood.FloodHazardRepository

class FakeFloodHazardRepository(
    var simulatedHazards: List<FloodHazardPolygon> = defaultFakeHazards(),
    var snapshotId: String = "fake_snapshot_v1_2",
) : FloodHazardRepository {

    override suspend fun getActiveHazards(bounds: GeoBounds?): FloodHazardSnapshot {
        val now = System.currentTimeMillis()
        val active = simulatedHazards.filter { h ->
            h.validUntilEpochMillis == null || h.validUntilEpochMillis > now
        }
        val filtered = if (bounds != null) {
            active.filter { h -> h.intersects(bounds) }
        } else {
            active
        }

        return FloodHazardSnapshot(
            snapshotId = snapshotId,
            generatedAtEpochMillis = now,
            validUntilEpochMillis = now + 86400000L,
            source = FloodHazardSource.SIMULATED,
            hazards = filtered,
        )
    }

    companion object {
        fun defaultFakeHazards(): List<FloodHazardPolygon> {
            val now = System.currentTimeMillis()
            val validUntil = now + 86400000L // 24 hours

            return listOf(
                FloodHazardPolygon(
                    id = "fake_hazard_low_01",
                    level = FloodHazardLevel.LOW,
                    rings = listOf(
                        listOf(
                            GeoPoint(latitude = -6.21, longitude = 106.81),
                            GeoPoint(latitude = -6.21, longitude = 106.815),
                            GeoPoint(latitude = -6.205, longitude = 106.815),
                            GeoPoint(latitude = -6.205, longitude = 106.81),
                            GeoPoint(latitude = -6.21, longitude = 106.81),
                        ),
                    ),
                    confidence = 0.85,
                    description = "Area genangan air dangkal simulasi",
                    observedAtEpochMillis = now,
                    validUntilEpochMillis = validUntil,
                    source = FloodHazardSource.SIMULATED,
                    sourceNodeIds = listOf("fake_node_01"),
                ),
                FloodHazardPolygon(
                    id = "fake_hazard_high_01",
                    level = FloodHazardLevel.HIGH,
                    rings = listOf(
                        listOf(
                            GeoPoint(latitude = -6.201, longitude = 106.817),
                            GeoPoint(latitude = -6.201, longitude = 106.821),
                            GeoPoint(latitude = -6.193, longitude = 106.821),
                            GeoPoint(latitude = -6.193, longitude = 106.817),
                            GeoPoint(latitude = -6.201, longitude = 106.817),
                        ),
                    ),
                    confidence = 0.95,
                    description = "Area terindikasi banjir tinggi simulasi Koridor Sudirman",
                    observedAtEpochMillis = now,
                    validUntilEpochMillis = validUntil,
                    source = FloodHazardSource.SIMULATED,
                    sourceNodeIds = listOf("fake_node_02", "fake_node_03"),
                ),
            )
        }
    }
}

private fun FloodHazardPolygon.intersects(bounds: GeoBounds): Boolean {
    for (ring in rings) {
        for (point in ring) {
            if (
                point.latitude in bounds.minLat..bounds.maxLat &&
                point.longitude in bounds.minLon..bounds.maxLon
            ) {
                return true
            }
        }
    }
    return false
}
