package opsi.sman35jkt.gathra.data.navigation

import opsi.sman35jkt.gathra.core.model.ManeuverType
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteStep
import opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample
import opsi.sman35jkt.gathra.domain.navigation.NavigationProgress
import opsi.sman35jkt.gathra.domain.navigation.RouteMatch
import kotlin.math.roundToLong

data class ArrivalDetectorConfig(
    val radiusMeters: Double = 30.0,
    val maximumAccuracyMeters: Double = 40.0,
    val requiredConsecutiveSamples: Int = 3,
)

class NavigationProgressCalculator(
    private val route: RouteOption,
    matcherConfig: RouteGeometryMatcherConfig = RouteGeometryMatcherConfig(),
    private val deviationDetector: DeviationDetector = DeviationDetector(),
    private val arrivalConfig: ArrivalDetectorConfig = ArrivalDetectorConfig(),
) {
    private val matcher = RouteGeometryMatcher(route.geometry, matcherConfig)
    private val metrics = PolylineMetrics(route.geometry)
    private var previousMatch: RouteMatch? = null
    private var consecutiveArrivalSamples = 0

    init {
        require(route.steps.isNotEmpty()) {
            "Navigation progress requires route steps."
        }
        require(arrivalConfig.radiusMeters > 0.0)
        require(arrivalConfig.maximumAccuracyMeters > 0.0)
        require(arrivalConfig.requiredConsecutiveSamples > 0)
    }

    fun calculate(
        location: NavigationLocationSample,
        rerouteInProgress: Boolean = false,
    ): NavigationProgress {
        val match = matcher.match(location, previousMatch)
        previousMatch = match

        val geometryFraction = (
            match.distanceAlongRouteMeters / matcher.routeLengthMeters
            ).coerceIn(0.0, 1.0)
        val travelledDistance = route.summary.distanceMeters * geometryFraction
        val remainingDistance = (
            route.summary.distanceMeters - travelledDistance
            ).coerceAtLeast(0.0)
        val currentStep = currentStep(match.distanceAlongRouteMeters)
        val distanceScale = route.summary.distanceMeters / matcher.routeLengthMeters
        val distanceToNextManoeuvre = (
            metrics.cumulativeDistances[currentStep.geometryEndIndex] -
                match.distanceAlongRouteMeters
            ).coerceAtLeast(0.0) * distanceScale
        val remainingDuration = calculateRemainingDuration(
            currentStep = currentStep,
            distanceAlongGeometryMeters = match.distanceAlongRouteMeters,
            fallbackFraction = 1.0 - geometryFraction,
        )

        val distanceToDestination = GeoMath.distanceMeters(location.point, route.geometry.points.last())
        if (
            distanceToDestination <= arrivalConfig.radiusMeters &&
            location.accuracyMeters <= arrivalConfig.maximumAccuracyMeters
        ) {
            consecutiveArrivalSamples++
        } else {
            consecutiveArrivalSamples = 0
        }
        val arrived = consecutiveArrivalSamples >= arrivalConfig.requiredConsecutiveSamples
        val deviation = if (arrived) {
            deviationDetector.reset()
            DeviationDecision(
                thresholdMeters = deviationDetector.thresholdMeters(location.accuracyMeters),
                consecutiveOffRouteSamples = 0,
                isOffRoute = false,
                shouldReroute = false,
            )
        } else {
            deviationDetector.evaluate(
                distanceFromRouteMeters = match.distanceFromRouteMeters,
                accuracyMeters = location.accuracyMeters,
                timestampMillis = location.elapsedRealtimeMillis,
                rerouteInProgress = rerouteInProgress,
            )
        }

        return NavigationProgress(
            matchedLocation = match.matchedLocation,
            distanceFromRouteMeters = match.distanceFromRouteMeters,
            travelledDistanceMeters = travelledDistance,
            remainingDistanceMeters = if (arrived) 0.0 else remainingDistance,
            remainingDurationSeconds = if (arrived) 0L else remainingDuration,
            currentStepIndex = if (arrived) route.steps.lastIndex else currentStep.index,
            distanceToNextManoeuvreMeters = if (arrived) 0.0 else distanceToNextManoeuvre,
            matchedSegmentIndex = match.segmentIndex,
            isOffRoute = deviation.isOffRoute,
            shouldReroute = deviation.shouldReroute,
            isArrived = arrived,
        )
    }

    fun reset() {
        previousMatch = null
        consecutiveArrivalSamples = 0
        deviationDetector.reset(clearCooldown = true)
    }

    private fun currentStep(distanceAlongGeometryMeters: Double): RouteStep =
        route.steps.firstOrNull { step ->
            step.maneuver.type != ManeuverType.ARRIVE &&
                distanceAlongGeometryMeters <
                metrics.cumulativeDistances[step.geometryEndIndex] - STEP_BOUNDARY_EPSILON_METERS
        } ?: route.steps.last()

    private fun calculateRemainingDuration(
        currentStep: RouteStep,
        distanceAlongGeometryMeters: Double,
        fallbackFraction: Double,
    ): Long {
        val currentStart = metrics.cumulativeDistances[currentStep.geometryStartIndex]
        val currentEnd = metrics.cumulativeDistances[currentStep.geometryEndIndex]
        val currentLength = currentEnd - currentStart
        val currentRemainingFraction = if (currentLength > STEP_BOUNDARY_EPSILON_METERS) {
            ((currentEnd - distanceAlongGeometryMeters) / currentLength).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val stepBasedDuration = currentStep.durationSeconds * currentRemainingFraction +
            route.steps
                .drop(currentStep.index + 1)
                .sumOf(RouteStep::durationSeconds)
        return if (stepBasedDuration > 0.0) {
            stepBasedDuration.roundToLong()
        } else {
            (route.summary.durationSeconds * fallbackFraction)
                .roundToLong()
                .coerceAtLeast(0L)
        }
    }

    private companion object {
        const val STEP_BOUNDARY_EPSILON_METERS = 0.5
    }
}
