package opsi.sman35jkt.gathra

import android.content.Context
import opsi.sman35jkt.gathra.core.location.LocationRepository
import opsi.sman35jkt.gathra.data.location.AndroidLocationRepository
import opsi.sman35jkt.gathra.data.route.FakeRouteRepository
import opsi.sman35jkt.gathra.data.route.remote.RouteNetworkFactory
import opsi.sman35jkt.gathra.domain.route.RouteRepository

class AppContainer(context: Context) {
    val locationRepository: LocationRepository = AndroidLocationRepository(
        context = context.applicationContext,
    )

    val routeRepository: RouteRepository = if (BuildConfig.USE_FAKE_ROUTES) {
        FakeRouteRepository()
    } else {
        RouteNetworkFactory.createRepository(BuildConfig.ROUTE_API_BASE_URL)
    }
}
