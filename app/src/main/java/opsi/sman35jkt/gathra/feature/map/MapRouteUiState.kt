package opsi.sman35jkt.gathra.feature.map

import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteSelectionPoint
import opsi.sman35jkt.gathra.core.model.TravelMode

/**
 * Immutable state rendered by the route-preview screen.
 *
 * Permission and location lookup state are intentionally independent from route loading state so
 * that the Jakarta fallback remains usable when foreground location cannot be obtained.
 */
data class MapRouteUiState(
    val origin: RouteSelectionPoint? = null,
    val destination: RouteSelectionPoint? = null,
    val pointSelectionMode: PointSelectionMode? = null,
    val pendingPoint: GeoPoint? = null,
    val selectedTravelMode: TravelMode = TravelMode.CAR,
    val routes: List<RouteOption> = emptyList(),
    val selectedRouteId: String? = null,
    val routeContentState: RouteContentState = RouteContentState.EMPTY,
    val locationPermissionState: LocationPermissionState = LocationPermissionState.NOT_REQUESTED,
    val isPermissionRationaleVisible: Boolean = false,
    val isNavigationPermissionRequest: Boolean = false,
    val hasShownNavigationDisclosure: Boolean = false,
    val isLocating: Boolean = false,
    val locationSelectionTarget: PointSelectionMode? = null,
    val currentLocationPoint: GeoPoint? = null,
    val bottomSheetState: RouteBottomSheetState = RouteBottomSheetState.COLLAPSED,
    val error: MapRouteError? = null,
    val floodHazardSnapshot: FloodHazardSnapshot? = null,
    val selectedFloodHazardId: String? = null,
    val isFloodLayerVisible: Boolean = true,
    val isLoadingFloodHazards: Boolean = false,
    val floodDataStatus: FloodDataStatus = FloodDataStatus.UNAVAILABLE,
    val floodRouteSyncState: FloodRouteSyncState = FloodRouteSyncState.NOT_EVALUATED,
    val floodRouteTargetSnapshotId: String? = null,
) {
    val selectedRoute: RouteOption?
        get() = routes.firstOrNull { it.id == selectedRouteId }

    val alternativeRoutes: List<RouteOption>
        get() = routes.filterNot { it.id == selectedRouteId }

    val isLoading: Boolean
        get() = routeContentState == RouteContentState.LOADING

    val canConfirmPointSelection: Boolean
        get() = pointSelectionMode != null && pendingPoint != null

    val selectedFloodHazard: FloodHazardPolygon?
        get() = floodHazardSnapshot?.hazards?.firstOrNull { it.id == selectedFloodHazardId }

    val isFloodSnapshotOutOfSync: Boolean
        get() = floodRouteSyncState in setOf(
            FloodRouteSyncState.OUTDATED_BY_FLOOD_UPDATE,
            FloodRouteSyncState.UPDATING,
            FloodRouteSyncState.STALE,
        )

    val isSelectedRouteRiskCurrent: Boolean
        get() = floodDataStatus == FloodDataStatus.FRESH &&
            floodRouteSyncState == FloodRouteSyncState.SYNCHRONIZED

    val canUseSelectedRoute: Boolean
        get() = floodRouteSyncState !in setOf(
            FloodRouteSyncState.OUTDATED_BY_FLOOD_UPDATE,
            FloodRouteSyncState.UPDATING,
            FloodRouteSyncState.STALE,
        )
}

enum class PointSelectionMode {
    ORIGIN,
    DESTINATION,
}

enum class RouteContentState {
    EMPTY,
    LOADING,
    READY,
    ERROR,
}

enum class LocationPermissionState {
    NOT_REQUESTED,
    PRECISE,
    APPROXIMATE,
    DENIED,
    PERMANENTLY_DENIED,
}

enum class RouteBottomSheetState {
    COLLAPSED,
    EXPANDED,
}

enum class MapRouteError {
    ROUTE_CALCULATION_FAILED,
    ROUTE_OFFLINE,
    ROUTE_TIMEOUT,
    ROUTE_NOT_FOUND,
    NO_ROUTE_DUE_TO_FLOOD,
    ORIGIN_IN_BLOCKED_AREA,
    DESTINATION_IN_BLOCKED_AREA,
    ROUTE_INVALID_RESPONSE,
    ROUTE_SERVICE_UNAVAILABLE,
}

enum class FloodDataStatus {
    UNAVAILABLE,
    FRESH,
    STALE,
}

enum class FloodRouteSyncState {
    NOT_EVALUATED,
    SYNCHRONIZED,
    OUTDATED_BY_FLOOD_UPDATE,
    UPDATING,
    STALE,
}
