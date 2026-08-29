package opsi.sman35jkt.gathra.data.sensor.remote

import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.data.common.parseStrictIsoTimestamp
import opsi.sman35jkt.gathra.domain.sensor.BackendDeliveryStatus
import opsi.sman35jkt.gathra.domain.sensor.GatewayStatus
import opsi.sman35jkt.gathra.domain.sensor.RadioReceptionStatus
import opsi.sman35jkt.gathra.domain.sensor.SensorCurrentState
import opsi.sman35jkt.gathra.domain.sensor.SensorGatewaySummary

fun SensorCurrentDto.toDomain(): SensorCurrentState {
    require(nodeId.matches(Regex("[A-Za-z0-9_-]{1,24}")))
    require(position.latitude.isFinite() && position.latitude in -90.0..90.0)
    require(position.longitude.isFinite() && position.longitude in -180.0..180.0)
    require(measurement.temperatureC == null || measurement.temperatureC.isFinite())
    require(measurement.humidityPercent == null || measurement.humidityPercent.isFinite())
    return SensorCurrentState(
        nodeId = nodeId,
        position = GeoPoint(position.latitude, position.longitude),
        waterHeightMm = flood.waterHeightMm,
        effectiveLevel = enumValue<FloodHazardLevel>(flood.effectiveLevel),
        freshness = enumValue<FloodHazardFreshness>(flood.freshness),
        observedAtEpochMillis = flood.observedAt?.let(::requiredTimestamp),
        acceptedDistanceMm = measurement.acceptedDistanceMm,
        temperatureC = measurement.temperatureC,
        humidityPercent = measurement.humidityPercent,
        gateway = gateway?.let { value ->
            SensorGatewaySummary(
                status = enumValue<GatewayStatus>(value.status),
                lastHeartbeatAtEpochMillis = value.lastHeartbeatAt?.let(::requiredTimestamp),
                radioReceptionStatus = enumValue<RadioReceptionStatus>(value.radioReceptionStatus),
                latestRssiDbm = value.latestRssiDbm?.also { require(it.isFinite()) },
                latestSnrDb = value.latestSnrDb?.also { require(it.isFinite()) },
                backendDeliveryStatus = enumValue<BackendDeliveryStatus>(value.backendDeliveryStatus),
            )
        },
    )
}

private inline fun <reified T : Enum<T>> enumValue(raw: String): T =
    enumValues<T>().firstOrNull { it.name == raw.uppercase() }
        ?: throw IllegalArgumentException("Invalid sensor response enum")

private fun requiredTimestamp(raw: String): Long =
    parseStrictIsoTimestamp(raw) ?: throw IllegalArgumentException("Invalid sensor timestamp")
