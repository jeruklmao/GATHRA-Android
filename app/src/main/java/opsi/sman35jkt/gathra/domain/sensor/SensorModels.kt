package opsi.sman35jkt.gathra.domain.sensor

import androidx.compose.runtime.Immutable
import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.GeoPoint

enum class GatewayStatus { ONLINE, STALE, OFFLINE, UNAVAILABLE }
enum class RadioReceptionStatus { RECENT, STALE, UNAVAILABLE }
enum class BackendDeliveryStatus { NORMAL, DEGRADED, UNAVAILABLE }

@Immutable
data class SensorGatewaySummary(
    val status: GatewayStatus,
    val lastHeartbeatAtEpochMillis: Long?,
    val radioReceptionStatus: RadioReceptionStatus,
    val latestRssiDbm: Double?,
    val latestSnrDb: Double?,
    val backendDeliveryStatus: BackendDeliveryStatus,
)

@Immutable
data class SensorCurrentState(
    val nodeId: String,
    val position: GeoPoint,
    val waterHeightMm: Int?,
    val effectiveLevel: FloodHazardLevel,
    val freshness: FloodHazardFreshness,
    val observedAtEpochMillis: Long?,
    val acceptedDistanceMm: Int?,
    val temperatureC: Double?,
    val humidityPercent: Double?,
    val gateway: SensorGatewaySummary?,
)
