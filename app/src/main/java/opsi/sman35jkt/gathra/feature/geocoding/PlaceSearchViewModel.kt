package opsi.sman35jkt.gathra.feature.geocoding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.PlaceSuggestion
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingFailureReason
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepository
import opsi.sman35jkt.gathra.domain.geocoding.GeocodingRepositoryException

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PlaceSearchViewModel(
    private val repository: GeocodingRepository,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceSearchUiState())
    val uiState: StateFlow<PlaceSearchUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PlaceSearchEffect>(
        extraBufferCapacity = 4,
    )
    val effects: SharedFlow<PlaceSearchEffect> = _effects.asSharedFlow()

    private val requestGeneration = AtomicLong(0)
    private val autocompleteRequests = MutableStateFlow(
        AutocompleteRequest.disabled(),
    )
    private var explicitSearchJob: Job? = null
    private var lookupJob: Job? = null

    init {
        require(debounceMillis >= 0) {
            "Search debounce cannot be negative."
        }
        viewModelScope.launch {
            autocompleteRequests
                .map { it }
                .distinctUntilChanged()
                .debounce(debounceMillis)
                .flatMapLatest(::autocompleteFlow)
                .collect(::applyAutocompleteOutcome)
        }
    }

    fun onAction(action: PlaceSearchAction) {
        when (action) {
            is PlaceSearchAction.Open -> open(action)
            is PlaceSearchAction.QueryChanged -> queryChanged(action.query)
            PlaceSearchAction.Submit -> explicitSearch()
            is PlaceSearchAction.SuggestionSelected ->
                selectSuggestion(action.id)
            PlaceSearchAction.Retry -> explicitSearch()
            PlaceSearchAction.CurrentLocationSelected ->
                finishWithCurrentLocation()
            PlaceSearchAction.ChooseOnMap -> finishWithMapSelection()
            PlaceSearchAction.Dismiss -> dismiss()
        }
    }

    private fun open(action: PlaceSearchAction.Open) {
        cancelForegroundRequests()
        val token = requestGeneration.incrementAndGet()
        autocompleteRequests.value = AutocompleteRequest.disabled(token)
        _uiState.value = PlaceSearchUiState(
            isVisible = true,
            targetField = action.targetField,
            proximity = action.proximity,
        )
    }

    private fun queryChanged(rawQuery: String) {
        if (!_uiState.value.isVisible) return
        explicitSearchJob?.cancel()
        lookupJob?.cancel()
        val query = rawQuery.take(MAX_QUERY_LENGTH)
        val normalized = query.trim()
        val activeRequest = autocompleteRequests.value
        if (
            normalized.length >= MIN_AUTOCOMPLETE_QUERY_LENGTH &&
            activeRequest.enabled &&
            activeRequest.query == normalized &&
            activeRequest.proximity == _uiState.value.proximity
        ) {
            // Preserve the current result/loading state when only surrounding
            // whitespace changed. The normalized query is distinct.
            _uiState.update { it.copy(query = query) }
            return
        }
        val token = requestGeneration.incrementAndGet()
        _uiState.update {
            it.copy(
                query = query,
                suggestions = emptyList(),
                selectedSuggestionId = null,
                status = when {
                    normalized.isEmpty() -> PlaceSearchStatus.IDLE
                    else -> PlaceSearchStatus.TYPING
                },
                error = null,
            )
        }
        autocompleteRequests.value = if (
            normalized.length >= MIN_AUTOCOMPLETE_QUERY_LENGTH
        ) {
            AutocompleteRequest(
                query = normalized,
                proximity = _uiState.value.proximity,
                token = token,
                enabled = true,
            )
        } else {
            AutocompleteRequest.disabled(token)
        }
    }

    private fun explicitSearch() {
        val state = _uiState.value
        val query = state.query.trim()
        if (!state.isVisible) return
        if (query.length < MIN_EXPLICIT_QUERY_LENGTH) {
            _uiState.update {
                it.copy(
                    status = PlaceSearchStatus.ERROR,
                    error = PlaceSearchError.INVALID_QUERY,
                    suggestions = emptyList(),
                )
            }
            return
        }

        lookupJob?.cancel()
        explicitSearchJob?.cancel()
        val token = requestGeneration.incrementAndGet()
        autocompleteRequests.value = AutocompleteRequest.disabled(token)
        _uiState.update {
            it.copy(
                status = PlaceSearchStatus.LOADING,
                error = null,
                suggestions = emptyList(),
                selectedSuggestionId = null,
            )
        }
        explicitSearchJob = viewModelScope.launch {
            try {
                val suggestions = repository.search(
                    query = query,
                    proximity = state.proximity,
                    limit = EXPLICIT_RESULT_LIMIT,
                )
                if (!isCurrent(token)) return@launch
                showSuggestions(suggestions)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: GeocodingRepositoryException) {
                if (isCurrent(token)) showFailure(failure.reason.toUiError())
            } catch (_: Throwable) {
                if (isCurrent(token)) showFailure(PlaceSearchError.UNKNOWN)
            }
        }
    }

    private fun selectSuggestion(id: String) {
        val state = _uiState.value
        val suggestion = state.suggestions.firstOrNull { it.id == id } ?: return
        if (!suggestion.insideSupportedRegion) {
            _uiState.update {
                it.copy(error = PlaceSearchError.OUTSIDE_COVERAGE)
            }
            return
        }

        explicitSearchJob?.cancel()
        lookupJob?.cancel()
        val token = requestGeneration.incrementAndGet()
        autocompleteRequests.value = AutocompleteRequest.disabled(token)
        val target = state.targetField
        _uiState.update {
            it.copy(
                status = PlaceSearchStatus.LOADING,
                error = null,
                selectedSuggestionId = id,
            )
        }
        lookupJob = viewModelScope.launch {
            try {
                val place = repository.lookup(id)
                if (!isCurrent(token)) return@launch
                if (!place.insideSupportedRegion) {
                    _uiState.update {
                        it.copy(
                            status = PlaceSearchStatus.RESULTS,
                            error = PlaceSearchError.OUTSIDE_COVERAGE,
                            selectedSuggestionId = null,
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isVisible = false,
                        selectedSuggestionId = null,
                    )
                }
                _effects.emit(PlaceSearchEffect.PlaceSelected(target, place))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: GeocodingRepositoryException) {
                if (isCurrent(token)) {
                    _uiState.update {
                        it.copy(
                            status = PlaceSearchStatus.ERROR,
                            error = failure.reason.toUiError(),
                            selectedSuggestionId = null,
                        )
                    }
                }
            } catch (_: Throwable) {
                if (isCurrent(token)) {
                    showFailure(PlaceSearchError.UNKNOWN)
                }
            }
        }
    }

    private fun finishWithCurrentLocation() {
        if (!_uiState.value.isVisible) return
        val target = _uiState.value.targetField
        dismiss()
        _effects.tryEmit(PlaceSearchEffect.UseCurrentLocation(target))
    }

    private fun finishWithMapSelection() {
        if (!_uiState.value.isVisible) return
        val target = _uiState.value.targetField
        dismiss()
        _effects.tryEmit(PlaceSearchEffect.ChooseOnMap(target))
    }

    private fun dismiss() {
        cancelForegroundRequests()
        val token = requestGeneration.incrementAndGet()
        autocompleteRequests.value = AutocompleteRequest.disabled(token)
        _uiState.update {
            it.copy(
                isVisible = false,
                selectedSuggestionId = null,
            )
        }
    }

    private fun autocompleteFlow(
        request: AutocompleteRequest,
    ): Flow<AutocompleteOutcome> {
        if (!request.enabled) return flowOf()
        return flow {
            emit(AutocompleteOutcome.Loading(request))
            val suggestions = repository.autocomplete(
                query = request.query,
                proximity = request.proximity,
                limit = AUTOCOMPLETE_RESULT_LIMIT,
            )
            emit(AutocompleteOutcome.Success(request, suggestions))
        }.catch { failure ->
            if (failure is CancellationException) throw failure
            val error = if (failure is GeocodingRepositoryException) {
                failure.reason.toUiError()
            } else {
                PlaceSearchError.UNKNOWN
            }
            emit(AutocompleteOutcome.Failure(request, error))
        }
    }

    private fun applyAutocompleteOutcome(outcome: AutocompleteOutcome) {
        if (!isCurrent(outcome.request.token)) return
        when (outcome) {
            is AutocompleteOutcome.Loading -> _uiState.update {
                it.copy(
                    status = PlaceSearchStatus.LOADING,
                    suggestions = emptyList(),
                    error = null,
                )
            }
            is AutocompleteOutcome.Success -> showSuggestions(
                outcome.suggestions,
            )
            is AutocompleteOutcome.Failure -> showFailure(outcome.error)
        }
    }

    private fun showSuggestions(suggestions: List<PlaceSuggestion>) {
        _uiState.update {
            it.copy(
                suggestions = suggestions,
                selectedSuggestionId = null,
                status = if (suggestions.isEmpty()) {
                    PlaceSearchStatus.EMPTY
                } else {
                    PlaceSearchStatus.RESULTS
                },
                error = null,
            )
        }
    }

    private fun showFailure(error: PlaceSearchError) {
        _uiState.update {
            it.copy(
                status = PlaceSearchStatus.ERROR,
                suggestions = emptyList(),
                selectedSuggestionId = null,
                error = error,
            )
        }
    }

    private fun isCurrent(token: Long): Boolean =
        token == requestGeneration.get() && _uiState.value.isVisible

    private fun cancelForegroundRequests() {
        explicitSearchJob?.cancel()
        explicitSearchJob = null
        lookupJob?.cancel()
        lookupJob = null
    }

    override fun onCleared() {
        cancelForegroundRequests()
        super.onCleared()
    }

    private data class AutocompleteRequest(
        val query: String,
        val proximity: GeoPoint?,
        val token: Long,
        val enabled: Boolean,
    ) {
        companion object {
            fun disabled(token: Long = 0) = AutocompleteRequest(
                query = "",
                proximity = null,
                token = token,
                enabled = false,
            )
        }
    }

    private sealed interface AutocompleteOutcome {
        val request: AutocompleteRequest

        data class Loading(
            override val request: AutocompleteRequest,
        ) : AutocompleteOutcome

        data class Success(
            override val request: AutocompleteRequest,
            val suggestions: List<PlaceSuggestion>,
        ) : AutocompleteOutcome

        data class Failure(
            override val request: AutocompleteRequest,
            val error: PlaceSearchError,
        ) : AutocompleteOutcome
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 400L
        const val MIN_AUTOCOMPLETE_QUERY_LENGTH = 3
        const val MIN_EXPLICIT_QUERY_LENGTH = 2
        const val MAX_QUERY_LENGTH = 120
        const val AUTOCOMPLETE_RESULT_LIMIT = 6
        const val EXPLICIT_RESULT_LIMIT = 8
    }
}

private fun GeocodingFailureReason.toUiError(): PlaceSearchError = when (this) {
    GeocodingFailureReason.INVALID_QUERY,
    GeocodingFailureReason.INVALID_COORDINATES,
    -> PlaceSearchError.INVALID_QUERY
    GeocodingFailureReason.OUTSIDE_SUPPORTED_REGION ->
        PlaceSearchError.OUTSIDE_COVERAGE
    GeocodingFailureReason.PLACE_NOT_FOUND -> PlaceSearchError.PLACE_NOT_FOUND
    GeocodingFailureReason.TIMEOUT -> PlaceSearchError.TIMEOUT
    GeocodingFailureReason.OFFLINE -> PlaceSearchError.OFFLINE
    GeocodingFailureReason.SERVER_UNAVAILABLE ->
        PlaceSearchError.SERVICE_UNAVAILABLE
    GeocodingFailureReason.INVALID_RESPONSE ->
        PlaceSearchError.INVALID_RESPONSE
    GeocodingFailureReason.UNKNOWN -> PlaceSearchError.UNKNOWN
}
