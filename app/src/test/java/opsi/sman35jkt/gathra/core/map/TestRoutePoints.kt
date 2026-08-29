package opsi.sman35jkt.gathra.core.map

import opsi.sman35jkt.gathra.core.model.GeoPoint

/** Deterministic route coordinates used only by Android unit tests. */
object TestRoutePoints {
    val origin = GeoPoint(
        latitude = -6.2000,
        longitude = 106.8167,
    )

    val destination = GeoPoint(
        latitude = -6.1754,
        longitude = 106.8272,
    )
}
