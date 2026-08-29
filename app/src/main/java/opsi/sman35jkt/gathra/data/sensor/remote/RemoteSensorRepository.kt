package opsi.sman35jkt.gathra.data.sensor.remote

import opsi.sman35jkt.gathra.domain.sensor.SensorRepository

class RemoteSensorRepository(private val api: SensorApi) : SensorRepository {
    override suspend fun getCurrent(nodeId: String) = api.current(nodeId).toDomain()
}
