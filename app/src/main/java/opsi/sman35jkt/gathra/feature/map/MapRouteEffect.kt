package opsi.sman35jkt.gathra.feature.map

import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.TravelMode

sealed interface MapRouteEffect {
    data object RequestForegroundLocationPermission : MapRouteEffect

    data object OpenApplicationSettings : MapRouteEffect

    data class ShowMessage(val message: MapRouteMessage) : MapRouteEffect

    data class OpenPlaceSearch(
        val mode: PointSelectionMode,
        val proximity: GeoPoint,
    ) : MapRouteEffect

    data class StartNavigation(
        val route: RouteOption,
        val destination: GeoPoint,
        val travelMode: TravelMode,
    ) : MapRouteEffect
}

/**
 * Semantic messages are mapped to localized string resources by the UI layer.
 */
enum class MapRouteMessage {
    LOCATION_DISABLED,
    LOCATION_UNAVAILABLE,
    NAVIGATION_ROUTE_UNAVAILABLE,
    FLOOD_ROUTE_OUTDATED,
}
