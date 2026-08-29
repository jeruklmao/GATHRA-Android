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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opsi.sman35jkt.gathra.core.location.LocationLookupResult
import opsi.sman35jkt.gathra.core.location.LocationRepository
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
import opsi.sman35jkt.gathra.domain.sensor.SensorRepository

data class FloodRefreshConfig(
    val pollingIntervalMillis: Long = 20_000L,
    val viewportDebounceMillis: Long = 600L,
    val snapshotMismatchDebounceMillis: Long = 750L,
    val sensorDetailPollingIntervalMillis: Long = 30_000L,
) {
    init {
        require(pollingIntervalMillis > 0)
        require(viewportDebounceMillis >= 0)
        require(snapshotMismatchDebounceMillis >= 0)
        require(sensorDetailPollingIntervalMillis > 0)
    }
}

class MapRouteViewModel(
    private val routeRepository: RouteRepository,
    private val locationRepository: LocationRepository,
    private val geocodingRepository: GeocodingRepository,
    private val floodHazardRepository: FloodHazardRepository,
    private val sensorRepository: SensorRepository,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val floodRefreshConfig: FloodRefreshConfig = FloodRefreshConfig(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapRouteUiState())
    val uiState: StateFlow<MapRouteUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MapRouteEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<MapRouteEffect> = _effects.asSharedFlow()

    private var routeCalculationJob: Job? = null
    private var locationLookupJob: Job? = null
    private var floodPollingJob: Job? = null
    private var floodFetchJob: Job? = null
    private var floodMismatchRecalculationJob: Job? = null
    private var viewportDebounceJob: Job? = null
    private var sensorFetchJob: Job? = null
    private var sensorPollingJob: Job? = null
    private var scheduledFloodSnapshotId: String? = null
    private val reverseGeocodingJobs = mutableMapOf<PointSelectionMode, Job>()
    private val routeRequestGeneration = AtomicLong(0)
    private val locationRequestGeneration = AtomicLong(0)
    private val floodRequestGeneration = AtomicLong(0)

    fun onAction(action: MapRouteAction) {
        when (action) {
            MapRouteAction.ScreenStarted -> startFloodPolling()
            MapRouteAction.ScreenStopped -> {
                stopFloodPolling()
                stopSensorPolling()
            }
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
            MapRouteAction.RetryFloodRouteUpdate -> retryFloodRouteUpdate()
            MapRouteAction.ToggleFloodLayer -> toggleFloodLayer()
            is MapRouteAction.FloodHazardSelected -> selectFloodHazard(action.hazardId)
            is MapRouteAction.SensorMarkerSelected -> selectSensorMarker(action.nodeId)
            MapRouteAction.RefreshSensorDetail -> refreshSelectedSensor()
            MapRouteAction.DismissFloodHazardDetails -> dismissFloodHazardDetails()
            is MapRouteAction.MapViewportSettled -> onViewportSettled(action.bounds)
        }
    }

    private fun startFloodPolling() {
        if (floodPollingJob?.isActive == true) return
        floodPollingJob = viewModelScope.launch {
            fetchFloodHazards()
            while (isActive) {
                delay(floodRefreshConfig.pollingIntervalMillis)
                fetchFloodHazards()
            }
        }
    }

    private fun stopFloodPolling() {
        floodPollingJob?.cancel()
        floodPollingJob = null
        floodFetchJob?.cancel()
        floodFetchJob = null
        floodRequestGeneration.incrementAndGet()
        floodMismatchRecalculationJob?.cancel()
        floodMismatchRecalculationJob = null
        scheduledFloodSnapshotId = null
        viewportDebounceJob?.cancel()
        viewportDebounceJob = null
        _uiState.update {
            it.copy(
                isLoadingFloodHazards = false,
                floodRefreshStatus = if (it.floodHazardSnapshot == null) {
                    it.floodRefreshStatus
                } else {
                    FloodRefreshStatus.RETAINED_AFTER_ERROR
                },
            )
        }
    }

    private fun fetchFloodHazards(bounds: GeoBounds? = null) {
        floodFetchJob?.cancel()
        val generation = floodRequestGeneration.incrementAndGet()
        _uiState.update { it.copy(isLoadingFloodHazards = true) }

        floodFetchJob = viewModelScope.launch {
            try {
                val snapshot = withContext(workDispatcher) {
                    floodHazardRepository.getActiveHazards(bounds)
                }
                if (generation != floodRequestGeneration.get()) return@launch

                var shouldScheduleRecalculation = false
                _uiState.update { current ->
                    val selectedRouteRiskSnapshotId =
                        current.selectedRoute?.risk?.hazardSnapshotId
                    val hasMismatch = selectedRouteRiskSnapshotId != null &&
                        selectedRouteRiskSnapshotId != snapshot.snapshotId
                    val nextSyncState = when {
                        selectedRouteRiskSnapshotId == null ->
                            FloodRouteSyncState.NOT_EVALUATED
                        !hasMismatch -> FloodRouteSyncState.SYNCHRONIZED
                        current.floodRouteTargetSnapshotId == snapshot.snapshotId &&
                            current.floodRouteSyncState in setOf(
                                FloodRouteSyncState.UPDATING,
                                FloodRouteSyncState.STALE,
                            ) -> current.floodRouteSyncState
                        else -> FloodRouteSyncState.OUTDATED_BY_FLOOD_UPDATE
                    }
                    shouldScheduleRecalculation =
                        nextSyncState == FloodRouteSyncState.OUTDATED_BY_FLOOD_UPDATE
                    current.copy(
                        floodHazardSnapshot = snapshot,
                        isLoadingFloodHazards = false,
                        floodRefreshStatus = FloodRefreshStatus.SUCCEEDED,
                        floodRouteSyncState = nextSyncState,
                        floodRouteTargetSnapshotId = if (hasMismatch) {
                            snapshot.snapshotId
                        } else {
                            null
                        },
                        selectedFloodHazardId = current.selectedFloodHazardId
                            ?.takeIf { selectedId ->
                                snapshot.hazards.any { it.id == selectedId }
                            },
                    )
                }
                if (shouldScheduleRecalculation) {
                    scheduleFloodRouteUpdate(snapshot.snapshotId)
                }
                ensureSensorMarkerDetail(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (generation != floodRequestGeneration.get()) return@launch
                _uiState.update {
                    it.copy(
                        isLoadingFloodHazards = false,
                        floodRefreshStatus = if (it.floodHazardSnapshot == null) {
                            FloodRefreshStatus.UNAVAILABLE
                        } else {
                            FloodRefreshStatus.RETAINED_AFTER_ERROR
                        },
                    )
                }
            }
        }
    }

    private fun scheduleFloodRouteUpdate(snapshotId: String) {
        val state = _uiState.value
        if (
            state.selectedRoute == null ||
            state.origin == null ||
            state.destination == null
        ) {
            return
        }
        if (
            state.floodRouteTargetSnapshotId == snapshotId &&
            state.floodRouteSyncState in setOf(
                FloodRouteSyncState.UPDATING,
                FloodRouteSyncState.STALE,
            )
        ) {
            return
        }
        if (
            scheduledFloodSnapshotId == snapshotId &&
            floodMismatchRecalculationJob?.isActive == true
        ) {
            return
        }

        floodMismatchRecalculationJob?.cancel()
        scheduledFloodSnapshotId = snapshotId
        _uiState.update {
            it.copy(
                floodRouteSyncState = FloodRouteSyncState.OUTDATED_BY_FLOOD_UPDATE,
                floodRouteTargetSnapshotId = snapshotId,
            )
        }
        floodMismatchRecalculationJob = viewModelScope.launch {
            delay(floodRefreshConfig.snapshotMismatchDebounceMillis)
            scheduledFloodSnapshotId = null
            calculateRoutesForFloodSnapshot(snapshotId)
        }
    }

    private fun retryFloodRouteUpdate() {
        val state = _uiState.value
        val snapshotId = state.floodHazardSnapshot?.snapshotId ?: return
        if (state.selectedRoute == null || routeCalculationJob?.isActive == true) return
        floodMismatchRecalculationJob?.cancel()
        floodMismatchRecalculationJob = null
        scheduledFloodSnapshotId = null
        calculateRoutesForFloodSnapshot(snapshotId)
    }

    private fun toggleFloodLayer() {
        _uiState.update { it.copy(isFloodLayerVisible = !it.isFloodLayerVisible) }
    }

    private fun selectFloodHazard(hazardId: String) {
        _uiState.update { it.copy(selectedFloodHazardId = hazardId) }
        startSelectedSensorPolling()
    }

    private fun dismissFloodHazardDetails() {
        _uiState.update { it.copy(selectedFloodHazardId = null) }
        stopSensorPolling()
    }

    private fun selectSensorMarker(nodeId: String) {
        val hazard = _uiState.value.floodHazardSnapshot?.hazards
            ?.firstOrNull { it.sourceNodeIds.size == 1 && it.sourceNodeIds.single() == nodeId }
            ?: return
        selectFloodHazard(hazard.id)
    }

    private fun ensureSensorMarkerDetail(snapshot: opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot) {
        val nodeIds = snapshot.hazards
            .filter { it.source == opsi.sman35jkt.gathra.core.model.FloodHazardSource.SENSOR }
            .flatMap { it.sourceNodeIds }
            .distinct()
        if (nodeIds.size != 1) {
            stopSensorPolling()
            _uiState.update { it.copy(sensorDetail = null) }
            return
        }
        if (_uiState.value.sensorDetail?.nodeId == nodeIds.single()) return
        fetchSensorDetail(nodeIds.single())
    }

    private fun startSelectedSensorPolling() {
        stopSensorPolling()
        val nodeId = _uiState.value.selectedSensorNodeId ?: return
        fetchSensorDetail(nodeId)
        sensorPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(floodRefreshConfig.sensorDetailPollingIntervalMillis)
                fetchSensorDetail(nodeId)
            }
        }
    }

    private fun stopSensorPolling() {
        sensorPollingJob?.cancel()
        sensorPollingJob = null
        sensorFetchJob?.cancel()
        sensorFetchJob = null
        _uiState.update { it.copy(isLoadingSensorDetail = false) }
    }

    private fun refreshSelectedSensor() {
        _uiState.value.selectedSensorNodeId?.let(::fetchSensorDetail)
    }

    private fun fetchSensorDetail(nodeId: String) {
        sensorFetchJob?.cancel()
        _uiState.update { it.copy(isLoadingSensorDetail = true, sensorDetailRefreshFailed = false) }
        sensorFetchJob = viewModelScope.launch {
            try {
                val detail = withContext(workDispatcher) { sensorRepository.getCurrent(nodeId) }
                if (detail.nodeId != nodeId) return@launch
                _uiState.update {
                    it.copy(
                        sensorDetail = detail,
                        isLoadingSensorDetail = false,
                        sensorDetailRefreshFailed = false,
                        sensorDetailRefreshedAtEpochMillis = System.currentTimeMillis(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(isLoadingSensorDetail = false, sensorDetailRefreshFailed = true)
                }
            }
        }
    }

    private fun onViewportSettled(bounds: GeoBounds) {
        if (floodPollingJob?.isActive != true) return
        if (_uiState.value.floodHazardSnapshot == null) return
        viewportDebounceJob?.cancel()
        viewportDebounceJob = viewModelScope.launch {
            delay(floodRefreshConfig.viewportDebounceMillis)
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
                    ?: _uiState.value.destination?.point,
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
        floodMismatchRecalculationJob?.cancel()
        floodMismatchRecalculationJob = null
        scheduledFloodSnapshotId = null
        calculateRoutesInternal(targetFloodSnapshotId = null)
    }

    private fun calculateRoutesForFloodSnapshot(snapshotId: String) {
        if (_uiState.value.floodHazardSnapshot?.snapshotId != snapshotId) return
        calculateRoutesInternal(targetFloodSnapshotId = snapshotId)
    }

    private fun calculateRoutesInternal(targetFloodSnapshotId: String?) {
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
                    floodRouteSyncState = FloodRouteSyncState.NOT_EVALUATED,
                    floodRouteTargetSnapshotId = null,
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
                    floodRouteSyncState = FloodRouteSyncState.NOT_EVALUATED,
                    floodRouteTargetSnapshotId = null,
                )
            }
            return
        }

        val request = RouteRequest(
            origin = origin,
            destination = destination,
            travelMode = state.selectedTravelMode,
        )
        _uiState.update { current ->
            if (targetFloodSnapshotId == null) {
                current.copy(
                    routes = emptyList(),
                    selectedRouteId = null,
                    routeContentState = RouteContentState.LOADING,
                    error = null,
                    floodRouteSyncState = FloodRouteSyncState.NOT_EVALUATED,
                    floodRouteTargetSnapshotId = null,
                )
            } else {
                current.copy(
                    routeContentState = RouteContentState.READY,
                    error = null,
                    floodRouteSyncState = FloodRouteSyncState.UPDATING,
                    floodRouteTargetSnapshotId = targetFloodSnapshotId,
                )
            }
        }

        routeCalculationJob = viewModelScope.launch {
            try {
                val routes = withContext(workDispatcher) {
                    routeRepository.getRoutes(request)
                }
                if (generation != routeRequestGeneration.get()) return@launch

                val selectedRoute = routes.firstOrNull { it.isRecommended } ?: routes.firstOrNull()
                if (routes.isEmpty()) {
                    if (targetFloodSnapshotId == null) {
                        showRouteFailure(MapRouteError.ROUTE_NOT_FOUND)
                    } else {
                        showFloodRouteUpdateFailure(targetFloodSnapshotId)
                    }
                } else {
                    val currentMapSnapshotId = _uiState.value.floodHazardSnapshot?.snapshotId
                    val routeRiskSnapshotId = selectedRoute?.risk?.hazardSnapshotId
                    if (targetFloodSnapshotId != null) {
                        when {
                            currentMapSnapshotId != targetFloodSnapshotId -> {
                                val newerSnapshotId = currentMapSnapshotId
                                _uiState.update {
                                    it.copy(
                                        floodRouteSyncState =
                                            FloodRouteSyncState.OUTDATED_BY_FLOOD_UPDATE,
                                        floodRouteTargetSnapshotId = newerSnapshotId,
                                    )
                                }
                                if (newerSnapshotId != null) {
                                    scheduleFloodRouteUpdate(newerSnapshotId)
                                }
                            }
                            routeRiskSnapshotId != targetFloodSnapshotId -> {
                                showFloodRouteUpdateFailure(targetFloodSnapshotId)
                            }
                            else -> {
                                _uiState.update {
                                    it.copy(
                                        routes = routes,
                                        selectedRouteId = selectedRoute?.id,
                                        routeContentState = RouteContentState.READY,
                                        error = null,
                                        floodRouteSyncState =
                                            FloodRouteSyncState.SYNCHRONIZED,
                                        floodRouteTargetSnapshotId = null,
                                    )
                                }
                            }
                        }
                    } else {
                        val syncState = when {
                            currentMapSnapshotId != null &&
                                routeRiskSnapshotId == currentMapSnapshotId ->
                                FloodRouteSyncState.SYNCHRONIZED
                            currentMapSnapshotId != null &&
                                routeRiskSnapshotId != null ->
                                FloodRouteSyncState.OUTDATED_BY_FLOOD_UPDATE
                            else -> FloodRouteSyncState.NOT_EVALUATED
                        }
                        _uiState.update {
                            it.copy(
                                routes = routes,
                                selectedRouteId = selectedRoute?.id,
                                routeContentState = RouteContentState.READY,
                                error = null,
                                floodRouteSyncState = syncState,
                                floodRouteTargetSnapshotId = if (
                                    syncState ==
                                    FloodRouteSyncState.OUTDATED_BY_FLOOD_UPDATE
                                ) {
                                    currentMapSnapshotId
                                } else {
                                    null
                                },
                            )
                        }
                        if (
                            syncState == FloodRouteSyncState.OUTDATED_BY_FLOOD_UPDATE &&
                            currentMapSnapshotId != null
                        ) {
                            scheduleFloodRouteUpdate(currentMapSnapshotId)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: RouteRepositoryException) {
                if (generation != routeRequestGeneration.get()) return@launch
                if (targetFloodSnapshotId == null) {
                    showRouteFailure(failure.reason.toMapRouteError())
                } else {
                    showFloodRouteUpdateFailure(targetFloodSnapshotId)
                }
            } catch (_: Throwable) {
                if (generation != routeRequestGeneration.get()) return@launch
                if (targetFloodSnapshotId == null) {
                    showRouteFailure(MapRouteError.ROUTE_CALCULATION_FAILED)
                } else {
                    showFloodRouteUpdateFailure(targetFloodSnapshotId)
                }
            }
        }
    }

    private fun showFloodRouteUpdateFailure(snapshotId: String) {
        _uiState.update {
            it.copy(
                routeContentState = if (it.routes.isEmpty()) {
                    RouteContentState.ERROR
                } else {
                    RouteContentState.READY
                },
                floodRouteSyncState = FloodRouteSyncState.STALE,
                floodRouteTargetSnapshotId = snapshotId,
            )
        }
    }

    private fun showRouteFailure(error: MapRouteError) {
        _uiState.update {
            it.copy(
                routes = emptyList(),
                selectedRouteId = null,
                routeContentState = RouteContentState.ERROR,
                error = error,
                floodRouteSyncState = FloodRouteSyncState.NOT_EVALUATED,
                floodRouteTargetSnapshotId = null,
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
        val shouldClearCurrentLocation =
            permissionState != LocationPermissionState.PRECISE &&
                permissionState != LocationPermissionState.APPROXIMATE &&
                _uiState.value.origin?.source == SelectionPointSource.CURRENT_LOCATION
        _uiState.update {
            it.copy(
                locationPermissionState = permissionState,
                isPermissionRationaleVisible = false,
                isNavigationPermissionRequest = false,
                origin = if (shouldClearCurrentLocation) {
                    null
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
            if (shouldClearCurrentLocation) {
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
        if (!state.canUseSelectedRoute) {
            _effects.tryEmit(
                MapRouteEffect.ShowMessage(MapRouteMessage.FLOOD_ROUTE_OUTDATED),
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
        if (route.steps.isEmpty() || !state.canUseSelectedRoute) return
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
        floodMismatchRecalculationJob?.cancel()
        viewportDebounceJob?.cancel()
        reverseGeocodingJobs.values.forEach(Job::cancel)
        super.onCleared()
    }

}

private fun RouteFailureReason.toMapRouteError(): MapRouteError = when (this) {
    RouteFailureReason.OFFLINE -> MapRouteError.ROUTE_OFFLINE
    RouteFailureReason.TIMEOUT -> MapRouteError.ROUTE_TIMEOUT
    RouteFailureReason.NO_ROUTE -> MapRouteError.ROUTE_NOT_FOUND
    RouteFailureReason.NO_ROUTE_DUE_TO_FLOOD -> MapRouteError.NO_ROUTE_DUE_TO_FLOOD
    RouteFailureReason.ORIGIN_IN_BLOCKED_AREA -> MapRouteError.ORIGIN_IN_BLOCKED_AREA
    RouteFailureReason.DESTINATION_IN_BLOCKED_AREA ->
        MapRouteError.DESTINATION_IN_BLOCKED_AREA
    RouteFailureReason.INVALID_RESPONSE -> MapRouteError.ROUTE_INVALID_RESPONSE
    RouteFailureReason.SERVER_UNAVAILABLE -> MapRouteError.ROUTE_SERVICE_UNAVAILABLE
    RouteFailureReason.UNKNOWN -> MapRouteError.ROUTE_CALCULATION_FAILED
}
