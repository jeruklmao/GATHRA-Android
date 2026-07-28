package opsi.sman35jkt.gathra

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import opsi.sman35jkt.gathra.domain.navigation.NavigationStatus
import opsi.sman35jkt.gathra.feature.map.MapRouteRoute
import opsi.sman35jkt.gathra.feature.map.MapRouteViewModel
import opsi.sman35jkt.gathra.feature.map.MapRouteViewModelFactory
import opsi.sman35jkt.gathra.feature.map.MapRouteAction
import opsi.sman35jkt.gathra.feature.map.PointSelectionMode
import opsi.sman35jkt.gathra.feature.map.RouteBottomSheetState
import opsi.sman35jkt.gathra.feature.geocoding.PlaceSearchAction
import opsi.sman35jkt.gathra.feature.geocoding.PlaceSearchRoute
import opsi.sman35jkt.gathra.feature.geocoding.PlaceSearchViewModel
import opsi.sman35jkt.gathra.feature.geocoding.PlaceSearchViewModelFactory
import opsi.sman35jkt.gathra.feature.geocoding.SearchTargetField
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
            geocodingRepository = appContainer.geocodingRepository,
            floodHazardRepository = appContainer.floodHazardRepository,
        )
    }
    val searchFactory = remember(appContainer) {
        PlaceSearchViewModelFactory(
            repository = appContainer.geocodingRepository,
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
    val searchViewModel: PlaceSearchViewModel = viewModel(
        factory = searchFactory,
    )
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
            NavigationRoute(
                viewModel = navigationViewModel,
                floodHazardSnapshot = mapViewModel.uiState.collectAsStateWithLifecycle().value.floodHazardSnapshot,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                MapRouteRoute(
                    viewModel = mapViewModel,
                    onOpenPlaceSearch = { mode, proximity ->
                        searchViewModel.onAction(
                            PlaceSearchAction.Open(
                                targetField = mode.toSearchTarget(),
                                proximity = proximity,
                            ),
                        )
                    },
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
                PlaceSearchRoute(
                    viewModel = searchViewModel,
                    onPlaceSelected = { target, place ->
                        mapViewModel.onAction(
                            MapRouteAction.PlaceSelected(
                                mode = target.toPointSelectionMode(),
                                place = place,
                            ),
                        )
                    },
                    onUseCurrentLocation = { target ->
                        mapViewModel.onAction(
                            MapRouteAction.UseCurrentLocation(
                                target.toPointSelectionMode(),
                            ),
                        )
                    },
                    onChooseOnMap = { target ->
                        mapViewModel.onAction(
                            MapRouteAction.StartPointSelection(
                                target.toPointSelectionMode(),
                            ),
                        )
                        mapViewModel.onAction(
                            MapRouteAction.BottomSheetChanged(
                                RouteBottomSheetState.COLLAPSED,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun PointSelectionMode.toSearchTarget(): SearchTargetField = when (this) {
    PointSelectionMode.ORIGIN -> SearchTargetField.ORIGIN
    PointSelectionMode.DESTINATION -> SearchTargetField.DESTINATION
}

private fun SearchTargetField.toPointSelectionMode(): PointSelectionMode = when (this) {
    SearchTargetField.ORIGIN -> PointSelectionMode.ORIGIN
    SearchTargetField.DESTINATION -> PointSelectionMode.DESTINATION
}
