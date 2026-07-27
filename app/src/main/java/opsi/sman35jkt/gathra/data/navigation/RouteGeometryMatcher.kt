package opsi.sman35jkt.gathra.data.navigation

import opsi.sman35jkt.gathra.core.model.RouteGeometry
import opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample
import opsi.sman35jkt.gathra.domain.navigation.RouteMatch
import kotlin.math.max

data class RouteGeometryMatcherConfig(
    val allowedBackwardMeters: Double = 20.0,
    val forwardContinuityBaseMeters: Double = 120.0,
    val maximumPlausibleSpeedMetersPerSecond: Double = 60.0,
    val continuityPenaltyMultiplier: Double = 4.0,
    val bearingPenaltyMeters: Double = 30.0,
    val minimumBearingSpeedMetersPerSecond: Double = 2.0,
)

/**
 * Matches a location to the nearest point on route line segments.
 *
 * Candidate scoring uses prior progress to disambiguate loops and movement
 * bearing when it is reliable. A noisy sample cannot cause a large backwards
 * progress jump.
 */
class RouteGeometryMatcher(
    geometry: RouteGeometry,
    private val config: RouteGeometryMatcherConfig = RouteGeometryMatcherConfig(),
) {
    private val metrics = PolylineMetrics(geometry)

    val routeLengthMeters: Double
        get() = metrics.totalDistanceMeters

    fun match(
        location: NavigationLocationSample,
        previousMatch: RouteMatch? = null,
    ): RouteMatch {
        val elapsedSeconds = previousMatch
            ?.let {
                (location.elapsedRealtimeMillis - it.elapsedRealtimeMillis)
                    .coerceAtLeast(0L) / 1_000.0
            }
            ?: 0.0
        val plausibleForwardProgress = config.forwardContinuityBaseMeters +
            elapsedSeconds * config.maximumPlausibleSpeedMetersPerSecond +
            location.accuracyMeters

        val candidate = metrics.segmentLengths.indices
            .map { segmentIndex ->
                val projection = GeoMath.projectOnSegment(
                    point = location.point,
                    start = metrics.points[segmentIndex],
                    end = metrics.points[segmentIndex + 1],
                )
                val distanceAlong = metrics.cumulativeDistances[segmentIndex] +
                    metrics.segmentLengths[segmentIndex] * projection.fraction
                val continuityPenalty = previousMatch?.let { previous ->
                    when {
                        distanceAlong < previous.distanceAlongRouteMeters -
                            config.allowedBackwardMeters -> {
                            (previous.distanceAlongRouteMeters -
                                config.allowedBackwardMeters - distanceAlong) *
                                config.continuityPenaltyMultiplier
                        }

                        distanceAlong > previous.distanceAlongRouteMeters +
                            plausibleForwardProgress -> {
                            (distanceAlong - previous.distanceAlongRouteMeters -
                                plausibleForwardProgress) *
                                config.continuityPenaltyMultiplier
                        }

                        else -> 0.0
                    }
                } ?: 0.0
                val bearingPenalty = if (
                    location.bearingDegrees != null &&
                    (location.speedMetersPerSecond ?: 0.0) >=
                    config.minimumBearingSpeedMetersPerSecond
                ) {
                    val segmentBearing = GeoMath.bearingDegrees(
                        metrics.points[segmentIndex],
                        metrics.points[segmentIndex + 1],
                    )
                    GeoMath.angularDifferenceDegrees(
                        location.bearingDegrees,
                        segmentBearing,
                    ) / 180.0 * config.bearingPenaltyMeters
                } else {
                    0.0
                }
                Candidate(
                    projection = projection,
                    segmentIndex = segmentIndex,
                    distanceAlongRouteMeters = distanceAlong,
                    score = projection.distanceMeters + continuityPenalty + bearingPenalty,
                )
            }
            .minBy(Candidate::score)

        val minimumAllowedProgress = previousMatch
            ?.distanceAlongRouteMeters
            ?.minus(config.allowedBackwardMeters)
            ?.coerceAtLeast(0.0)
            ?: 0.0
        val adjustedProgress = max(candidate.distanceAlongRouteMeters, minimumAllowedProgress)
        val adjustedPoint = if (adjustedProgress == candidate.distanceAlongRouteMeters) {
            PointAlongPolyline(
                point = candidate.projection.point,
                segmentIndex = candidate.segmentIndex,
                segmentFraction = candidate.projection.fraction,
            )
        } else {
            metrics.pointAtDistance(adjustedProgress)
        }

        return RouteMatch(
            matchedLocation = adjustedPoint.point,
            segmentIndex = adjustedPoint.segmentIndex,
            segmentFraction = adjustedPoint.segmentFraction,
            distanceFromRouteMeters = GeoMath.distanceMeters(
                location.point,
                adjustedPoint.point,
            ),
            distanceAlongRouteMeters = adjustedProgress,
            elapsedRealtimeMillis = location.elapsedRealtimeMillis,
        )
    }

    private data class Candidate(
        val projection: SegmentProjection,
        val segmentIndex: Int,
        val distanceAlongRouteMeters: Double,
        val score: Double,
    )
}
