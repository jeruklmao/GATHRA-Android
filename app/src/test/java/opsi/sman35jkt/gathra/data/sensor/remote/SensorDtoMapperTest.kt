package opsi.sman35jkt.gathra.data.sensor.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.domain.sensor.GatewayStatus
import opsi.sman35jkt.gathra.domain.sensor.RadioReceptionStatus

class SensorDtoMapperTest {
    @Test fun `maps a full public current response`() {
        val result = dto().toDomain()
        assertEquals("GTH-10003BD4BCFC", result.nodeId)
        assertEquals(-6.235149, result.position.latitude, 0.0)
        assertEquals(123, result.waterHeightMm)
        assertEquals(FloodHazardLevel.LOW, result.effectiveLevel)
        assertEquals(FloodHazardFreshness.FRESH, result.freshness)
        assertEquals(1602, result.acceptedDistanceMm)
        assertEquals(31.2, result.temperatureC!!, 0.0)
        assertEquals(72.4, result.humidityPercent!!, 0.0)
        assertEquals(GatewayStatus.ONLINE, result.gateway?.status)
        assertEquals(RadioReceptionStatus.RECENT, result.gateway?.radioReceptionStatus)
    }

    @Test fun `accepts valid nullable optional data`() {
        val result = dto(
            flood = SensorFloodDto(null, "UNKNOWN", "NO_TELEMETRY", null),
            measurement = SensorMeasurementDto(null, null, null),
            gateway = null,
        ).toDomain()
        assertNull(result.waterHeightMm)
        assertNull(result.observedAtEpochMillis)
        assertNull(result.acceptedDistanceMm)
        assertNull(result.temperatureC)
        assertNull(result.humidityPercent)
        assertNull(result.gateway)
    }

    @Test fun `maps stale unknown and every Gateway status without RSSI classification`() {
        for (status in listOf("ONLINE", "STALE", "OFFLINE", "UNAVAILABLE")) {
            val result = dto(
                flood = SensorFloodDto(123, "UNKNOWN", "STALE", "2026-08-29T00:00:00.000Z"),
                gateway = gateway(status = status, radio = "STALE", rssi = -130.0),
            ).toDomain()
            assertEquals(FloodHazardFreshness.STALE, result.freshness)
            assertEquals(FloodHazardLevel.UNKNOWN, result.effectiveLevel)
            assertEquals(GatewayStatus.valueOf(status), result.gateway?.status)
            assertEquals(-130.0, result.gateway?.latestRssiDbm!!, 0.0)
            assertEquals(RadioReceptionStatus.STALE, result.gateway?.radioReceptionStatus)
        }
    }

    @Test fun `maps unavailable radio independently`() {
        val result = dto(gateway = gateway(radio = "UNAVAILABLE", rssi = null)).toDomain()
        assertEquals(RadioReceptionStatus.UNAVAILABLE, result.gateway?.radioReceptionStatus)
        assertNull(result.gateway?.latestRssiDbm)
    }

    @Test fun `rejects malformed critical fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            dto(flood = SensorFloodDto(1, "SAFE", "FRESH", null)).toDomain()
        }
        assertThrows(IllegalArgumentException::class.java) {
            dto(position = SensorPositionDto(100.0, 106.0)).toDomain()
        }
        assertThrows(IllegalArgumentException::class.java) {
            dto(nodeId = "invalid node id").toDomain()
        }
    }
}

private fun dto(
    nodeId: String = "GTH-10003BD4BCFC",
    position: SensorPositionDto = SensorPositionDto(-6.235149, 106.720401),
    flood: SensorFloodDto = SensorFloodDto(123, "LOW", "FRESH", "2026-08-29T00:00:00.000Z"),
    measurement: SensorMeasurementDto = SensorMeasurementDto(1602, 31.2, 72.4),
    gateway: SensorGatewayDto? = gateway(),
) = SensorCurrentDto(nodeId, position, flood, measurement, gateway)

private fun gateway(
    status: String = "ONLINE",
    radio: String = "RECENT",
    rssi: Double? = -79.0,
) = SensorGatewayDto(
    status = status,
    lastHeartbeatAt = "2026-08-29T00:00:30.000Z",
    radioReceptionStatus = radio,
    latestRssiDbm = rssi,
    latestSnrDb = 8.4,
    backendDeliveryStatus = "NORMAL",
)
