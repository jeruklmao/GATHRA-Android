package opsi.sman35jkt.gathra.data.navigation

import opsi.sman35jkt.gathra.core.model.RouteGeometry
import opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample

/**
 * Deterministic, clock-free navigation input for demo builds and tests.
 *
 * The caller controls emission cadence. Each [nextLocation] advances the
 * simulator's internal timestamp by [intervalMillis].
 */
class DemoLocationSimulator(
    geometry: RouteGeometry,
    private val intervalMillis: Long = 1_000L,
    private val baseSpeedMetersPerSecond: Double = 12.0,
    startElapsedRealtimeMillis: Long = 0L,
    startEpochTimeMillis: Long = 0L,
) {
    private val metrics = PolylineMetrics(geometry)
    private var distanceAlongRouteMeters = 0.0
    private var elapsedRealtimeMillis = startElapsedRealtimeMillis
    private var epochTimeMillis = startEpochTimeMillis
    private var paused = false
    private var speedMultiplier = 1.0

    init {
        require(intervalMillis > 0L)
        require(baseSpeedMetersPerSecond > 0.0 && baseSpeedMetersPerSecond.isFinite())
        require(startElapsedRealtimeMillis >= 0L)
        require(startEpochTimeMillis >= 0L)
    }

    val isPaused: Boolean
        get() = paused

    val isFinished: Boolean
        get() = distanceAlongRouteMeters >= metrics.totalDistanceMeters

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun setSpeedMultiplier(multiplier: Double) {
        require(multiplier.isFinite() && multiplier > 0.0)
        speedMultiplier = multiplier
    }

    fun reset(
        startElapsedRealtimeMillis: Long = 0L,
        startEpochTimeMillis: Long = 0L,
    ) {
        require(startElapsedRealtimeMillis >= 0L)
        require(startEpochTimeMillis >= 0L)
        distanceAlongRouteMeters = 0.0
        elapsedRealtimeMillis = startElapsedRealtimeMillis
        epochTimeMillis = startEpochTimeMillis
        paused = false
    }

    fun nextLocation(): NavigationLocationSample {
        val position = metrics.pointAtDistance(distanceAlongRouteMeters)
        val segmentStart = metrics.points[position.segmentIndex]
        val segmentEnd = metrics.points[position.segmentIndex + 1]
        val emitted = NavigationLocationSample(
            point = position.point,
            accuracyMeters = DEMO_ACCURACY_METERS,
            bearingDegrees = GeoMath.bearingDegrees(segmentStart, segmentEnd),
            speedMetersPerSecond = if (paused || isFinished) {
                0.0
            } else {
                baseSpeedMetersPerSecond * speedMultiplier
            },
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            epochTimeMillis = epochTimeMillis,
        )
        elapsedRealtimeMillis += intervalMillis
        epochTimeMillis += intervalMillis
        if (!paused) {
            distanceAlongRouteMeters = (
                distanceAlongRouteMeters +
                    baseSpeedMetersPerSecond * speedMultiplier * intervalMillis / 1_000.0
                ).coerceAtMost(metrics.totalDistanceMeters)
        }
        return emitted
    }

    private companion object {
        const val DEMO_ACCURACY_METERS = 5.0
    }
}
