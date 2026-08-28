package opsi.sman35jkt.gathra.data.flood.remote

import kotlinx.serialization.json.Json
import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FloodDtoMapperTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @Test
    fun `FRESH LOW sensor JSON preserves the production contract`() {
        val snapshot = decodeSensorFeature(
            properties = productionProperties(
                riskLevel = "LOW",
                freshness = "FRESH",
                routingMultiplier = 1.0,
                reasonCodes = emptyList(),
            ),
        ).toDomain()

        val hazard = snapshot.hazards.single()
        assertEquals(FloodHazardLevel.LOW, hazard.level)
        assertEquals(FloodHazardFreshness.FRESH, hazard.freshness)
        assertEquals(1.0, hazard.routingMultiplier, 0.0)
        assertEquals(emptyList<String>(), hazard.reasonCodes)
        assertEquals(1_787_935_454_836L, hazard.observedAtEpochMillis)
        assertEquals(1_787_937_254_836L, hazard.validUntilEpochMillis)
        assertEquals(FloodHazardSource.SENSOR, hazard.source)
        assertEquals(listOf("GTH-10003BD4BCFC"), hazard.sourceNodeIds)
    }

    @Test
    fun `STALE sensor remains UNKNOWN and preserves expired validity`() {
        val hazard = decodeSensorFeature(
            generatedAt = "2026-08-28T17:30:00.000Z",
            collectionValidUntil = null,
            properties = productionProperties(
                riskLevel = "UNKNOWN",
                freshness = "STALE",
                routingMultiplier = 1.0,
                reasonCodes = listOf("STALE"),
            ),
        ).toDomain().hazards.single()

        assertEquals(FloodHazardLevel.UNKNOWN, hazard.level)
        assertEquals(FloodHazardFreshness.STALE, hazard.freshness)
        assertEquals(listOf("STALE"), hazard.reasonCodes)
        assertTrue(hazard.observedAtEpochMillis != null)
        assertTrue(hazard.validUntilEpochMillis != null)
    }

    @Test
    fun `NO_TELEMETRY with null timestamps parses successfully`() {
        val hazard = decodeSensorFeature(
            collectionValidUntil = null,
            properties = productionProperties(
                riskLevel = "UNKNOWN",
                freshness = "NO_TELEMETRY",
                routingMultiplier = 1.0,
                reasonCodes = listOf("NO_TELEMETRY"),
                observedAt = null,
                validUntil = null,
            ),
        ).toDomain().hazards.single()

        assertEquals(FloodHazardLevel.UNKNOWN, hazard.level)
        assertEquals(FloodHazardFreshness.NO_TELEMETRY, hazard.freshness)
        assertEquals(listOf("NO_TELEMETRY"), hazard.reasonCodes)
        assertNull(hazard.observedAtEpochMillis)
        assertNull(hazard.validUntilEpochMillis)
    }

    @Test
    fun `FRESH UNKNOWN sensor health state is not downgraded to LOW`() {
        val hazard = decodeSensorFeature(
            properties = productionProperties(
                riskLevel = "UNKNOWN",
                freshness = "FRESH",
                routingMultiplier = 1.0,
                reasonCodes = listOf("SENSOR_UNHEALTHY"),
            ),
        ).toDomain().hazards.single()

        assertEquals(FloodHazardLevel.UNKNOWN, hazard.level)
        assertEquals(FloodHazardFreshness.FRESH, hazard.freshness)
        assertEquals(listOf("SENSOR_UNHEALTHY"), hazard.reasonCodes)
    }

    @Test
    fun `routing multiplier is independent from risk level`() {
        listOf(1.0, 0.35, 0.05, 0.0).forEach { multiplier ->
            val hazard = validResponse(
                feature = validFeature(
                    properties = validProperties().copy(
                        riskLevel = "LOW",
                        routingMultiplier = multiplier,
                    ),
                ),
            ).toDomain().hazards.single()

            assertEquals(FloodHazardLevel.LOW, hazard.level)
            assertEquals(multiplier, hazard.routingMultiplier, 0.0)
        }
    }

    @Test
    fun `null timestamps are distinct from malformed non-null timestamps`() {
        val nullTimestamps = validResponse(
            validUntil = null,
            feature = validFeature(
                properties = validProperties().copy(
                    observedAt = null,
                    validUntil = null,
                    freshness = "NO_TELEMETRY",
                    reasonCodes = listOf("NO_TELEMETRY"),
                    riskLevel = "UNKNOWN",
                ),
            ),
        ).toDomain()
        assertNull(nullTimestamps.validUntilEpochMillis)
        assertNull(nullTimestamps.hazards.single().observedAtEpochMillis)
        assertNull(nullTimestamps.hazards.single().validUntilEpochMillis)

        listOf(
            validProperties().copy(observedAt = "not-a-date"),
            validProperties().copy(validUntil = "not-a-date"),
        ).forEach { properties ->
            assertThrows(InvalidFloodResponseException::class.java) {
                validResponse(feature = validFeature(properties = properties)).toDomain()
            }
        }
    }

    @Test
    fun `unknown future reason code is preserved for safe presentation fallback`() {
        val hazard = validResponse(
            feature = validFeature(
                properties = validProperties().copy(
                    reasonCodes = listOf("FUTURE_SENSOR_REASON"),
                ),
            ),
        ).toDomain().hazards.single()

        assertEquals(listOf("FUTURE_SENSOR_REASON"), hazard.reasonCodes)
    }

    @Test
    fun `strict mapper rejects invalid routing multiplier and freshness`() {
        listOf(-0.01, 1.01, Double.NaN).forEach { multiplier ->
            assertThrows(InvalidFloodResponseException::class.java) {
                validResponse(
                    feature = validFeature(
                        properties = validProperties().copy(
                            routingMultiplier = multiplier,
                        ),
                    ),
                ).toDomain()
            }
        }
        assertThrows(InvalidFloodResponseException::class.java) {
            validResponse(
                feature = validFeature(
                    properties = validProperties().copy(freshness = "FUTURE_FRESHNESS"),
                ),
            ).toDomain()
        }
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
            validResponse(features = listOf(validFeature(id = "valid"), malformed)).toDomain()
        }
    }

    @Test
    fun `strict mapper requires valid collection metadata`() {
        listOf(
            validResponse().copy(type = "Feature"),
            validResponse().copy(snapshotId = ""),
            validResponse().copy(generatedAt = "invalid"),
        ).forEach { response ->
            assertThrows(InvalidFloodResponseException::class.java) {
                response.toDomain()
            }
        }
    }

    private fun decodeSensorFeature(
        generatedAt: String = "2026-08-28T16:47:28.217Z",
        collectionValidUntil: String? = "2026-08-28T17:14:14.836Z",
        properties: String,
    ): FloodHazardsResponseDto {
        val collectionValidity = collectionValidUntil?.let { "\"$it\"" } ?: "null"
        return json.decodeFromString(
            """
            {
              "type": "FeatureCollection",
              "snapshotId": "sensor_snapshot_e5dff66b26499bd780694f46",
              "generatedAt": "$generatedAt",
              "validUntil": $collectionValidity,
              "source": "SENSOR",
              "features": [{
                "type": "Feature",
                "id": "sensor_GTH-10003BD4BCFC",
                "properties": $properties,
                "geometry": {
                  "type": "Polygon",
                  "coordinates": [[
                    [106.7161, -6.2263],
                    [106.7198, -6.2253],
                    [106.7242, -6.2269],
                    [106.7161, -6.2263]
                  ]]
                }
              }]
            }
            """.trimIndent(),
        )
    }

    private fun productionProperties(
        riskLevel: String,
        freshness: String,
        routingMultiplier: Double,
        reasonCodes: List<String>,
        observedAt: String? = "2026-08-28T16:44:14.836Z",
        validUntil: String? = "2026-08-28T17:14:14.836Z",
    ): String {
        val observed = observedAt?.let { "\"$it\"" } ?: "null"
        val validity = validUntil?.let { "\"$it\"" } ?: "null"
        val reasons = reasonCodes.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        return """
            {
              "riskLevel": "$riskLevel",
              "confidence": 1,
              "description": "Flood monitoring coverage",
              "observedAt": $observed,
              "validUntil": $validity,
              "source": "SENSOR",
              "sourceNodeIds": ["GTH-10003BD4BCFC"],
              "routingMultiplier": $routingMultiplier,
              "reasonCodes": $reasons,
              "freshness": "$freshness"
            }
        """.trimIndent()
    }

    private fun validResponse(
        feature: FloodHazardFeatureDto = validFeature(),
        features: List<FloodHazardFeatureDto> = listOf(feature),
        validUntil: String? = "2026-07-30T11:00:00.000Z",
    ) = FloodHazardsResponseDto(
        type = "FeatureCollection",
        snapshotId = "snapshot_v1_1",
        generatedAt = "2026-07-30T10:00:00.000Z",
        validUntil = validUntil,
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
        sourceNodeIds = emptyList(),
        routingMultiplier = 0.25,
        reasonCodes = emptyList(),
        freshness = null,
    )
}
