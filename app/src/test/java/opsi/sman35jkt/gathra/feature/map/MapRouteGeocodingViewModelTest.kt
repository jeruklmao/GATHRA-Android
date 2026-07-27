package opsi.sman35jkt.gathra.feature.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import opsi.sman35jkt.gathra.core.location.LocationLookupResult
import opsi.sman35jkt.gathra.core.location.LocationRepository
import opsi.sman35jkt.gathra.core.map.JakartaDemoPoints
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.PlaceCategory
import opsi.sman35jkt.gathra.core.model.PlaceSuggestion
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.core.model.SelectedPlace
import opsi.sman35jkt.gathra.core.model.SelectionPointSource
import opsi.sman35jkt.gathra.data.route.FakeRouteRepository
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepository
import opsi.sman35jkt.gathra.domain.route.RouteRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapRouteGeocodingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `place selection updates label and recalculates route`() = runTest {
        val routeRepository = RecordingRouteRepository()
        val viewModel = viewModel(routeRepository = routeRepository)
        val place = selectedPlace()

        viewModel.onAction(
            MapRouteAction.PlaceSelected(
                mode = PointSelectionMode.DESTINATION,
                place = place,
            ),
        )
        advanceUntilIdle()

        assertEquals(place.position, viewModel.uiState.value.destination?.point)
        assertEquals(
            SelectionPointSource.GEOCODING_SEARCH,
            viewModel.uiState.value.destination?.source,
        )
        assertEquals(
            "Monumen Nasional",
            viewModel.uiState.value.destination?.displayName,
        )
        assertEquals(place.position, routeRepository.requests.single().destination)
        assertEquals(RouteContentState.READY, viewModel.uiState.value.routeContentState)
    }

    @Test
    fun `reverse label never replaces authoritative map coordinate`() = runTest {
        val selectedPoint = GeoPoint(-6.20123, 106.81789)
        val providerPoint = GeoPoint(-6.19, 106.83)
        val geocoding = StubGeocodingRepository(
            reverseResult = selectedPlace().copy(position = providerPoint),
        )
        val routeRepository = RecordingRouteRepository()
        val viewModel = viewModel(
            routeRepository = routeRepository,
            geocodingRepository = geocoding,
        )

        selectMapPoint(viewModel, selectedPoint)
        advanceUntilIdle()

        assertEquals(selectedPoint, viewModel.uiState.value.destination?.point)
        assertEquals(
            selectedPoint,
            routeRepository.requests.single().destination,
        )
        assertEquals(
            "Monumen Nasional",
            viewModel.uiState.value.destination?.displayName,
        )
    }

    @Test
    fun `reverse failure retains coordinate and does not block routing`() = runTest {
        val point = GeoPoint(-6.205, 106.82)
        val viewModel = viewModel(
            geocodingRepository = StubGeocodingRepository(
                reverseFailure = true,
            ),
        )

        selectMapPoint(viewModel, point)
        advanceUntilIdle()

        assertEquals(point, viewModel.uiState.value.destination?.point)
        assertNull(viewModel.uiState.value.destination?.displayName)
        assertEquals(RouteContentState.READY, viewModel.uiState.value.routeContentState)
        assertTrue(viewModel.uiState.value.routes.isNotEmpty())
    }

    @Test
    fun `current location action can target destination`() = runTest {
        val current = GeoPoint(-6.24, 106.79)
        val viewModel = viewModel(
            locationRepository = object : LocationRepository {
                override suspend fun locateOnce() = LocationLookupResult.Success(
                    point = current,
                    fromLastKnown = false,
                )
            },
        )

        viewModel.onAction(
            MapRouteAction.UseCurrentLocation(
                PointSelectionMode.DESTINATION,
            ),
        )
        viewModel.onAction(
            MapRouteAction.LocationPermissionResult(
                preciseGranted = true,
                approximateGranted = false,
            ),
        )
        advanceUntilIdle()

        assertEquals(current, viewModel.uiState.value.destination?.point)
        assertEquals(
            SelectionPointSource.CURRENT_LOCATION,
            viewModel.uiState.value.destination?.source,
        )
        assertEquals(JakartaDemoPoints.origin, viewModel.uiState.value.origin?.point)
    }

    private fun viewModel(
        routeRepository: RouteRepository = RecordingRouteRepository(),
        locationRepository: LocationRepository = object : LocationRepository {
            override suspend fun locateOnce() = LocationLookupResult.Unavailable
        },
        geocodingRepository: GeocodingRepository = StubGeocodingRepository(),
    ) = MapRouteViewModel(
        routeRepository = routeRepository,
        locationRepository = locationRepository,
        geocodingRepository = geocodingRepository,
        workDispatcher = StandardTestDispatcher(),
    )

    private fun selectMapPoint(
        viewModel: MapRouteViewModel,
        point: GeoPoint,
    ) {
        viewModel.onAction(
            MapRouteAction.StartPointSelection(
                PointSelectionMode.DESTINATION,
            ),
        )
        viewModel.onAction(MapRouteAction.MapPointTapped(point))
        viewModel.onAction(MapRouteAction.ConfirmPointSelection)
    }

    private class RecordingRouteRepository : RouteRepository {
        private val delegate = FakeRouteRepository(loadingDelayMillis = 0)
        val requests = mutableListOf<RouteRequest>()

        override suspend fun getRoutes(request: RouteRequest): List<RouteOption> {
            requests += request
            return delegate.getRoutes(request)
        }
    }

    private class StubGeocodingRepository(
        private val reverseResult: SelectedPlace? = null,
        private val reverseFailure: Boolean = false,
    ) : GeocodingRepository {
        override suspend fun autocomplete(
            query: String,
            proximity: GeoPoint?,
            limit: Int,
        ) = emptyList<PlaceSuggestion>()

        override suspend fun search(
            query: String,
            proximity: GeoPoint?,
            limit: Int,
        ) = emptyList<PlaceSuggestion>()

        override suspend fun lookup(id: String) = selectedPlace()

        override suspend fun reverse(point: GeoPoint): SelectedPlace? {
            if (reverseFailure) error("Reverse geocoding failed")
            return reverseResult
        }
    }

    private companion object {
        fun selectedPlace() = SelectedPlace(
            id = "monas",
            name = "Monumen Nasional",
            formattedAddress = "Gambir, Jakarta Pusat",
            position = GeoPoint(-6.1754, 106.8272),
            category = PlaceCategory.LANDMARK,
            insideSupportedRegion = true,
        )
    }
}
