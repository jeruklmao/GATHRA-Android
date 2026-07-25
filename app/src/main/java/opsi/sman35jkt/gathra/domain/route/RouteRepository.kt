package opsi.sman35jkt.gathra.domain.route

import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteRequest

interface RouteRepository {
    suspend fun getRoutes(request: RouteRequest): List<RouteOption>
}
