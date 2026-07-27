package opsi.sman35jkt.gathra.core.location

import kotlinx.coroutines.flow.Flow
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample

interface NavigationLocationSource {
    fun updates(route: RouteOption): Flow<NavigationLocationEvent>

    fun setSimulationPaused(paused: Boolean) = Unit

    fun setSimulationSpeed(multiplier: Double) = Unit

    fun simulateOffRoute() = Unit
}

sealed interface NavigationLocationEvent {
    data class Location(
        val sample: NavigationLocationSample,
    ) : NavigationLocationEvent

    data object TemporarilyUnavailable : NavigationLocationEvent

    data object LocationDisabled : NavigationLocationEvent

    data object PermissionDenied : NavigationLocationEvent
}
