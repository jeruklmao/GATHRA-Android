package opsi.sman35jkt.gathra.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opsi.sman35jkt.gathra.core.location.LocationLookupResult
import opsi.sman35jkt.gathra.core.location.LocationRepository
import opsi.sman35jkt.gathra.core.map.JakartaDemoPoints
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.core.model.RouteSelectionPoint
import opsi.sman35jkt.gathra.core.model.SelectionPointSource
import opsi.sman35jkt.gathra.domain.route.RouteFailureReason
import opsi.sman35jkt.gathra.domain.route.RouteRepository
import opsi.sman35jkt.gathra.domain.route.RouteRepositoryException

class MapRouteViewModel(
    private val routeRepository: RouteRepository,
    private val locationRepository: LocationRepository,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MapRouteUiState(
            origin = RouteSelectionPoint(
                point = JakartaDemoPoints.origin,
                source = SelectionPointSource.DEMO_FALLBACK,
            ),
        ),
    )
    val uiState: StateFlow<MapRouteUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MapRouteEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<MapRouteEffect> = _effects.asSharedFlow()

    private var routeCalculationJob: Job? = null
    private var locationLookupJob: Job? = null
    private val routeRequestGeneration = AtomicLong(0)
    private val locationRequestGeneration = AtomicLong(0)

    fun onAction(action: MapRouteAction) {
        when (action) {
            is MapRouteAction.StartPointSelection -> startPointSelection(action.mode)
            is MapRouteAction.MapPointTapped -> updatePendingPoint(action)
            MapRouteAction.ConfirmPointSelection -> confirmPointSelection()
            MapRouteAction.CancelPointSelection -> cancelPointSelection()
            MapRouteAction.SwapPoints -> swapPoints()
            is MapRouteAction.TravelModeSelected -> selectTravelMode(action)
            is MapRouteAction.RouteSelected -> selectRoute(action.routeId)
            MapRouteAction.RetryRoute -> calculateRoutes()
            is MapRouteAction.BottomSheetChanged -> {
                _uiState.update { it.copy(bottomSheetState = action.state) }
            }
            MapRouteAction.CurrentLocationClicked -> onCurrentLocationClicked()
            MapRouteAction.PermissionRationaleAccepted -> onPermissionRationaleAccepted()
            MapRouteAction.PermissionRationaleDismissed -> {
                _uiState.update {
                    it.copy(
                        isPermissionRationaleVisible = false,
                        isNavigationPermissionRequest = false,
                    )
                }
            }
            is MapRouteAction.LocationPermissionResult -> onLocationPermissionResult(action)
            MapRouteAction.ErrorDismissed -> dismissError()
            MapRouteAction.PreviewClicked -> requestNavigationStart()
        }
    }

    private fun startPointSelection(mode: PointSelectionMode) {
        if (mode == PointSelectionMode.ORIGIN) {
            cancelPendingLocationLookup()
        }
        _uiState.update {
            it.copy(
                pointSelectionMode = mode,
                pendingPoint = null,
                error = null,
            )
        }
    }

    private fun updatePendingPoint(action: MapRouteAction.MapPointTapped) {
        _uiState.update { state ->
            if (state.pointSelectionMode == null) state else state.copy(pendingPoint = action.point)
        }
    }

    private fun confirmPointSelection() {
        val state = _uiState.value
        val mode = state.pointSelectionMode ?: return
        val point = state.pendingPoint ?: return
        val selection = RouteSelectionPoint(
            point = point,
            source = SelectionPointSource.MAP_SELECTION,
        )

        if (mode == PointSelectionMode.ORIGIN) {
            cancelPendingLocationLookup()
        }
        _uiState.update {
            when (mode) {
                PointSelectionMode.ORIGIN -> it.copy(
                    origin = selection,
                    pointSelectionMode = null,
                    pendingPoint = null,
                    error = null,
                )
                PointSelectionMode.DESTINATION -> it.copy(
                    destination = selection,
                    pointSelectionMode = null,
                    pendingPoint = null,
                    error = null,
                )
            }
        }
        calculateRoutes()
    }

    private fun cancelPointSelection() {
        _uiState.update {
            it.copy(
                pointSelectionMode = null,
                pendingPoint = null,
            )
        }
    }

    private fun swapPoints() {
        cancelPendingLocationLookup()
        _uiState.update {
            it.copy(
                origin = it.destination,
                destination = it.origin,
                pointSelectionMode = null,
                pendingPoint = null,
                error = null,
            )
        }
        calculateRoutes()
    }

    private fun selectTravelMode(action: MapRouteAction.TravelModeSelected) {
        if (_uiState.value.selectedTravelMode == action.mode) return
        _uiState.update {
            it.copy(
                selectedTravelMode = action.mode,
                error = null,
            )
        }
        calculateRoutes()
    }

    private fun selectRoute(routeId: String) {
        _uiState.update { state ->
            if (state.routes.none { it.id == routeId }) {
                state
            } else {
                state.copy(selectedRouteId = routeId)
            }
        }
    }

    private fun calculateRoutes() {
        routeCalculationJob?.cancel()
        val generation = routeRequestGeneration.incrementAndGet()
        val state = _uiState.value
        val origin = state.origin?.point
        val destination = state.destination?.point

        if (origin == null || destination == null) {
            _uiState.update {
                it.copy(
                    routes = emptyList(),
                    selectedRouteId = null,
                    routeContentState = RouteContentState.EMPTY,
                    error = null,
                )
            }
            return
        }

        if (origin == destination) {
            _uiState.update {
                it.copy(
                    routes = emptyList(),
                    selectedRouteId = null,
                    routeContentState = RouteContentState.ERROR,
                    error = MapRouteError.ROUTE_CALCULATION_FAILED,
                )
            }
            return
        }

        val request = RouteRequest(
            origin = origin,
            destination = destination,
            travelMode = state.selectedTravelMode,
        )
        _uiState.update {
            it.copy(
                routes = emptyList(),
                selectedRouteId = null,
                routeContentState = RouteContentState.LOADING,
                error = null,
            )
        }

        routeCalculationJob = viewModelScope.launch {
            try {
                val routes = withContext(workDispatcher) {
                    routeRepository.getRoutes(request)
                }
                if (generation != routeRequestGeneration.get()) return@launch

                val selectedRoute = routes.firstOrNull { it.isRecommended } ?: routes.firstOrNull()
                if (routes.isEmpty()) {
                    showRouteFailure(MapRouteError.ROUTE_NOT_FOUND)
                } else {
                    _uiState.update {
                        it.copy(
                            routes = routes,
                            selectedRouteId = selectedRoute?.id,
                            routeContentState = RouteContentState.READY,
                            error = null,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: RouteRepositoryException) {
                if (generation != routeRequestGeneration.get()) return@launch
                showRouteFailure(failure.reason.toMapRouteError())
            } catch (_: Throwable) {
                if (generation != routeRequestGeneration.get()) return@launch
                showRouteFailure(MapRouteError.ROUTE_CALCULATION_FAILED)
            }
        }
    }

    private fun showRouteFailure(error: MapRouteError) {
        _uiState.update {
            it.copy(
                routes = emptyList(),
                selectedRouteId = null,
                routeContentState = RouteContentState.ERROR,
                error = error,
            )
        }
    }

    private fun onCurrentLocationClicked() {
        when (_uiState.value.locationPermissionState) {
            LocationPermissionState.PRECISE,
            LocationPermissionState.APPROXIMATE,
            -> locateCurrentPosition()

            LocationPermissionState.PERMANENTLY_DENIED -> {
                _effects.tryEmit(MapRouteEffect.OpenApplicationSettings)
            }

            LocationPermissionState.NOT_REQUESTED,
            LocationPermissionState.DENIED,
            -> {
                _uiState.update {
                    it.copy(
                        isPermissionRationaleVisible = true,
                        isNavigationPermissionRequest = false,
                    )
                }
            }
        }
    }

    private fun onPermissionRationaleAccepted() {
        val state = _uiState.value
        val isNavigationDisclosure = state.isNavigationPermissionRequest
        _uiState.update {
            it.copy(
                isPermissionRationaleVisible = false,
                isNavigationPermissionRequest = if (
                    isNavigationDisclosure &&
                    state.locationPermissionState in setOf(
                        LocationPermissionState.PRECISE,
                        LocationPermissionState.APPROXIMATE,
                    )
                ) {
                    false
                } else {
                    it.isNavigationPermissionRequest
                },
                hasShownNavigationDisclosure =
                    it.hasShownNavigationDisclosure || isNavigationDisclosure,
            )
        }
        if (
            isNavigationDisclosure &&
            state.locationPermissionState in setOf(
                LocationPermissionState.PRECISE,
                LocationPermissionState.APPROXIMATE,
            )
        ) {
            emitNavigationStart()
        } else {
            _effects.tryEmit(MapRouteEffect.RequestForegroundLocationPermission)
        }
    }

    private fun onLocationPermissionResult(action: MapRouteAction.LocationPermissionResult) {
        val navigationStartPending = _uiState.value.isNavigationPermissionRequest
        val permissionState = when {
            action.preciseGranted -> LocationPermissionState.PRECISE
            action.approximateGranted -> LocationPermissionState.APPROXIMATE
            action.permanentlyDenied -> LocationPermissionState.PERMANENTLY_DENIED
            else -> LocationPermissionState.DENIED
        }
        val shouldRestoreDemoOrigin =
            permissionState != LocationPermissionState.PRECISE &&
                permissionState != LocationPermissionState.APPROXIMATE &&
                _uiState.value.origin?.source == SelectionPointSource.CURRENT_LOCATION
        _uiState.update {
            it.copy(
                locationPermissionState = permissionState,
                isPermissionRationaleVisible = false,
                isNavigationPermissionRequest = false,
                origin = if (shouldRestoreDemoOrigin) {
                    RouteSelectionPoint(
                        point = JakartaDemoPoints.origin,
                        source = SelectionPointSource.DEMO_FALLBACK,
                    )
                } else {
                    it.origin
                },
            )
        }

        if (
            permissionState == LocationPermissionState.PRECISE ||
            permissionState == LocationPermissionState.APPROXIMATE
        ) {
            if (navigationStartPending) {
                emitNavigationStart()
            } else {
                locateCurrentPosition()
            }
        } else {
            cancelPendingLocationLookup()
            if (shouldRestoreDemoOrigin) {
                calculateRoutes()
            }
        }
    }

    private fun requestNavigationStart() {
        val state = _uiState.value
        val route = state.selectedRoute
        if (
            route == null ||
            state.destination == null ||
            route.steps.isEmpty()
        ) {
            _effects.tryEmit(
                MapRouteEffect.ShowMessage(
                    MapRouteMessage.NAVIGATION_ROUTE_UNAVAILABLE,
                ),
            )
            return
        }

        if (!state.hasShownNavigationDisclosure) {
            _uiState.update {
                it.copy(
                    isPermissionRationaleVisible = true,
                    isNavigationPermissionRequest = true,
                )
            }
            return
        }

        when (state.locationPermissionState) {
            LocationPermissionState.PRECISE,
            LocationPermissionState.APPROXIMATE,
            -> emitNavigationStart()

            LocationPermissionState.PERMANENTLY_DENIED -> {
                _effects.tryEmit(MapRouteEffect.OpenApplicationSettings)
            }

            LocationPermissionState.NOT_REQUESTED,
            LocationPermissionState.DENIED,
            -> {
                _uiState.update {
                    it.copy(
                        isPermissionRationaleVisible = true,
                        isNavigationPermissionRequest = true,
                    )
                }
            }
        }
    }

    private fun emitNavigationStart() {
        val state = _uiState.value
        val route = state.selectedRoute ?: return
        val destination = state.destination?.point ?: return
        if (route.steps.isEmpty()) return
        _effects.tryEmit(
            MapRouteEffect.StartNavigation(
                route = route,
                destination = destination,
                travelMode = state.selectedTravelMode,
            ),
        )
    }

    private fun locateCurrentPosition() {
        locationLookupJob?.cancel()
        val generation = locationRequestGeneration.incrementAndGet()
        _uiState.update {
            it.copy(
                isLocating = true,
            )
        }

        locationLookupJob = viewModelScope.launch {
            val result = try {
                withContext(workDispatcher) {
                    locationRepository.locateOnce()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                LocationLookupResult.Unavailable
            }
            if (generation != locationRequestGeneration.get()) return@launch

            when (result) {
                is LocationLookupResult.Success -> {
                    _uiState.update {
                        it.copy(
                            origin = RouteSelectionPoint(
                                point = result.point,
                                source = SelectionPointSource.CURRENT_LOCATION,
                            ),
                            isLocating = false,
                            error = null,
                        )
                    }
                    calculateRoutes()
                }

                LocationLookupResult.PermissionDenied -> {
                    _uiState.update {
                        it.copy(
                            locationPermissionState = LocationPermissionState.DENIED,
                            isLocating = false,
                        )
                    }
                }

                LocationLookupResult.LocationDisabled -> {
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                        )
                    }
                    _effects.tryEmit(
                        MapRouteEffect.ShowMessage(MapRouteMessage.LOCATION_DISABLED),
                    )
                }

                LocationLookupResult.Unavailable -> {
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                        )
                    }
                    _effects.tryEmit(
                        MapRouteEffect.ShowMessage(MapRouteMessage.LOCATION_UNAVAILABLE),
                    )
                }
            }
        }
    }

    private fun dismissError() {
        _uiState.update {
            it.copy(
                error = null,
                routeContentState = if (it.routeContentState == RouteContentState.ERROR) {
                    RouteContentState.EMPTY
                } else {
                    it.routeContentState
                },
            )
        }
    }

    private fun cancelPendingLocationLookup() {
        locationLookupJob?.cancel()
        locationLookupJob = null
        locationRequestGeneration.incrementAndGet()
        _uiState.update { it.copy(isLocating = false) }
    }

    override fun onCleared() {
        routeCalculationJob?.cancel()
        locationLookupJob?.cancel()
        super.onCleared()
    }
}

private fun RouteFailureReason.toMapRouteError(): MapRouteError = when (this) {
    RouteFailureReason.OFFLINE -> MapRouteError.ROUTE_OFFLINE
    RouteFailureReason.TIMEOUT -> MapRouteError.ROUTE_TIMEOUT
    RouteFailureReason.NO_ROUTE -> MapRouteError.ROUTE_NOT_FOUND
    RouteFailureReason.INVALID_RESPONSE -> MapRouteError.ROUTE_INVALID_RESPONSE
    RouteFailureReason.SERVER_UNAVAILABLE -> MapRouteError.ROUTE_SERVICE_UNAVAILABLE
    RouteFailureReason.UNKNOWN -> MapRouteError.ROUTE_CALCULATION_FAILED
}
