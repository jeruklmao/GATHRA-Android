package opsi.sman35jkt.gathra.domain.geocoding

class GeocodingRepositoryException(
    val reason: GeocodingFailureReason,
    cause: Throwable? = null,
) : Exception(reason.name, cause)

enum class GeocodingFailureReason {
    INVALID_QUERY,
    INVALID_COORDINATES,
    OUTSIDE_SUPPORTED_REGION,
    PLACE_NOT_FOUND,
    TIMEOUT,
    OFFLINE,
    SERVER_UNAVAILABLE,
    INVALID_RESPONSE,
    UNKNOWN,
}
