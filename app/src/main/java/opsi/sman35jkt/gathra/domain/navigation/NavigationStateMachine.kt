package opsi.sman35jkt.gathra.domain.navigation

object NavigationStateMachine {
    private val allowedTransitions = mapOf(
        NavigationStatus.IDLE to setOf(
            NavigationStatus.PREPARING,
            NavigationStatus.STOPPED,
        ),
        NavigationStatus.PREPARING to setOf(
            NavigationStatus.NAVIGATING,
            NavigationStatus.GPS_UNAVAILABLE,
            NavigationStatus.STOPPED,
            NavigationStatus.ERROR,
        ),
        NavigationStatus.NAVIGATING to setOf(
            NavigationStatus.OFF_ROUTE,
            NavigationStatus.RECALCULATING,
            NavigationStatus.GPS_UNAVAILABLE,
            NavigationStatus.ARRIVED,
            NavigationStatus.STOPPED,
            NavigationStatus.ERROR,
        ),
        NavigationStatus.OFF_ROUTE to setOf(
            NavigationStatus.RECALCULATING,
            NavigationStatus.NAVIGATING,
            NavigationStatus.GPS_UNAVAILABLE,
            NavigationStatus.ARRIVED,
            NavigationStatus.STOPPED,
            NavigationStatus.ERROR,
        ),
        NavigationStatus.RECALCULATING to setOf(
            NavigationStatus.NAVIGATING,
            NavigationStatus.OFF_ROUTE,
            NavigationStatus.GPS_UNAVAILABLE,
            NavigationStatus.ARRIVED,
            NavigationStatus.STOPPED,
            NavigationStatus.ERROR,
        ),
        NavigationStatus.GPS_UNAVAILABLE to setOf(
            NavigationStatus.NAVIGATING,
            NavigationStatus.OFF_ROUTE,
            NavigationStatus.ARRIVED,
            NavigationStatus.STOPPED,
            NavigationStatus.ERROR,
        ),
        NavigationStatus.ARRIVED to setOf(
            NavigationStatus.STOPPED,
        ),
        NavigationStatus.STOPPED to setOf(
            NavigationStatus.IDLE,
            NavigationStatus.PREPARING,
        ),
        NavigationStatus.ERROR to setOf(
            NavigationStatus.PREPARING,
            NavigationStatus.RECALCULATING,
            NavigationStatus.NAVIGATING,
            NavigationStatus.ARRIVED,
            NavigationStatus.STOPPED,
            NavigationStatus.IDLE,
        ),
    )

    fun canTransition(from: NavigationStatus, to: NavigationStatus): Boolean =
        from == to || to in allowedTransitions.getValue(from)

    fun transition(session: NavigationSession, to: NavigationStatus): NavigationSession {
        require(canTransition(session.status, to)) {
            "Invalid navigation transition: ${session.status} -> $to"
        }
        return session.copy(status = to)
    }
}
