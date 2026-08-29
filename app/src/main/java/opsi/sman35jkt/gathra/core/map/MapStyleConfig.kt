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

    // MapLibre needs a camera before backend geometry is available. This is a
    // non-user-visible technical fallback; production startup is fitted to the
    // first backend SENSOR polygon by InitialFloodCameraPolicy.
    const val INITIAL_FALLBACK_LATITUDE = 0.0
    const val INITIAL_FALLBACK_LONGITUDE = 0.0
    const val INITIAL_FALLBACK_ZOOM = 1.5
}
