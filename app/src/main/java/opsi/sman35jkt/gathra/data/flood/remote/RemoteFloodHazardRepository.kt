package opsi.sman35jkt.gathra.data.flood.remote

import opsi.sman35jkt.gathra.core.model.GeoBounds
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.domain.flood.FloodHazardRepository

class RemoteFloodHazardRepository(
    private val api: FloodApi,
) : FloodHazardRepository {
    override suspend fun getActiveHazards(bounds: GeoBounds?): FloodHazardSnapshot {
        val dto = api.getActiveHazards(
            minLat = bounds?.minLat,
            minLon = bounds?.minLon,
            maxLat = bounds?.maxLat,
            maxLon = bounds?.maxLon,
        )
        return dto.toDomain()
    }
}
