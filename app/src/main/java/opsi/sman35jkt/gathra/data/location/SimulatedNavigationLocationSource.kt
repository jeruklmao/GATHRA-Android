package opsi.sman35jkt.gathra.data.location

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import opsi.sman35jkt.gathra.core.location.NavigationLocationEvent
import opsi.sman35jkt.gathra.core.location.NavigationLocationSource
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.data.navigation.DemoLocationSimulator

class SimulatedNavigationLocationSource(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
) : NavigationLocationSource {
    private val paused = MutableStateFlow(false)
    private val speedMultiplier = MutableStateFlow(DEFAULT_SPEED_MULTIPLIER)
    private val offRouteSamplesRemaining = AtomicInteger(0)

    init {
        require(intervalMillis > 0L)
    }

    override fun updates(route: RouteOption): Flow<NavigationLocationEvent> = flow {
        val simulator = DemoLocationSimulator(
            geometry = route.geometry,
            intervalMillis = intervalMillis,
            startElapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime(),
            startEpochTimeMillis = System.currentTimeMillis(),
        )
        while (true) {
            simulator.setSpeedMultiplier(speedMultiplier.value)
            if (paused.value) simulator.pause() else simulator.resume()
            val sample = simulator.nextLocation()
            val remaining = offRouteSamplesRemaining.get()
            val emittedSample = if (
                remaining > 0 &&
                offRouteSamplesRemaining.compareAndSet(remaining, remaining - 1)
            ) {
                sample.copy(
                    point = GeoPoint(
                        latitude = sample.point.latitude + OFF_ROUTE_OFFSET_DEGREES,
                        longitude = sample.point.longitude,
                    ),
                )
            } else {
                sample
            }
            emit(NavigationLocationEvent.Location(emittedSample))
            delay(intervalMillis)
        }
    }

    override fun setSimulationPaused(paused: Boolean) {
        this.paused.value = paused
    }

    override fun setSimulationSpeed(multiplier: Double) {
        require(multiplier in SUPPORTED_SPEEDS)
        speedMultiplier.value = multiplier
    }

    override fun simulateOffRoute() {
        offRouteSamplesRemaining.set(OFF_ROUTE_SAMPLE_COUNT)
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 1_000L
        const val DEFAULT_SPEED_MULTIPLIER = 2.0
        val SUPPORTED_SPEEDS = setOf(1.0, 2.0, 4.0)
        private const val OFF_ROUTE_OFFSET_DEGREES = 0.001
        private const val OFF_ROUTE_SAMPLE_COUNT = 3
    }
}
