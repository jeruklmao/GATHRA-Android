package opsi.sman35jkt.gathra.data.navigation

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample
import opsi.sman35jkt.gathra.domain.navigation.NavigationProgress
import opsi.sman35jkt.gathra.domain.navigation.NavigationRepository
import opsi.sman35jkt.gathra.domain.navigation.NavigationSession
import opsi.sman35jkt.gathra.domain.navigation.NavigationStateMachine
import opsi.sman35jkt.gathra.domain.navigation.NavigationStatus

class NavigationSessionRepository : NavigationRepository {
    private val _session = MutableStateFlow<NavigationSession?>(null)
    override val session: StateFlow<NavigationSession?> = _session.asStateFlow()

    override fun prepare(
        route: RouteOption,
        destination: GeoPoint,
        travelMode: TravelMode,
    ): NavigationSession {
        require(route.steps.isNotEmpty()) {
            "Navigation cannot start without route steps."
        }
        val prepared = NavigationSession(
            id = UUID.randomUUID().toString(),
            route = route,
            destination = destination,
            travelMode = travelMode,
            status = NavigationStatus.PREPARING,
            startedAtMillis = System.currentTimeMillis(),
        )
        _session.value = prepared
        return prepared
    }

    override fun setMuted(muted: Boolean) {
        _session.update { current -> current?.copy(muted = muted) }
    }

    fun markVoiceUnavailable() {
        _session.update { current -> current?.copy(voiceUnavailable = true) }
    }

    override fun finish() {
        transitionTo(NavigationStatus.STOPPED)
    }

    fun updateProgress(
        rawLocation: NavigationLocationSample,
        progress: NavigationProgress,
    ) {
        _session.update { current ->
            current?.let {
                val desiredStatus = when {
                    progress.isArrived -> NavigationStatus.ARRIVED
                    current.status == NavigationStatus.RECALCULATING ->
                        NavigationStatus.RECALCULATING
                    current.rerouteError != null && progress.isOffRoute ->
                        NavigationStatus.ERROR
                    progress.isOffRoute -> NavigationStatus.OFF_ROUTE
                    else -> NavigationStatus.NAVIGATING
                }
                val nextStatus = desiredStatus.takeIf {
                    NavigationStateMachine.canTransition(current.status, it)
                } ?: current.status
                val updated = current.copy(
                    rawLocation = rawLocation,
                    progress = progress,
                    rerouteError = current.rerouteError.takeIf {
                        nextStatus == NavigationStatus.ERROR
                    },
                )
                NavigationStateMachine.transition(updated, nextStatus)
            }
        }
    }

    fun updateRawLocation(location: NavigationLocationSample) {
        _session.update { current -> current?.copy(rawLocation = location) }
    }

    fun replaceRoute(
        route: RouteOption,
        rawLocation: NavigationLocationSample?,
        progress: NavigationProgress?,
    ) {
        _session.update { current ->
            current
                ?.takeIf {
                    NavigationStateMachine.canTransition(
                        it.status,
                        NavigationStatus.NAVIGATING,
                    )
                }
                ?.let { active ->
                    NavigationStateMachine.transition(
                        active.copy(
                            route = route,
                            rawLocation = rawLocation ?: active.rawLocation,
                            progress = progress,
                            rerouteError = null,
                        ),
                        NavigationStatus.NAVIGATING,
                    )
                }
                ?: current
        }
    }

    fun markRerouteFailed() {
        _session.update { current ->
            current?.let {
                if (
                    NavigationStateMachine.canTransition(
                        current.status,
                        NavigationStatus.ERROR,
                    )
                ) {
                    NavigationStateMachine.transition(
                        current.copy(rerouteError = REROUTE_FAILED),
                        NavigationStatus.ERROR,
                    )
                } else {
                    current
                }
            }
        }
    }

    fun transitionTo(status: NavigationStatus) {
        _session.update { current ->
            current?.let {
                if (NavigationStateMachine.canTransition(it.status, status)) {
                    NavigationStateMachine.transition(it, status)
                } else {
                    it
                }
            }
        }
    }

    private companion object {
        const val REROUTE_FAILED = "reroute_failed"
    }
}
