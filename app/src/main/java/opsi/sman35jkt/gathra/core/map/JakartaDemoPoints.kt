package opsi.sman35jkt.gathra.core.map

import opsi.sman35jkt.gathra.core.model.GeoPoint

/**
 * Centralized fallback coordinates used when foreground location is unavailable.
 */
object JakartaDemoPoints {
    val origin = GeoPoint(
        latitude = -6.2000,
        longitude = 106.8167,
    )

    val suggestedDestination = GeoPoint(
        latitude = -6.1754,
        longitude = 106.8272,
    )
}
