package opsi.sman35jkt.gathra.domain.flood

import opsi.sman35jkt.gathra.core.model.GeoBounds
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot

interface FloodHazardRepository {
    suspend fun getActiveHazards(
        bounds: GeoBounds? = null,
    ): FloodHazardSnapshot
}
