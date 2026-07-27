package opsi.sman35jkt.gathra.data.navigation

import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteGeometry
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(first: GeoPoint, second: GeoPoint): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val haversine = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
        return 2.0 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    fun bearingDegrees(first: GeoPoint, second: GeoPoint): Double {
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val y = sin(longitudeDelta) * cos(secondLatitude)
        val x = cos(firstLatitude) * sin(secondLatitude) -
            sin(firstLatitude) * cos(secondLatitude) * cos(longitudeDelta)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun angularDifferenceDegrees(first: Double, second: Double): Double {
        val raw = kotlin.math.abs(first - second) % 360.0
        return minOf(raw, 360.0 - raw)
    }

    fun interpolate(first: GeoPoint, second: GeoPoint, fraction: Double): GeoPoint {
        val safeFraction = fraction.coerceIn(0.0, 1.0)
        return GeoPoint(
            latitude = first.latitude + (second.latitude - first.latitude) * safeFraction,
            longitude = first.longitude + (second.longitude - first.longitude) * safeFraction,
        )
    }

    /**
     * Projects [point] on a short line segment using a local equirectangular
     * plane. Route legs are small enough that this is stable while avoiding
     * framework or map-SDK types.
     */
    fun projectOnSegment(
        point: GeoPoint,
        start: GeoPoint,
        end: GeoPoint,
    ): SegmentProjection {
        val latitudeRadians = Math.toRadians(point.latitude)
        val metersPerLatitudeDegree = EARTH_RADIUS_METERS * PI / 180.0
        val metersPerLongitudeDegree = metersPerLatitudeDegree * cos(latitudeRadians)

        val startX = (start.longitude - point.longitude) * metersPerLongitudeDegree
        val startY = (start.latitude - point.latitude) * metersPerLatitudeDegree
        val endX = (end.longitude - point.longitude) * metersPerLongitudeDegree
        val endY = (end.latitude - point.latitude) * metersPerLatitudeDegree
        val deltaX = endX - startX
        val deltaY = endY - startY
        val squaredLength = deltaX * deltaX + deltaY * deltaY
        val fraction = if (squaredLength <= 1e-9) {
            0.0
        } else {
            (-(startX * deltaX + startY * deltaY) / squaredLength).coerceIn(0.0, 1.0)
        }
        val projectedX = startX + deltaX * fraction
        val projectedY = startY + deltaY * fraction
        return SegmentProjection(
            point = interpolate(start, end, fraction),
            fraction = fraction,
            distanceMeters = sqrt(projectedX * projectedX + projectedY * projectedY),
        )
    }
}

internal data class SegmentProjection(
    val point: GeoPoint,
    val fraction: Double,
    val distanceMeters: Double,
)

internal class PolylineMetrics(
    geometry: RouteGeometry,
) {
    val points: List<GeoPoint> = geometry.points
    val segmentLengths: List<Double> = points.zipWithNext(GeoMath::distanceMeters)
    val cumulativeDistances: List<Double> = buildList {
        add(0.0)
        segmentLengths.forEach { add(last() + it) }
    }
    val totalDistanceMeters: Double = cumulativeDistances.last()

    init {
        require(totalDistanceMeters > 0.0) {
            "Navigation geometry must have a positive length."
        }
    }

    fun pointAtDistance(distanceMeters: Double): PointAlongPolyline {
        val safeDistance = distanceMeters.coerceIn(0.0, totalDistanceMeters)
        val segmentIndex = segmentLengths.indices.firstOrNull { index ->
            safeDistance <= cumulativeDistances[index + 1]
        } ?: segmentLengths.lastIndex
        val segmentLength = segmentLengths[segmentIndex]
        val fraction = if (segmentLength <= 1e-9) {
            0.0
        } else {
            (safeDistance - cumulativeDistances[segmentIndex]) / segmentLength
        }.coerceIn(0.0, 1.0)
        return PointAlongPolyline(
            point = GeoMath.interpolate(points[segmentIndex], points[segmentIndex + 1], fraction),
            segmentIndex = segmentIndex,
            segmentFraction = fraction,
        )
    }
}

internal data class PointAlongPolyline(
    val point: GeoPoint,
    val segmentIndex: Int,
    val segmentFraction: Double,
)

