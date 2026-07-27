package opsi.sman35jkt.gathra.feature.geocoding

import opsi.sman35jkt.gathra.core.model.SelectedPlace

sealed interface PlaceSearchEffect {
    data class PlaceSelected(
        val targetField: SearchTargetField,
        val place: SelectedPlace,
    ) : PlaceSearchEffect

    data class UseCurrentLocation(
        val targetField: SearchTargetField,
    ) : PlaceSearchEffect

    data class ChooseOnMap(
        val targetField: SearchTargetField,
    ) : PlaceSearchEffect
}
