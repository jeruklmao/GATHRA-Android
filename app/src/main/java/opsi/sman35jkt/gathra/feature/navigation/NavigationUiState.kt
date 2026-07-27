package opsi.sman35jkt.gathra.feature.navigation

import opsi.sman35jkt.gathra.core.map.NavigationCameraMode
import opsi.sman35jkt.gathra.core.model.RouteStep
import opsi.sman35jkt.gathra.domain.navigation.NavigationSession
import opsi.sman35jkt.gathra.domain.navigation.NavigationStatus

data class NavigationUiState(
    val session: NavigationSession? = null,
    val cameraMode: NavigationCameraMode = NavigationCameraMode.FOLLOW,
    val stopConfirmationVisible: Boolean = false,
    val simulationEnabled: Boolean = false,
    val simulationPaused: Boolean = false,
    val simulationSpeed: Double = 2.0,
) {
    val nextStep: RouteStep?
        get() {
            val current = session ?: return null
            val currentIndex = current.progress?.currentStepIndex ?: 0
            return current.route.steps.getOrNull(
                (currentIndex + 1).coerceAtMost(current.route.steps.lastIndex),
            )
        }

    val currentStep: RouteStep?
        get() {
            val current = session ?: return null
            return current.route.steps.getOrNull(
                current.progress?.currentStepIndex ?: 0,
            )
        }

    val locationQuality: NavigationLocationQuality
        get() {
            val current = session ?: return NavigationLocationQuality.UNAVAILABLE
            if (current.status == NavigationStatus.GPS_UNAVAILABLE) {
                return NavigationLocationQuality.UNAVAILABLE
            }
            val location = current.rawLocation
                ?: return NavigationLocationQuality.UNAVAILABLE
            return when {
                location.isApproximate -> NavigationLocationQuality.APPROXIMATE
                location.accuracyMeters <= 25.0 -> NavigationLocationQuality.GOOD
                else -> NavigationLocationQuality.WEAK
            }
        }
}

enum class NavigationLocationQuality {
    GOOD,
    APPROXIMATE,
    WEAK,
    UNAVAILABLE,
}
