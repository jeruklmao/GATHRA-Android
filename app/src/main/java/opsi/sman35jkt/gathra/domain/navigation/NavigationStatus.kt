package opsi.sman35jkt.gathra.domain.navigation

/**
 * The single source of truth for an active navigation session's lifecycle.
 *
 * UI concerns such as whether a sheet is expanded must not be represented here.
 */
enum class NavigationStatus {
    IDLE,
    PREPARING,
    NAVIGATING,
    RECALCULATING,
    OFF_ROUTE,
    GPS_UNAVAILABLE,
    ARRIVED,
    STOPPED,
    ERROR,
}

