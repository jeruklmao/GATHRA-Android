package opsi.sman35jkt.gathra.domain.sensor

interface SensorRepository {
    suspend fun getCurrent(nodeId: String): SensorCurrentState
}
