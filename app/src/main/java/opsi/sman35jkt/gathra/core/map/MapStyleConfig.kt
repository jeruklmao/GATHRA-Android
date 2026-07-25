package opsi.sman35jkt.gathra.core.map

/**
 * Map style configuration that is safe to ship without credentials.
 *
 * OpenFreeMap publishes this MapLibre-compatible style without registration or an API key.
 * A production release should still replace it with a service whose availability guarantees
 * match GATHRA's requirements.
 */
object MapStyleConfig {
    const val PUBLIC_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"

    const val INITIAL_JAKARTA_LATITUDE = -6.2000
    const val INITIAL_JAKARTA_LONGITUDE = 106.8167
    const val INITIAL_ZOOM = 12.0
}
