package opsi.sman35jkt.gathra.data.sensor.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class SensorPositionDto(val latitude: Double, val longitude: Double)
@Serializable data class SensorFloodDto(
    val waterHeightMm: Int? = null,
    val effectiveLevel: String,
    val freshness: String,
    val observedAt: String? = null,
)
@Serializable data class SensorMeasurementDto(
    val acceptedDistanceMm: Int? = null,
    val temperatureC: Double? = null,
    val humidityPercent: Double? = null,
)
@Serializable data class SensorGatewayDto(
    val status: String,
    val lastHeartbeatAt: String? = null,
    val radioReceptionStatus: String,
    val latestRssiDbm: Double? = null,
    val latestSnrDb: Double? = null,
    val backendDeliveryStatus: String,
)
@Serializable data class SensorCurrentDto(
    val nodeId: String,
    val position: SensorPositionDto,
    val flood: SensorFloodDto,
    val measurement: SensorMeasurementDto,
    val gateway: SensorGatewayDto? = null,
)
