package opsi.sman35jkt.gathra.feature.navigation

sealed interface NavigationAction {
    data object MapPanned : NavigationAction
    data object RecenterClicked : NavigationAction
    data object OverviewClicked : NavigationAction
    data object MuteClicked : NavigationAction
    data object StopRequested : NavigationAction
    data object StopDismissed : NavigationAction
    data object StopConfirmed : NavigationAction
    data object FinishClicked : NavigationAction
    data object RetryReroute : NavigationAction
    data class FloodSnapshotChanged(val snapshotId: String) : NavigationAction
}
