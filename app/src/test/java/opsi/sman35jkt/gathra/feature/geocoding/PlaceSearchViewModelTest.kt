package opsi.sman35jkt.gathra.feature.geocoding

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.PlaceCategory
import opsi.sman35jkt.gathra.core.model.PlaceSuggestion
import opsi.sman35jkt.gathra.core.model.SelectedPlace
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingFailureReason
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepository
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepositoryException
import opsi.sman35jkt.gathra.feature.map.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `autocomplete is debounced and blank input makes no request`() = runTest {
        val repository = RecordingGeocodingRepository()
        val viewModel = PlaceSearchViewModel(
            repository = repository,
            debounceMillis = 400,
        )
        open(viewModel)

        viewModel.onAction(PlaceSearchAction.QueryChanged(""))
        viewModel.onAction(PlaceSearchAction.QueryChanged("Mon"))
        advanceTimeBy(399)
        runCurrent()
        assertTrue(repository.autocompleteQueries.isEmpty())

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf("Mon"), repository.autocompleteQueries)
        assertEquals(PlaceSearchStatus.RESULTS, viewModel.uiState.value.status)
    }

    @Test
    fun `normalized duplicate query does not make another request`() = runTest {
        val repository = RecordingGeocodingRepository()
        val viewModel = PlaceSearchViewModel(
            repository = repository,
            debounceMillis = 0,
        )
        open(viewModel)
        viewModel.onAction(PlaceSearchAction.QueryChanged("Mon"))
        advanceUntilIdle()

        viewModel.onAction(PlaceSearchAction.QueryChanged("  Mon  "))
        advanceUntilIdle()

        assertEquals(listOf("Mon"), repository.autocompleteQueries)
        assertEquals(PlaceSearchStatus.RESULTS, viewModel.uiState.value.status)
    }

    @Test
    fun `new query cancels old autocomplete request`() = runTest {
        val repository = CancellableAutocompleteRepository()
        val viewModel = PlaceSearchViewModel(
            repository = repository,
            debounceMillis = 0,
        )
        open(viewModel)

        viewModel.onAction(PlaceSearchAction.QueryChanged("Mon"))
        repository.firstStarted.await()
        viewModel.onAction(PlaceSearchAction.QueryChanged("Ragunan"))
        advanceUntilIdle()

        assertTrue(repository.firstCancelled)
        assertEquals("Ragunan", viewModel.uiState.value.suggestions.single().primaryText)
    }

    @Test
    fun `stale explicit response cannot overwrite newer query`() = runTest {
        val repository = StaleSearchRepository()
        val viewModel = PlaceSearchViewModel(
            repository = repository,
            debounceMillis = 10_000,
        )
        open(viewModel)

        viewModel.onAction(PlaceSearchAction.QueryChanged("Monas"))
        viewModel.onAction(PlaceSearchAction.Submit)
        repository.firstStarted.await()
        viewModel.onAction(PlaceSearchAction.QueryChanged("Ragunan"))
        viewModel.onAction(PlaceSearchAction.Submit)
        advanceUntilIdle()
        assertEquals("Ragunan", viewModel.uiState.value.suggestions.single().primaryText)

        repository.releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals("Ragunan", viewModel.uiState.value.suggestions.single().primaryText)
    }

    @Test
    fun `selecting supported suggestion performs lookup and emits selection`() = runTest {
        val repository = RecordingGeocodingRepository()
        val viewModel = PlaceSearchViewModel(
            repository = repository,
            debounceMillis = 0,
        )
        val effect = async { viewModel.effects.first() }
        open(viewModel)
        viewModel.onAction(PlaceSearchAction.QueryChanged("Monas"))
        advanceUntilIdle()

        viewModel.onAction(PlaceSearchAction.SuggestionSelected("monas"))
        advanceUntilIdle()

        val selected = effect.await() as PlaceSearchEffect.PlaceSelected
        assertEquals(SearchTargetField.DESTINATION, selected.targetField)
        assertEquals("Monumen Nasional", selected.place.name)
        assertFalse(viewModel.uiState.value.isVisible)
        assertEquals(listOf("monas"), repository.lookupIds)
    }

    @Test
    fun `outside coverage suggestion is not looked up`() = runTest {
        val repository = RecordingGeocodingRepository(
            suggestions = listOf(suggestion(inside = false)),
        )
        val viewModel = PlaceSearchViewModel(
            repository = repository,
            debounceMillis = 0,
        )
        open(viewModel)
        viewModel.onAction(PlaceSearchAction.QueryChanged("Monas"))
        advanceUntilIdle()

        viewModel.onAction(PlaceSearchAction.SuggestionSelected("monas"))

        assertTrue(repository.lookupIds.isEmpty())
        assertEquals(
            PlaceSearchError.OUTSIDE_COVERAGE,
            viewModel.uiState.value.error,
        )
        assertTrue(viewModel.uiState.value.isVisible)
    }

    @Test
    fun `lookup failure becomes recoverable error`() = runTest {
        val repository = RecordingGeocodingRepository(
            lookupFailure = GeocodingFailureReason.PLACE_NOT_FOUND,
        )
        val viewModel = PlaceSearchViewModel(
            repository = repository,
            debounceMillis = 0,
        )
        open(viewModel)
        viewModel.onAction(PlaceSearchAction.QueryChanged("Monas"))
        advanceUntilIdle()

        viewModel.onAction(PlaceSearchAction.SuggestionSelected("monas"))
        advanceUntilIdle()

        assertEquals(PlaceSearchStatus.ERROR, viewModel.uiState.value.status)
        assertEquals(
            PlaceSearchError.PLACE_NOT_FOUND,
            viewModel.uiState.value.error,
        )
        assertNull(viewModel.uiState.value.selectedSuggestionId)
    }

    @Test
    fun `typed query remains in ViewModel state`() = runTest {
        val viewModel = PlaceSearchViewModel(
            repository = RecordingGeocodingRepository(),
            debounceMillis = 10_000,
        )
        open(viewModel)

        viewModel.onAction(
            PlaceSearchAction.QueryChanged("Jalan Sudirman"),
        )

        assertEquals("Jalan Sudirman", viewModel.uiState.value.query)
        assertEquals(PlaceSearchStatus.TYPING, viewModel.uiState.value.status)
    }

    private fun open(viewModel: PlaceSearchViewModel) {
        viewModel.onAction(
            PlaceSearchAction.Open(
                targetField = SearchTargetField.DESTINATION,
                proximity = GeoPoint(-6.2, 106.8167),
            ),
        )
    }

    private class RecordingGeocodingRepository(
        private val suggestions: List<PlaceSuggestion> = listOf(suggestion()),
        private val lookupFailure: GeocodingFailureReason? = null,
    ) : GeocodingRepository {
        val autocompleteQueries = mutableListOf<String>()
        val lookupIds = mutableListOf<String>()

        override suspend fun autocomplete(
            query: String,
            proximity: GeoPoint?,
            limit: Int,
        ): List<PlaceSuggestion> {
            autocompleteQueries += query
            return suggestions
        }

        override suspend fun search(
            query: String,
            proximity: GeoPoint?,
            limit: Int,
        ): List<PlaceSuggestion> = suggestions

        override suspend fun lookup(id: String): SelectedPlace {
            lookupIds += id
            lookupFailure?.let {
                throw GeocodingRepositoryException(it)
            }
            return selectedPlace()
        }

        override suspend fun reverse(point: GeoPoint): SelectedPlace? = null
    }

    private class CancellableAutocompleteRepository : GeocodingRepository {
        val firstStarted = CompletableDeferred<Unit>()
        var firstCancelled = false

        override suspend fun autocomplete(
            query: String,
            proximity: GeoPoint?,
            limit: Int,
        ): List<PlaceSuggestion> {
            if (query == "Mon") {
                firstStarted.complete(Unit)
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    firstCancelled = true
                }
            }
            return listOf(
                suggestion().copy(primaryText = query),
            )
        }

        override suspend fun search(
            query: String,
            proximity: GeoPoint?,
            limit: Int,
        ) = emptyList<PlaceSuggestion>()

        override suspend fun lookup(id: String) = selectedPlace()

        override suspend fun reverse(point: GeoPoint): SelectedPlace? = null
    }

    private class StaleSearchRepository : GeocodingRepository {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        private var calls = 0

        override suspend fun autocomplete(
            query: String,
            proximity: GeoPoint?,
            limit: Int,
        ) = emptyList<PlaceSuggestion>()

        override suspend fun search(
            query: String,
            proximity: GeoPoint?,
            limit: Int,
        ): List<PlaceSuggestion> {
            calls += 1
            if (calls == 1) {
                firstStarted.complete(Unit)
                withContext(NonCancellable) {
                    releaseFirst.await()
                }
                return listOf(
                    suggestion().copy(primaryText = "Monumen Nasional"),
                )
            }
            return listOf(suggestion().copy(primaryText = query))
        }

        override suspend fun lookup(id: String) = selectedPlace()

        override suspend fun reverse(point: GeoPoint): SelectedPlace? = null
    }

    private companion object {
        fun suggestion(inside: Boolean = true) = PlaceSuggestion(
            id = "monas",
            primaryText = "Monumen Nasional",
            secondaryText = "Gambir, Jakarta Pusat",
            category = PlaceCategory.LANDMARK,
            position = GeoPoint(-6.1754, 106.8272),
            distanceMeters = 1_000,
            insideSupportedRegion = inside,
        )

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
