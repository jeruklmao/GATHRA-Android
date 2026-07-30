package opsi.sman35jkt.gathra.domain.route

/**
 * Framework-independent failures exposed by a route data source.
 *
 * HTTP status codes, Retrofit types, and backend DTOs intentionally stay in the data layer.
 */
class RouteRepositoryException(
    val reason: RouteFailureReason,
    cause: Throwable? = null,
) : Exception(reason.name, cause)

enum class RouteFailureReason {
    OFFLINE,
    TIMEOUT,
    NO_ROUTE,
    NO_ROUTE_DUE_TO_FLOOD,
    ORIGIN_IN_BLOCKED_AREA,
    DESTINATION_IN_BLOCKED_AREA,
    INVALID_RESPONSE,
    SERVER_UNAVAILABLE,
    UNKNOWN,
}
