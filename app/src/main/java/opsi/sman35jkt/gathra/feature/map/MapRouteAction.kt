package opsi.sman35jkt.gathra.feature.map

import opsi.sman35jkt.gathra.core.model.GeoBounds
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.SelectedPlace
import opsi.sman35jkt.gathra.core.model.TravelMode

sealed interface MapRouteAction {
    data class StartPointSelection(val mode: PointSelectionMode) : MapRouteAction

    data class SearchRequested(val mode: PointSelectionMode) : MapRouteAction

    data class PlaceSelected(
        val mode: PointSelectionMode,
        val place: SelectedPlace,
    ) : MapRouteAction

    data class UseCurrentLocation(
        val mode: PointSelectionMode,
    ) : MapRouteAction

    data class MapPointTapped(val point: GeoPoint) : MapRouteAction

    data object ConfirmPointSelection : MapRouteAction

    data object CancelPointSelection : MapRouteAction

    data object SwapPoints : MapRouteAction

    data class TravelModeSelected(val mode: TravelMode) : MapRouteAction

    data class RouteSelected(val routeId: String) : MapRouteAction

    data object RetryRoute : MapRouteAction

    data class BottomSheetChanged(val state: RouteBottomSheetState) : MapRouteAction

    data object CurrentLocationClicked : MapRouteAction

    data object PermissionRationaleAccepted : MapRouteAction

    data object PermissionRationaleDismissed : MapRouteAction

    /**
     * Sent after the Activity Result permission request, or once on entry when permissions are
     * already granted. [permanentlyDenied] should only be true after a request has been made and
     * Android no longer recommends showing the platform rationale.
     */
    data class LocationPermissionResult(
        val preciseGranted: Boolean,
        val approximateGranted: Boolean,
        val permanentlyDenied: Boolean = false,
    ) : MapRouteAction

    data object ErrorDismissed : MapRouteAction

    data object PreviewClicked : MapRouteAction

    data object RefreshFloodHazards : MapRouteAction

    data object ToggleFloodLayer : MapRouteAction

    data class FloodHazardSelected(val hazardId: String) : MapRouteAction

    data object DismissFloodHazardDetails : MapRouteAction

    data class MapViewportSettled(val bounds: GeoBounds) : MapRouteAction
}
