package opsi.sman35jkt.gathra

import android.content.Context
import opsi.sman35jkt.gathra.core.location.LocationRepository
import opsi.sman35jkt.gathra.data.location.AndroidLocationRepository
import opsi.sman35jkt.gathra.data.location.FusedNavigationLocationSource
import opsi.sman35jkt.gathra.data.location.SimulatedNavigationLocationSource
import opsi.sman35jkt.gathra.data.navigation.NavigationSessionEngine
import opsi.sman35jkt.gathra.data.navigation.NavigationSessionRepository
import opsi.sman35jkt.gathra.data.route.FakeRouteRepository
import opsi.sman35jkt.gathra.data.route.remote.RouteNetworkFactory
import opsi.sman35jkt.gathra.domain.route.RouteRepository
import opsi.sman35jkt.gathra.service.navigation.NavigationServiceController

class AppContainer(context: Context) {
    val locationRepository: LocationRepository = AndroidLocationRepository(
        context = context.applicationContext,
    )

    val routeRepository: RouteRepository = if (BuildConfig.USE_FAKE_ROUTES) {
        FakeRouteRepository()
    } else {
        RouteNetworkFactory.createRepository(BuildConfig.ROUTE_API_BASE_URL)
    }

    val navigationSessionRepository = NavigationSessionRepository()

    private val navigationLocationSource = if (
        BuildConfig.ENABLE_NAVIGATION_SIMULATION
    ) {
        SimulatedNavigationLocationSource()
    } else {
        FusedNavigationLocationSource(context.applicationContext)
    }

    val navigationSessionEngine = NavigationSessionEngine(
        sessionRepository = navigationSessionRepository,
        routeRepository = routeRepository,
        locationSource = navigationLocationSource,
    )

    val navigationServiceController = NavigationServiceController(
        context.applicationContext,
    )
}
