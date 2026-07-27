package opsi.sman35jkt.gathra.domain.navigation

import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.TravelMode

/**
 * Immutable snapshot of one navigation session.
 *
 * Raw locations are intentionally not accumulated: only the latest accepted
 * reading and derived progress are retained.
 */
data class NavigationSession(
    val id: String,
    val route: RouteOption,
    val destination: GeoPoint = route.geometry.points.last(),
    val travelMode: TravelMode,
    val status: NavigationStatus = NavigationStatus.IDLE,
    val progress: NavigationProgress? = null,
    val rawLocation: NavigationLocationSample? = null,
    val muted: Boolean = false,
    val voiceUnavailable: Boolean = false,
    val rerouteError: String? = null,
    val startedAtMillis: Long? = null,
) {
    init {
        require(id.isNotBlank()) {
            "A navigation session must have a stable, non-blank ID."
        }
        require(route.steps.isNotEmpty()) {
            "An active navigation route must contain navigation steps."
        }
    }
}
