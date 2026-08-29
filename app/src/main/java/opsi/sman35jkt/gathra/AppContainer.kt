package opsi.sman35jkt.gathra

import android.content.Context
import opsi.sman35jkt.gathra.core.location.LocationRepository
import opsi.sman35jkt.gathra.data.flood.remote.FloodNetworkFactory
import opsi.sman35jkt.gathra.data.geocoding.remote.GeocodingNetworkFactory
import opsi.sman35jkt.gathra.data.location.AndroidLocationRepository
import opsi.sman35jkt.gathra.data.location.FusedNavigationLocationSource
import opsi.sman35jkt.gathra.data.navigation.NavigationSessionEngine
import opsi.sman35jkt.gathra.data.navigation.NavigationSessionRepository
import opsi.sman35jkt.gathra.data.route.remote.RouteNetworkFactory
import opsi.sman35jkt.gathra.domain.flood.FloodHazardRepository
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepository
import opsi.sman35jkt.gathra.domain.route.RouteRepository
import opsi.sman35jkt.gathra.data.sensor.remote.SensorNetworkFactory
import opsi.sman35jkt.gathra.service.navigation.NavigationServiceController

class AppContainer(context: Context) {
    val locationRepository: LocationRepository = AndroidLocationRepository(
        context = context.applicationContext,
    )

    val routeRepository: RouteRepository =
        RouteNetworkFactory.createRepository(BuildConfig.API_BASE_URL)

    val geocodingRepository: GeocodingRepository =
        GeocodingNetworkFactory.createRepository(BuildConfig.API_BASE_URL)

    val floodHazardRepository: FloodHazardRepository =
        FloodNetworkFactory.createRepository(BuildConfig.API_BASE_URL)

    val sensorRepository = SensorNetworkFactory.createRepository(BuildConfig.API_BASE_URL)

    val navigationSessionRepository = NavigationSessionRepository()

    private val navigationLocationSource =
        FusedNavigationLocationSource(context.applicationContext)

    val navigationSessionEngine = NavigationSessionEngine(
        sessionRepository = navigationSessionRepository,
        routeRepository = routeRepository,
        locationSource = navigationLocationSource,
    )

    val navigationServiceController = NavigationServiceController(
        context.applicationContext,
    )
}
