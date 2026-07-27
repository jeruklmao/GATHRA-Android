package opsi.sman35jkt.gathra.feature.geocoding

import opsi.sman35jkt.gathra.core.model.GeoPoint

sealed interface PlaceSearchAction {
    data class Open(
        val targetField: SearchTargetField,
        val proximity: GeoPoint?,
    ) : PlaceSearchAction

    data class QueryChanged(val query: String) : PlaceSearchAction

    data object Submit : PlaceSearchAction

    data class SuggestionSelected(val id: String) : PlaceSearchAction

    data object Retry : PlaceSearchAction

    data object CurrentLocationSelected : PlaceSearchAction

    data object ChooseOnMap : PlaceSearchAction

    data object Dismiss : PlaceSearchAction
}
