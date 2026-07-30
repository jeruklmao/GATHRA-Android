package opsi.sman35jkt.gathra.domain.navigation

/**
 * Whether the active route was evaluated against the flood snapshot currently
 * displayed by the app.
 */
enum class NavigationFloodRouteStatus {
    NOT_EVALUATED,
    SYNCHRONIZED,
    UPDATING,
    STALE,
}
