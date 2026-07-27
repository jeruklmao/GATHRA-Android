package opsi.sman35jkt.gathra

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import opsi.sman35jkt.gathra.domain.navigation.NavigationStatus
import opsi.sman35jkt.gathra.feature.map.MapRouteRoute
import opsi.sman35jkt.gathra.feature.map.MapRouteViewModel
import opsi.sman35jkt.gathra.feature.map.MapRouteViewModelFactory
import opsi.sman35jkt.gathra.feature.navigation.NavigationRoute
import opsi.sman35jkt.gathra.feature.navigation.NavigationViewModel
import opsi.sman35jkt.gathra.feature.navigation.NavigationViewModelFactory
import opsi.sman35jkt.gathra.ui.theme.GATHRATheme

class GathraApplication : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer(applicationContext)
    }
}

@Composable
fun GathraApp(
    appContainer: AppContainer,
) {
    val mapFactory = remember(appContainer) {
        MapRouteViewModelFactory(
            routeRepository = appContainer.routeRepository,
            locationRepository = appContainer.locationRepository,
        )
    }
    val navigationFactory = remember(appContainer) {
        NavigationViewModelFactory(
            repository = appContainer.navigationSessionRepository,
            serviceController = appContainer.navigationServiceController,
            simulationEnabled = BuildConfig.ENABLE_NAVIGATION_SIMULATION,
        )
    }
    val mapViewModel: MapRouteViewModel = viewModel(factory = mapFactory)
    val navigationViewModel: NavigationViewModel = viewModel(
        factory = navigationFactory,
    )
    val navigationSession =
        appContainer.navigationSessionRepository.session.collectAsStateWithLifecycle()
    val navigationIsVisible = navigationSession.value?.status?.let { status ->
        status != NavigationStatus.IDLE && status != NavigationStatus.STOPPED
    } == true

    GATHRATheme {
        if (navigationIsVisible) {
            NavigationRoute(viewModel = navigationViewModel)
        } else {
            MapRouteRoute(
                viewModel = mapViewModel,
                onStartNavigation = { route, destination, travelMode ->
                    val started = runCatching {
                        appContainer.navigationSessionRepository.prepare(
                            route = route,
                            destination = destination,
                            travelMode = travelMode,
                        )
                        check(appContainer.navigationServiceController.start()) {
                            "Navigation foreground service could not start."
                        }
                        true
                    }.onFailure {
                        appContainer.navigationSessionRepository.finish()
                    }.getOrDefault(false)
                    started
                },
            )
        }
    }
}
