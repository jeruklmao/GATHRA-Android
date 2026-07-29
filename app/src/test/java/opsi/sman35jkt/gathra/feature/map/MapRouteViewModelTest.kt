package opsi.sman35jkt.gathra.feature.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import opsi.sman35jkt.gathra.core.location.LocationLookupResult
import opsi.sman35jkt.gathra.core.location.LocationRepository
import opsi.sman35jkt.gathra.core.map.JakartaDemoPoints
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.core.model.SelectionPointSource
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.data.flood.FakeFloodHazardRepository
import opsi.sman35jkt.gathra.data.route.FakeRouteRepository
import opsi.sman35jkt.gathra.data.geocoding.FakeGeocodingRepository
import opsi.sman35jkt.gathra.domain.flood.FloodHazardRepository
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepository
import opsi.sman35jkt.gathra.domain.route.RouteRepository
import opsi.sman35jkt.gathra.domain.route.RouteFailureReason
import opsi.sman35jkt.gathra.domain.route.RouteRepositoryException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapRouteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `starts with Jakarta fallback origin and an empty destination`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(JakartaDemoPoints.origin, state.origin?.point)
        assertEquals(SelectionPointSource.DEMO_FALLBACK, state.origin?.source)
        assertEquals(null, state.destination)
        assertEquals(TravelMode.CAR, state.selectedTravelMode)
        assertEquals(RouteContentState.EMPTY, state.routeContentState)
        assertTrue(state.routes.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `confirming manual origin and destination triggers route calculation`() = runTest {
        val repository = RecordingRouteRepository()
        val viewModel = createViewModel(repository)
        val origin = GeoPoint(latitude = -6.2100, longitude = 106.8100)
        val destination = GeoPoint(latitude = -6.1700, longitude = 106.8400)

        selectPoint(viewModel, PointSelectionMode.ORIGIN, origin)
        advanceUntilIdle()
        assertTrue(repository.requests.isEmpty())

        selectPoint(viewModel, PointSelectionMode.DESTINATION, destination)
        advanceUntilIdle()

        assertEquals(1, repository.requests.size)
        assertEquals(origin, repository.requests.single().origin)
        assertEquals(destination, repository.requests.single().destination)
        assertEquals(RouteContentState.READY, viewModel.uiState.value.routeContentState)
        assertEquals(2, viewModel.uiState.value.routes.size)
    }

    @Test
    fun `changing car to motorcycle recalculates routes with a different ETA`() = runTest {
        val repository = RecordingRouteRepository()
        val viewModel = createViewModel(repository)
        selectPoint(
            viewModel = viewModel,
            mode = PointSelectionMode.DESTINATION,
            point = JakartaDemoPoints.suggestedDestination,
        )
        advanceUntilIdle()
        val carEta = requireNotNull(viewModel.uiState.value.selectedRoute).summary.etaMinutes

        viewModel.onAction(MapRouteAction.TravelModeSelected(TravelMode.MOTORCYCLE))
        advanceUntilIdle()

        val motorcycleEta =
            requireNotNull(viewModel.uiState.value.selectedRoute).summary.etaMinutes
        assertEquals(listOf(TravelMode.CAR, TravelMode.MOTORCYCLE), repository.requests.map {
            it.travelMode
        })
        assertNotEquals(carEta, motorcycleEta)
        assertTrue(motorcycleEta < carEta)
    }

    @Test
    fun `swap exchanges origin and destination and recalculates`() = runTest {
        val repository = RecordingRouteRepository()
        val viewModel = createViewModel(repository)
        selectPoint(
            viewModel = viewModel,
            mode = PointSelectionMode.DESTINATION,
            point = JakartaDemoPoints.suggestedDestination,
        )
        advanceUntilIdle()
        val originalOrigin = requireNotNull(viewModel.uiState.value.origin)
        val originalDestination = requireNotNull(viewModel.uiState.value.destination)

        viewModel.onAction(MapRouteAction.SwapPoints)
        advanceUntilIdle()

        assertEquals(originalDestination, viewModel.uiState.value.origin)
        assertEquals(originalOrigin, viewModel.uiState.value.destination)
        assertEquals(originalDestination.point, repository.requests.last().origin)
        assertEquals(originalOrigin.point, repository.requests.last().destination)
    }

    @Test
    fun `repository error is recoverable through retry`() = runTest {
        val repository = RecordingRouteRepository(shouldFail = true)
        val viewModel = createViewModel(repository)
        selectPoint(
            viewModel = viewModel,
            mode = PointSelectionMode.DESTINATION,
            point = JakartaDemoPoints.suggestedDestination,
        )
        advanceUntilIdle()

        assertEquals(RouteContentState.ERROR, viewModel.uiState.value.routeContentState)
        assertEquals(
            MapRouteError.ROUTE_CALCULATION_FAILED,
            viewModel.uiState.value.error,
        )

        repository.shouldFail = false
        viewModel.onAction(MapRouteAction.RetryRoute)
        advanceUntilIdle()

        assertEquals(RouteContentState.READY, viewModel.uiState.value.routeContentState)
        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(2, viewModel.uiState.value.routes.size)
    }

    @Test
    fun `typed repository error produces an offline retry state`() = runTest {
        val repository = object : RouteRepository {
            override suspend fun getRoutes(request: RouteRequest): List<RouteOption> {
                throw RouteRepositoryException(RouteFailureReason.OFFLINE)
            }
        }
        val viewModel = createViewModel(repository)

        selectPoint(
            viewModel = viewModel,
            mode = PointSelectionMode.DESTINATION,
            point = JakartaDemoPoints.suggestedDestination,
        )
        advanceUntilIdle()

        assertEquals(RouteContentState.ERROR, viewModel.uiState.value.routeContentState)
        assertEquals(MapRouteError.ROUTE_OFFLINE, viewModel.uiState.value.error)
    }

    @Test
    fun `location failure does not replace a recoverable route error`() = runTest {
        val repository = object : RouteRepository {
            override suspend fun getRoutes(request: RouteRequest): List<RouteOption> {
                throw RouteRepositoryException(RouteFailureReason.OFFLINE)
            }
        }
        val locationRepository = object : LocationRepository {
            override suspend fun locateOnce(): LocationLookupResult =
                LocationLookupResult.LocationDisabled
        }
        val viewModel = createViewModel(
            routeRepository = repository,
            locationRepository = locationRepository,
        )
        selectPoint(
            viewModel = viewModel,
            mode = PointSelectionMode.DESTINATION,
            point = JakartaDemoPoints.suggestedDestination,
        )
        advanceUntilIdle()

        viewModel.onAction(
            MapRouteAction.LocationPermissionResult(
                preciseGranted = true,
                approximateGranted = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(RouteContentState.ERROR, viewModel.uiState.value.routeContentState)
        assertEquals(MapRouteError.ROUTE_OFFLINE, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLocating)
    }

    @Test
    fun `stale canceled route cannot replace the latest mode result`() = runTest {
        val repository = StaleRouteRepository()
        val viewModel = createViewModel(repository)

        selectPoint(
            viewModel = viewModel,
            mode = PointSelectionMode.DESTINATION,
            point = JakartaDemoPoints.suggestedDestination,
        )
        repository.firstRequestStarted.await()

        viewModel.onAction(MapRouteAction.TravelModeSelected(TravelMode.MOTORCYCLE))
        advanceUntilIdle()
        repository.releaseFirstRequest.complete(Unit)
        advanceUntilIdle()

        assertEquals(TravelMode.MOTORCYCLE, viewModel.uiState.value.selectedTravelMode)
        assertEquals(2, repository.requests.size)
        assertEquals(
            FakeRouteRepository(loadingDelayMillis = 0)
                .getRoutes(repository.requests.last())
                .first()
                .summary
                .etaMinutes,
            viewModel.uiState.value.selectedRoute?.summary?.etaMinutes,
        )
    }

    @Test
    fun `permission denial retains fallback and still permits demo routing`() = runTest {
        val repository = RecordingRouteRepository()
        val viewModel = createViewModel(repository)

        viewModel.onAction(
            MapRouteAction.LocationPermissionResult(
                preciseGranted = false,
                approximateGranted = false,
            ),
        )

        assertEquals(
            LocationPermissionState.DENIED,
            viewModel.uiState.value.locationPermissionState,
        )
        assertEquals(JakartaDemoPoints.origin, viewModel.uiState.value.origin?.point)
        assertEquals(
            SelectionPointSource.DEMO_FALLBACK,
            viewModel.uiState.value.origin?.source,
        )

        selectPoint(
            viewModel = viewModel,
            mode = PointSelectionMode.DESTINATION,
            point = JakartaDemoPoints.suggestedDestination,
        )
        advanceUntilIdle()

        assertEquals(RouteContentState.READY, viewModel.uiState.value.routeContentState)
        assertEquals(2, viewModel.uiState.value.routes.size)
    }

    @Test
    fun `tapping start with permission emits selected navigation route`() = runTest {
        val viewModel = createViewModel()
        selectPoint(
            viewModel = viewModel,
            mode = PointSelectionMode.DESTINATION,
            point = JakartaDemoPoints.suggestedDestination,
        )
        advanceUntilIdle()
        viewModel.onAction(
            MapRouteAction.LocationPermissionResult(
                preciseGranted = true,
                approximateGranted = true,
            ),
        )
        advanceUntilIdle()
        val selectedRoute = requireNotNull(viewModel.uiState.value.selectedRoute)
        val effect = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.first()
        }

        viewModel.onAction(MapRouteAction.PreviewClicked)
        assertTrue(viewModel.uiState.value.isPermissionRationaleVisible)
        viewModel.onAction(MapRouteAction.PermissionRationaleAccepted)

        assertEquals(
            MapRouteEffect.StartNavigation(
                route = selectedRoute,
                destination = JakartaDemoPoints.suggestedDestination,
                travelMode = TravelMode.CAR,
            ),
            effect.await(),
        )
    }

    @Test
    fun `tapping start without permission shows navigation rationale`() = runTest {
        val viewModel = createViewModel()
        selectPoint(
            viewModel = viewModel,
            mode = PointSelectionMode.DESTINATION,
            point = JakartaDemoPoints.suggestedDestination,
        )
        advanceUntilIdle()

        viewModel.onAction(MapRouteAction.PreviewClicked)

        assertTrue(viewModel.uiState.value.isPermissionRationaleVisible)
        assertTrue(viewModel.uiState.value.isNavigationPermissionRequest)
    }

    @Test
    fun `late location result cannot overwrite a manually selected origin`() = runTest {
        val delayedLocation = CompletableDeferred<LocationLookupResult>()
        val locationRepository = object : LocationRepository {
            override suspend fun locateOnce(): LocationLookupResult = delayedLocation.await()
        }
        val viewModel = createViewModel(locationRepository = locationRepository)
        val manualOrigin = GeoPoint(latitude = -6.2140, longitude = 106.8010)

        viewModel.onAction(
            MapRouteAction.LocationPermissionResult(
                preciseGranted = true,
                approximateGranted = true,
            ),
        )
        selectPoint(viewModel, PointSelectionMode.ORIGIN, manualOrigin)
        delayedLocation.complete(
            LocationLookupResult.Success(
                point = GeoPoint(latitude = -6.1200, longitude = 106.9000),
                fromLastKnown = false,
            ),
        )
        advanceUntilIdle()

        assertEquals(manualOrigin, viewModel.uiState.value.origin?.point)
        assertEquals(
            SelectionPointSource.MAP_SELECTION,
            viewModel.uiState.value.origin?.source,
        )
        assertFalse(viewModel.uiState.value.isLocating)
    }

    private fun createViewModel(
        routeRepository: RouteRepository = RecordingRouteRepository(),
        locationRepository: LocationRepository = StubLocationRepository(),
        geocodingRepository: GeocodingRepository = FakeGeocodingRepository(
                loadingDelayMillis = 0,
            ),
        floodHazardRepository: FloodHazardRepository = FakeFloodHazardRepository(),
    ): MapRouteViewModel = MapRouteViewModel(
        routeRepository = routeRepository,
        locationRepository = locationRepository,
        geocodingRepository = geocodingRepository,
        floodHazardRepository = floodHazardRepository,
        workDispatcher = StandardTestDispatcher(),
    )

    private fun selectPoint(
        viewModel: MapRouteViewModel,
        mode: PointSelectionMode,
        point: GeoPoint,
    ) {
        viewModel.onAction(MapRouteAction.StartPointSelection(mode))
        viewModel.onAction(MapRouteAction.MapPointTapped(point))
        viewModel.onAction(MapRouteAction.ConfirmPointSelection)
    }

    private class RecordingRouteRepository(
        var shouldFail: Boolean = false,
    ) : RouteRepository {
        private val delegate = FakeRouteRepository(loadingDelayMillis = 0)
        val requests = mutableListOf<RouteRequest>()

        override suspend fun getRoutes(request: RouteRequest): List<RouteOption> {
            requests += request
            if (shouldFail) error("Expected test route failure")
            return delegate.getRoutes(request)
        }
    }

    private class StaleRouteRepository : RouteRepository {
        private val delegate = FakeRouteRepository(loadingDelayMillis = 0)
        val requests = mutableListOf<RouteRequest>()
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()

        override suspend fun getRoutes(request: RouteRequest): List<RouteOption> {
            requests += request
            if (requests.size == 1) {
                firstRequestStarted.complete(Unit)
                withContext(NonCancellable) {
                    releaseFirstRequest.await()
                }
            }
            return delegate.getRoutes(request)
        }
    }

    private class StubLocationRepository(
        private val result: LocationLookupResult = LocationLookupResult.Unavailable,
    ) : LocationRepository {
        override suspend fun locateOnce(): LocationLookupResult = result
    }
}
