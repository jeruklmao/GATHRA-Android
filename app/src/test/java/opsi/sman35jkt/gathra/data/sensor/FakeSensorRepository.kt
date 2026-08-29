package opsi.sman35jkt.gathra.data.sensor

import opsi.sman35jkt.gathra.domain.sensor.SensorCurrentState
import opsi.sman35jkt.gathra.domain.sensor.SensorRepository

class FakeSensorRepository(
    var current: SensorCurrentState? = null,
) : SensorRepository {
    var calls = 0
    override suspend fun getCurrent(nodeId: String): SensorCurrentState {
        calls += 1
        return requireNotNull(current) { "No fake current sensor detail configured" }
    }
}
