package opsi.sman35jkt.gathra.feature.geocoding

import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.PlaceSuggestion

data class PlaceSearchUiState(
    val isVisible: Boolean = false,
    val query: String = "",
    val targetField: SearchTargetField = SearchTargetField.DESTINATION,
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val selectedSuggestionId: String? = null,
    val status: PlaceSearchStatus = PlaceSearchStatus.IDLE,
    val error: PlaceSearchError? = null,
    val proximity: GeoPoint? = null,
    val isOutsideCoverageConfirmationVisible: Boolean = false,
)

enum class SearchTargetField {
    ORIGIN,
    DESTINATION,
}

enum class PlaceSearchStatus {
    IDLE,
    TYPING,
    LOADING,
    RESULTS,
    EMPTY,
    ERROR,
}

enum class PlaceSearchError {
    INVALID_QUERY,
    OFFLINE,
    TIMEOUT,
    SERVICE_UNAVAILABLE,
    INVALID_RESPONSE,
    PLACE_NOT_FOUND,
    OUTSIDE_COVERAGE,
    UNKNOWN,
}
