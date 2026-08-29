package opsi.sman35jkt.gathra.feature.map

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import opsi.sman35jkt.gathra.core.location.LocationLookupResult
import opsi.sman35jkt.gathra.core.location.LocationRepository
import opsi.sman35jkt.gathra.core.map.TestRoutePoints
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import opsi.sman35jkt.gathra.core.model.GeoBounds
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.data.flood.FakeFloodHazardRepository
import opsi.sman35jkt.gathra.data.sensor.FakeSensorRepository
import opsi.sman35jkt.gathra.data.geocoding.FakeGeocodingRepository
import opsi.sman35jkt.gathra.data.route.FakeRouteRepository
import opsi.sman35jkt.gathra.domain.flood.FloodHazardRepository
import opsi.sman35jkt.gathra.domain.route.RouteRepository
import opsi.sman35jkt.gathra.domain.sensor.SensorCurrentState
import opsi.sman35jkt.gathra.domain.sensor.SensorRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapRouteFloodSafetyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `construction does not poll and screen lifecycle owns exactly one polling job`() = runTest {
        val floodRepository = RecordingFloodRepository()
        val viewModel = createViewModel(
            floodRepository = floodRepository,
            config = FloodRefreshConfig(
                pollingIntervalMillis = 1_000,
                viewportDebounceMillis = 0,
                snapshotMismatchDebounceMillis = 100,
            ),
        )

        runCurrent()
        assertEquals(0, floodRepository.calls)

        viewModel.onAction(MapRouteAction.ScreenStarted)
        runCurrent()
        assertEquals(1, floodRepository.calls)

        viewModel.onAction(MapRouteAction.ScreenStarted)
        runCurrent()
        assertEquals(1, floodRepository.calls)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, floodRepository.calls)

        viewModel.onAction(MapRouteAction.ScreenStopped)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, floodRepository.calls)

        viewModel.onAction(MapRouteAction.ScreenStopped)
        viewModel.onAction(MapRouteAction.ScreenStarted)
        runCurrent()
        assertEquals(3, floodRepository.calls)
        viewModel.onAction(MapRouteAction.ScreenStopped)
    }

    @Test
    fun `screen stop preserves the snapshot but marks transport refresh retention`() = runTest {
        val floodRepository = RecordingFloodRepository()
        val viewModel = createViewModel(floodRepository = floodRepository)
        viewModel.onAction(MapRouteAction.ScreenStarted)
        runCurrent()
        val snapshot = requireNotNull(viewModel.uiState.value.floodHazardSnapshot)

        viewModel.onAction(MapRouteAction.ScreenStopped)

        assertEquals(snapshot, viewModel.uiState.value.floodHazardSnapshot)
        assertEquals(
            FloodRefreshStatus.RETAINED_AFTER_ERROR,
            viewModel.uiState.value.floodRefreshStatus,
        )
        assertFalse(viewModel.uiState.value.isSelectedRouteRiskCurrent)
    }

    @Test
    fun `matching snapshot IDs do not recalculate`() = runTest {
        val routeRepository = SnapshotRouteRepository()
        val floodRepository = RecordingFloodRepository()
        val viewModel = createViewModel(routeRepository, floodRepository)

        refreshFlood(viewModel)
        selectDestination(viewModel)
        runCurrent()
        assertEquals(1, routeRepository.calls)
        assertEquals(
            FloodRouteSyncState.SYNCHRONIZED,
            viewModel.uiState.value.floodRouteSyncState,
        )

        refreshFlood(viewModel)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, routeRepository.calls)
    }

    @Test
    fun `failed hazard refresh preserves the last snapshot and marks it stale`() = runTest {
        val floodRepository = RecordingFloodRepository()
        val viewModel = createViewModel(floodRepository = floodRepository)
        refreshFlood(viewModel)
        val previousSnapshot = requireNotNull(
            viewModel.uiState.value.floodHazardSnapshot,
        )

        floodRepository.fail = true
        refreshFlood(viewModel)

        assertEquals(
            previousSnapshot,
            viewModel.uiState.value.floodHazardSnapshot,
        )
        assertEquals(
            FloodRefreshStatus.RETAINED_AFTER_ERROR,
            viewModel.uiState.value.floodRefreshStatus,
        )
        assertFalse(viewModel.uiState.value.isSelectedRouteRiskCurrent)
    }

    @Test
    fun `one newer snapshot triggers one bounded recalculation and replaces risk`() = runTest {
        val routeRepository = SnapshotRouteRepository()
        val floodRepository = RecordingFloodRepository()
        val viewModel = createViewModel(routeRepository, floodRepository)
        refreshFlood(viewModel)
        selectDestination(viewModel)
        runCurrent()
        val originalRoute = requireNotNull(viewModel.uiState.value.selectedRoute)

        floodRepository.snapshotId = "snapshot_v2_1"
        routeRepository.snapshotId = "snapshot_v2_1"
        viewModel.onAction(MapRouteAction.RefreshFloodHazards)
        viewModel.onAction(MapRouteAction.RefreshFloodHazards)
        runCurrent()

        assertEquals(
            FloodRouteSyncState.OUTDATED_BY_FLOOD_UPDATE,
            viewModel.uiState.value.floodRouteSyncState,
        )
        assertFalse(viewModel.uiState.value.isSelectedRouteRiskCurrent)
        assertEquals(originalRoute.geometry, viewModel.uiState.value.selectedRoute?.geometry)

        advanceTimeBy(MISMATCH_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(2, routeRepository.calls)
        assertEquals(
            "snapshot_v2_1",
            viewModel.uiState.value.selectedRoute?.risk?.hazardSnapshotId,
        )
        assertEquals(
            FloodRouteSyncState.SYNCHRONIZED,
            viewModel.uiState.value.floodRouteSyncState,
        )
        assertTrue(viewModel.uiState.value.isSelectedRouteRiskCurrent)
    }

    @Test
    fun `failed snapshot recalculation retains geometry marks stale and retries explicitly`() =
        runTest {
            val routeRepository = SnapshotRouteRepository()
            val floodRepository = RecordingFloodRepository()
            val viewModel = createViewModel(routeRepository, floodRepository)
            refreshFlood(viewModel)
            selectDestination(viewModel)
            runCurrent()
            val originalRoute = requireNotNull(viewModel.uiState.value.selectedRoute)

            floodRepository.snapshotId = "snapshot_v2_1"
            routeRepository.snapshotId = "snapshot_v2_1"
            routeRepository.fail = true
            refreshFlood(viewModel)
            advanceTimeBy(MISMATCH_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals(2, routeRepository.calls)
            assertEquals(originalRoute.geometry, viewModel.uiState.value.selectedRoute?.geometry)
            assertEquals(
                FloodRouteSyncState.STALE,
                viewModel.uiState.value.floodRouteSyncState,
            )
            assertFalse(viewModel.uiState.value.isSelectedRouteRiskCurrent)

            refreshFlood(viewModel)
            advanceTimeBy(MISMATCH_DEBOUNCE_MILLIS * 3)
            runCurrent()
            assertEquals(2, routeRepository.calls)

            routeRepository.fail = false
            viewModel.onAction(MapRouteAction.RetryFloodRouteUpdate)
            runCurrent()
            assertEquals(3, routeRepository.calls)
            assertEquals(
                FloodRouteSyncState.SYNCHRONIZED,
                viewModel.uiState.value.floodRouteSyncState,
            )
        }

    @Test
    fun `stale flood recalculation cannot overwrite a newer snapshot result`() = runTest {
        val routeRepository = DelayedSnapshotRouteRepository()
        val floodRepository = RecordingFloodRepository()
        val viewModel = createViewModel(routeRepository, floodRepository)
        refreshFlood(viewModel)
        selectDestination(viewModel)
        runCurrent()

        floodRepository.snapshotId = "snapshot_v2_1"
        refreshFlood(viewModel)
        advanceTimeBy(MISMATCH_DEBOUNCE_MILLIS)
        runCurrent()
        routeRepository.secondRequestStarted.await()

        floodRepository.snapshotId = "snapshot_v3_1"
        refreshFlood(viewModel)
        advanceTimeBy(MISMATCH_DEBOUNCE_MILLIS)
        runCurrent()
        assertEquals(
            "snapshot_v3_1",
            viewModel.uiState.value.selectedRoute?.risk?.hazardSnapshotId,
        )

        routeRepository.releaseSecondRequest.complete(Unit)
        runCurrent()

        assertEquals(3, routeRepository.calls)
        assertEquals(
            "snapshot_v3_1",
            viewModel.uiState.value.selectedRoute?.risk?.hazardSnapshotId,
        )
    }

    @Test
    fun `sensor detail refreshes every thirty seconds only while the shared sheet is open`() =
        runTest {
            val sensorRepository = FakeSensorRepository(
                SensorCurrentState(
                    "NODE-1", GeoPoint(-6.235, 106.720), 125,
                    FloodHazardLevel.MEDIUM, FloodHazardFreshness.FRESH,
                    1L, 1600, 31.2, 72.4, null,
                ),
            )
            val floodRepository = object : FloodHazardRepository {
                override suspend fun getActiveHazards(bounds: GeoBounds?) = sensorSnapshot()
            }
            val viewModel = createViewModel(
                floodRepository = floodRepository,
                sensorRepository = sensorRepository,
                config = FloodRefreshConfig(
                    pollingIntervalMillis = 60_000,
                    viewportDebounceMillis = 0,
                    snapshotMismatchDebounceMillis = 100,
                    sensorDetailPollingIntervalMillis = 30_000,
                ),
            )
            refreshFlood(viewModel)
            assertEquals(1, sensorRepository.calls)

            viewModel.onAction(MapRouteAction.FloodHazardSelected("sensor-NODE-1"))
            runCurrent()
            assertEquals(2, sensorRepository.calls)
            advanceTimeBy(29_999)
            runCurrent()
            assertEquals(2, sensorRepository.calls)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(3, sensorRepository.calls)

            viewModel.onAction(MapRouteAction.RefreshSensorDetail)
            runCurrent()
            assertEquals(4, sensorRepository.calls)
            viewModel.onAction(MapRouteAction.DismissFloodHazardDetails)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(4, sensorRepository.calls)
        }

    private fun createViewModel(
        routeRepository: RouteRepository = SnapshotRouteRepository(),
        floodRepository: FloodHazardRepository = RecordingFloodRepository(),
        config: FloodRefreshConfig = FloodRefreshConfig(
            pollingIntervalMillis = 1_000,
            viewportDebounceMillis = 0,
            snapshotMismatchDebounceMillis = MISMATCH_DEBOUNCE_MILLIS,
        ),
        sensorRepository: SensorRepository = FakeSensorRepository(),
    ) = MapRouteViewModel(
        routeRepository = routeRepository,
        locationRepository = object : LocationRepository {
            override suspend fun locateOnce(): LocationLookupResult =
                LocationLookupResult.Unavailable
        },
        geocodingRepository = FakeGeocodingRepository(loadingDelayMillis = 0),
        floodHazardRepository = floodRepository,
        sensorRepository = sensorRepository,
        workDispatcher = Dispatchers.Main,
        floodRefreshConfig = config,
    )

    private fun TestScope.refreshFlood(viewModel: MapRouteViewModel) {
        viewModel.onAction(MapRouteAction.RefreshFloodHazards)
        runCurrent()
    }

    private fun selectDestination(viewModel: MapRouteViewModel) {
        // This is explicit deterministic test setup; production has no seeded origin.
        viewModel.onAction(
            MapRouteAction.StartPointSelection(PointSelectionMode.ORIGIN),
        )
        viewModel.onAction(
            MapRouteAction.MapPointTapped(TestRoutePoints.origin),
        )
        viewModel.onAction(MapRouteAction.ConfirmPointSelection)
        viewModel.onAction(
            MapRouteAction.StartPointSelection(PointSelectionMode.DESTINATION),
        )
        viewModel.onAction(
            MapRouteAction.MapPointTapped(TestRoutePoints.destination),
        )
        viewModel.onAction(MapRouteAction.ConfirmPointSelection)
    }

    private class RecordingFloodRepository : FloodHazardRepository {
        var snapshotId = FakeFloodHazardRepository.DEMO_FLOOD_SNAPSHOT_ID
        var fail = false
        var calls = 0

        override suspend fun getActiveHazards(bounds: GeoBounds?): FloodHazardSnapshot {
            calls += 1
            if (fail) error("flood unavailable")
            return FakeFloodHazardRepository(
                snapshotId = snapshotId,
            ).getActiveHazards(bounds)
        }
    }

    private open class SnapshotRouteRepository : RouteRepository {
        var snapshotId = FakeFloodHazardRepository.DEMO_FLOOD_SNAPSHOT_ID
        var fail = false
        var calls = 0
        private val delegate = FakeRouteRepository(loadingDelayMillis = 0)

        override suspend fun getRoutes(request: RouteRequest): List<RouteOption> {
            calls += 1
            if (fail) error("route unavailable")
            return routesFor(request, snapshotId)
        }

        protected suspend fun routesFor(
            request: RouteRequest,
            responseSnapshotId: String,
        ): List<RouteOption> = delegate.getRoutes(request).map { route ->
            route.copy(
                id = "${route.id}-$responseSnapshotId",
                risk = route.risk?.copy(hazardSnapshotId = responseSnapshotId),
            )
        }
    }

    private class DelayedSnapshotRouteRepository : SnapshotRouteRepository() {
        val secondRequestStarted = CompletableDeferred<Unit>()
        val releaseSecondRequest = CompletableDeferred<Unit>()

        override suspend fun getRoutes(request: RouteRequest): List<RouteOption> {
            calls += 1
            val responseSnapshotId = when (calls) {
                1 -> FakeFloodHazardRepository.DEMO_FLOOD_SNAPSHOT_ID
                2 -> {
                    secondRequestStarted.complete(Unit)
                    withContext(NonCancellable) {
                        releaseSecondRequest.await()
                    }
                    "snapshot_v2_1"
                }
                else -> "snapshot_v3_1"
            }
            return routesFor(request, responseSnapshotId)
        }
    }

    private companion object {
        const val MISMATCH_DEBOUNCE_MILLIS = 100L
    }

    private fun sensorSnapshot() = FloodHazardSnapshot(
        "sensor-snapshot", 1L, null, FloodHazardSource.SENSOR,
        listOf(
            FloodHazardPolygon(
                "sensor-NODE-1", FloodHazardLevel.MEDIUM,
                listOf(listOf(
                    GeoPoint(-6.23, 106.71), GeoPoint(-6.24, 106.72),
                    GeoPoint(-6.22, 106.73), GeoPoint(-6.23, 106.71),
                )), null, null, 1L, 2L, FloodHazardSource.SENSOR,
                listOf("NODE-1"), 0.35, emptyList(), FloodHazardFreshness.FRESH,
            ),
        ),
    )
}
