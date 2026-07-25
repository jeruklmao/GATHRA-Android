package opsi.sman35jkt.gathra.feature.map

sealed interface MapRouteEffect {
    data object RequestForegroundLocationPermission : MapRouteEffect

    data object OpenApplicationSettings : MapRouteEffect

    data class ShowMessage(val message: MapRouteMessage) : MapRouteEffect
}

/**
 * Semantic messages are mapped to localized string resources by the UI layer.
 */
enum class MapRouteMessage {
    NAVIGATION_COMING_LATER,
    LOCATION_DISABLED,
    LOCATION_UNAVAILABLE,
}
