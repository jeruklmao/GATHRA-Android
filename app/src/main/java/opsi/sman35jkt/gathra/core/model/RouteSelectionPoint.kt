package opsi.sman35jkt.gathra.core.model

enum class SelectionPointSource {
    CURRENT_LOCATION,
    MAP_SELECTION,
    GEOCODING_SEARCH,
    DEMO_FALLBACK,
}

data class RouteSelectionPoint(
    val point: GeoPoint,
    val source: SelectionPointSource,
    val displayName: String? = null,
    val formattedAddress: String? = null,
)
