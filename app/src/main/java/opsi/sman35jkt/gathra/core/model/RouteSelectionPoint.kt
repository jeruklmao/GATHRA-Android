package opsi.sman35jkt.gathra.core.model

enum class SelectionPointSource {
    CURRENT_LOCATION,
    MAP_SELECTION,
    DEMO_FALLBACK,
}

data class RouteSelectionPoint(
    val point: GeoPoint,
    val source: SelectionPointSource,
)
