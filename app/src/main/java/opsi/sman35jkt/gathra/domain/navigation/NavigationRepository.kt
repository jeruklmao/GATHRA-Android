package opsi.sman35jkt.gathra.domain.navigation

import kotlinx.coroutines.flow.StateFlow
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.TravelMode

interface NavigationRepository {
    val session: StateFlow<NavigationSession?>

    fun prepare(
        route: RouteOption,
        destination: GeoPoint,
        travelMode: TravelMode,
    ): NavigationSession

    fun setMuted(muted: Boolean)

    fun finish()
}
