package opsi.sman35jkt.gathra.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import opsi.sman35jkt.gathra.core.model.GeoBounds
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.core.model.RouteSelectionPoint
import opsi.sman35jkt.gathra.core.model.SelectedPlace
import opsi.sman35jkt.gathra.core.model.SelectionPointSource
import opsi.sman35jkt.gathra.domain.flood.FloodHazardRepository
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepository
import opsi.sman35jkt.gathra.domain.route.RouteFailureReason
import opsi.sman35jkt.gathra.domain.route.RouteRepository
import opsi.sman35jkt.gathra.domain.route.RouteRepositoryException

class MapRouteViewModel(
    private val routeRepository: RouteRepository,
    private val locationRepository: LocationRepository,
    private val geocodingRepository: GeocodingRepository,
    private val floodHazardRepository: FloodHazardRepository,
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
    private var floodPollingJob: Job? = null
    private var floodFetchJob: Job? = null
    private var viewportDebounceJob: Job? = null
    private val reverseGeocodingJobs = mutableMapOf<PointSelectionMode, Job>()
    private val routeRequestGeneration = AtomicLong(0)
    private val locationRequestGeneration = AtomicLong(0)
    private val floodRequestGeneration = AtomicLong(0)

    init {
        startFloodPolling()
    }

    fun onAction(action: MapRouteAction) {
        when (action) {
            is MapRouteAction.StartPointSelection -> startPointSelection(action.mode)
            is MapRouteAction.SearchRequested -> requestPlaceSearch(action.mode)
            is MapRouteAction.PlaceSelected -> selectPlace(
                action.mode,
                action.place,
            )
            is MapRouteAction.UseCurrentLocation ->
                onCurrentLocationClicked(action.mode)
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
            MapRouteAction.CurrentLocationClicked ->
                onCurrentLocationClicked(PointSelectionMode.ORIGIN)
            MapRouteAction.PermissionRationaleAccepted -> onPermissionRationaleAccepted()
            MapRouteAction.PermissionRationaleDismissed -> {
                _uiState.update {
                    it.copy(
                        isPermissionRationaleVisible = false,
                        isNavigationPermissionRequest = false,
                        locationSelectionTarget = null,
                    )
                }
            }
            is MapRouteAction.LocationPermissionResult -> onLocationPermissionResult(action)
            MapRouteAction.ErrorDismissed -> dismissError()
            MapRouteAction.PreviewClicked -> requestNavigationStart()
            MapRouteAction.RefreshFloodHazards -> fetchFloodHazards()
            MapRouteAction.ToggleFloodLayer -> toggleFloodLayer()
            is MapRouteAction.FloodHazardSelected -> selectFloodHazard(action.hazardId)
            MapRouteAction.DismissFloodHazardDetails -> dismissFloodHazardDetails()
            is MapRouteAction.MapViewportSettled -> onViewportSettled(action.bounds)
        }
    }

    private fun startFloodPolling() {
        floodPollingJob?.cancel()
        floodPollingJob = viewModelScope.launch {
            fetchFloodHazards()
            while (true) {
                delay(POLLING_INTERVAL_MS)
                fetchFloodHazards()
            }
        }
    }

    fun fetchFloodHazards(bounds: GeoBounds? = null) {
        floodFetchJob?.cancel()
        val generation = floodRequestGeneration.incrementAndGet()
        _uiState.update { it.copy(isLoadingFloodHazards = true) }

        floodFetchJob = viewModelScope.launch {
            try {
                val snapshot = withContext(workDispatcher) {
                    floodHazardRepository.getActiveHazards(bounds)
                }
                if (generation != floodRequestGeneration.get()) return@launch

                val selectedRouteRiskSnapshotId = _uiState.value.selectedRoute?.risk?.hazardSnapshotId
                val outOfSync = selectedRouteRiskSnapshotId != null &&
                    selectedRouteRiskSnapshotId != snapshot.snapshotId

                _uiState.update {
                    it.copy(
                        floodHazardSnapshot = snapshot,
                        isLoadingFloodHazards = false,
                        isFloodSnapshotOutOfSync = outOfSync,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (generation != floodRequestGeneration.get()) return@launch
                // Preserve previous snapshot on recoverable network failure
                _uiState.update { it.copy(isLoadingFloodHazards = false) }
            }
        }
    }

    private fun toggleFloodLayer() {
        _uiState.update { it.copy(isFloodLayerVisible = !it.isFloodLayerVisible) }
    }

    private fun selectFloodHazard(hazardId: String) {
        _uiState.update { it.copy(selectedFloodHazardId = hazardId) }
    }

    private fun dismissFloodHazardDetails() {
        _uiState.update { it.copy(selectedFloodHazardId = null) }
    }

    private fun onViewportSettled(bounds: GeoBounds) {
        viewportDebounceJob?.cancel()
        viewportDebounceJob = viewModelScope.launch {
            delay(DEBOUNCE_VIEWPORT_MS)
            fetchFloodHazards(bounds)
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

    private fun requestPlaceSearch(mode: PointSelectionMode) {
        _effects.tryEmit(
            MapRouteEffect.OpenPlaceSearch(
                mode = mode,
                proximity = _uiState.value.currentLocationPoint
                    ?: _uiState.value.origin?.point
                    ?: JakartaDemoPoints.origin,
            ),
        )
    }

    private fun selectPlace(
        mode: PointSelectionMode,
        place: SelectedPlace,
    ) {
        if (!place.insideSupportedRegion) return
        reverseGeocodingJobs.remove(mode)?.cancel()
        if (mode == PointSelectionMode.ORIGIN) {
            cancelPendingLocationLookup()
        }
        val selection = RouteSelectionPoint(
            point = place.position,
            source = SelectionPointSource.GEOCODING_SEARCH,
            displayName = place.name,
            formattedAddress = place.formattedAddress,
        )
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
        reverseGeocodeSelection(mode, selection)
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
        restartPendingReverseGeocoding()
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
                    val currentMapSnapshotId = _uiState.value.floodHazardSnapshot?.snapshotId
                    val routeRiskSnapshotId = selectedRoute?.risk?.hazardSnapshotId
                    val outOfSync = currentMapSnapshotId != null &&
                        routeRiskSnapshotId != null &&
                        currentMapSnapshotId != routeRiskSnapshotId

                    _uiState.update {
                        it.copy(
                            routes = routes,
                            selectedRouteId = selectedRoute?.id,
                            routeContentState = RouteContentState.READY,
                            error = null,
                            isFloodSnapshotOutOfSync = outOfSync,
                        )
                    }
                    if (outOfSync) {
                        fetchFloodHazards()
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

    private fun onCurrentLocationClicked(mode: PointSelectionMode) {
        _uiState.update { it.copy(locationSelectionTarget = mode) }
        when (_uiState.value.locationPermissionState) {
            LocationPermissionState.PRECISE,
            LocationPermissionState.APPROXIMATE,
            -> locateCurrentPosition(mode)

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
                        locationSelectionTarget = mode,
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
        val stateBeforeUpdate = _uiState.value
        val navigationStartPending = stateBeforeUpdate.isNavigationPermissionRequest
        val locationTarget =
            stateBeforeUpdate.locationSelectionTarget ?: PointSelectionMode.ORIGIN
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
                locateCurrentPosition(locationTarget)
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

    private fun locateCurrentPosition(target: PointSelectionMode) {
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
                        val selection = RouteSelectionPoint(
                            point = result.point,
                            source = SelectionPointSource.CURRENT_LOCATION,
                            displayName = null,
                            formattedAddress = null,
                        )
                        when (target) {
                            PointSelectionMode.ORIGIN -> it.copy(
                                origin = selection,
                                currentLocationPoint = result.point,
                                locationSelectionTarget = null,
                                isLocating = false,
                                error = null,
                            )
                            PointSelectionMode.DESTINATION -> it.copy(
                                destination = selection,
                                currentLocationPoint = result.point,
                                locationSelectionTarget = null,
                                isLocating = false,
                                error = null,
                            )
                        }
                    }
                    calculateRoutes()
                }

                LocationLookupResult.PermissionDenied -> {
                    _uiState.update {
                        it.copy(
                            locationPermissionState = LocationPermissionState.DENIED,
                            isLocating = false,
                            locationSelectionTarget = null,
                        )
                    }
                }

                LocationLookupResult.LocationDisabled -> {
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            locationSelectionTarget = null,
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
                            locationSelectionTarget = null,
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
        _uiState.update {
            it.copy(
                isLocating = false,
                locationSelectionTarget = null,
            )
        }
    }

    private fun reverseGeocodeSelection(
        mode: PointSelectionMode,
        selection: RouteSelectionPoint,
    ) {
        reverseGeocodingJobs.remove(mode)?.cancel()
        reverseGeocodingJobs[mode] = viewModelScope.launch {
            val place = try {
                geocodingRepository.reverse(selection.point)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            } ?: return@launch

            _uiState.update { state ->
                val current = when (mode) {
                    PointSelectionMode.ORIGIN -> state.origin
                    PointSelectionMode.DESTINATION -> state.destination
                }
                if (
                    current?.point != selection.point ||
                    current.source != SelectionPointSource.MAP_SELECTION
                ) {
                    state
                } else {
                    val labelled = current.copy(
                        displayName = place.name,
                        formattedAddress = place.formattedAddress,
                    )
                    when (mode) {
                        PointSelectionMode.ORIGIN ->
                            state.copy(origin = labelled)
                        PointSelectionMode.DESTINATION ->
                            state.copy(destination = labelled)
                    }
                }
            }
        }
    }

    private fun restartPendingReverseGeocoding() {
        reverseGeocodingJobs.values.forEach(Job::cancel)
        reverseGeocodingJobs.clear()
        val state = _uiState.value
        state.origin
            ?.takeIf {
                it.source == SelectionPointSource.MAP_SELECTION &&
                    it.displayName == null
            }
            ?.let {
                reverseGeocodeSelection(PointSelectionMode.ORIGIN, it)
            }
        state.destination
            ?.takeIf {
                it.source == SelectionPointSource.MAP_SELECTION &&
                    it.displayName == null
            }
            ?.let {
                reverseGeocodeSelection(PointSelectionMode.DESTINATION, it)
            }
    }

    override fun onCleared() {
        routeCalculationJob?.cancel()
        locationLookupJob?.cancel()
        floodPollingJob?.cancel()
        floodFetchJob?.cancel()
        viewportDebounceJob?.cancel()
        reverseGeocodingJobs.values.forEach(Job::cancel)
        super.onCleared()
    }

    companion object {
        const val POLLING_INTERVAL_MS = 20_000L
        const val DEBOUNCE_VIEWPORT_MS = 600L
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
