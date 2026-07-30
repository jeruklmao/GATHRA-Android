package opsi.sman35jkt.gathra.data.flood.remote

import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FloodDtoMapperTest {
    @Test
    fun `strict mapper preserves UNKNOWN instead of downgrading it to LOW`() {
        val snapshot = validResponse(
            feature = validFeature(
                properties = validProperties().copy(riskLevel = "UNKNOWN"),
            ),
        ).toDomain()

        assertEquals(FloodHazardLevel.UNKNOWN, snapshot.hazards.single().level)
    }

    @Test
    fun `strict mapper rejects the entire snapshot when one polygon ring is open`() {
        val malformed = validFeature().copy(
            geometry = GeoJsonPolygonGeometryDto(
                type = "Polygon",
                coordinates = listOf(
                    listOf(
                        listOf(106.8, -6.2),
                        listOf(106.81, -6.2),
                        listOf(106.81, -6.19),
                        listOf(106.8, -6.19),
                    ),
                ),
            ),
        )

        assertThrows(InvalidFloodResponseException::class.java) {
            validResponse(
                features = listOf(validFeature(id = "valid"), malformed),
            ).toDomain()
        }
    }

    @Test
    fun `strict mapper rejects invalid confidence timestamp and identifiers`() {
        val invalidFeatures = listOf(
            validFeature(id = "contains whitespace"),
            validFeature(
                properties = validProperties().copy(confidence = 1.5),
            ),
            validFeature(
                properties = validProperties().copy(observedAt = "not-a-date"),
            ),
        )

        invalidFeatures.forEach { feature ->
            assertThrows(InvalidFloodResponseException::class.java) {
                validResponse(feature = feature).toDomain()
            }
        }
    }

    @Test
    fun `strict mapper requires valid collection metadata`() {
        assertThrows(InvalidFloodResponseException::class.java) {
            validResponse().copy(type = "Feature").toDomain()
        }
        assertThrows(InvalidFloodResponseException::class.java) {
            validResponse().copy(snapshotId = "").toDomain()
        }
        assertThrows(InvalidFloodResponseException::class.java) {
            validResponse().copy(generatedAt = "invalid").toDomain()
        }
    }

    private fun validResponse(
        feature: FloodHazardFeatureDto = validFeature(),
        features: List<FloodHazardFeatureDto> = listOf(feature),
    ) = FloodHazardsResponseDto(
        type = "FeatureCollection",
        snapshotId = "snapshot_v1_1",
        generatedAt = "2026-07-30T10:00:00.000Z",
        validUntil = "2026-07-30T11:00:00.000Z",
        source = "SIMULATED",
        features = features,
    )

    private fun validFeature(
        id: String = "hazard_1",
        properties: FloodHazardPropertiesDto = validProperties(),
    ) = FloodHazardFeatureDto(
        type = "Feature",
        id = id,
        properties = properties,
        geometry = GeoJsonPolygonGeometryDto(
            type = "Polygon",
            coordinates = listOf(
                listOf(
                    listOf(106.8, -6.2),
                    listOf(106.81, -6.2),
                    listOf(106.81, -6.19),
                    listOf(106.8, -6.19),
                    listOf(106.8, -6.2),
                ),
            ),
        ),
    )

    private fun validProperties() = FloodHazardPropertiesDto(
        riskLevel = "HIGH",
        confidence = 0.9,
        description = "Data simulasi",
        observedAt = "2026-07-30T10:00:00.000Z",
        validUntil = "2026-07-30T11:00:00.000Z",
        source = "SIMULATED",
        sourceNodeIds = listOf("node_1"),
    )
}
