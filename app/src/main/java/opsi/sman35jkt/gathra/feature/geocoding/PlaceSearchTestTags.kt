package opsi.sman35jkt.gathra.feature.geocoding

object PlaceSearchTestTags {
    const val Surface = "place_search_surface"
    const val Field = "place_search_field"
    const val CurrentLocation = "place_search_current_location"
    const val ChooseOnMap = "place_search_choose_on_map"
    const val Suggestions = "place_search_suggestions"
    const val Loading = "place_search_loading"
    const val Empty = "place_search_empty"
    const val Error = "place_search_error"
    const val Retry = "place_search_retry"

    fun suggestion(id: String): String = "place_suggestion_$id"
}
