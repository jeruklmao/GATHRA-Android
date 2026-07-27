package opsi.sman35jkt.gathra.feature.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.ManeuverModifier
import opsi.sman35jkt.gathra.core.model.ManeuverType
import opsi.sman35jkt.gathra.core.model.RouteGeometry
import opsi.sman35jkt.gathra.core.model.RouteManeuver
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteStep
import opsi.sman35jkt.gathra.core.model.RouteSummary
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample
import opsi.sman35jkt.gathra.domain.navigation.NavigationProgress
import opsi.sman35jkt.gathra.domain.navigation.NavigationSession
import opsi.sman35jkt.gathra.domain.navigation.NavigationStatus
import opsi.sman35jkt.gathra.ui.theme.GATHRATheme

class NavigationScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigatingStateShowsInstructionAndRemainingMetrics() {
        setScreen(state = navigatingState())

        composeRule.onNodeWithTag(NavigationTestTags.InstructionCard)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Belok kanan ke Jalan Merdeka")
            .assertIsDisplayed()
        composeRule.onNodeWithText("150 m")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTestTags.RemainingEta)
            .assertIsDisplayed()
        composeRule.onNodeWithText("7 mnt")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTestTags.RemainingDistance)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Jarak")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MapPlaceholderTag)
            .assertIsDisplayed()
    }

    @Test
    fun muteControlDispatchesActionAndShowsUnmuteState() {
        var state by mutableStateOf(navigatingState())
        val actions = mutableListOf<NavigationAction>()
        composeRule.setContent {
            GATHRATheme {
                NavigationScreen(
                    state = state,
                    onAction = { action ->
                        actions += action
                        if (action == NavigationAction.MuteClicked) {
                            state = state.copy(
                                session = requireNotNull(state.session).copy(muted = true),
                            )
                        }
                    },
                    mapContent = { modifier, _, _ ->
                        Box(modifier.testTag(MapPlaceholderTag))
                    },
                )
            }
        }

        composeRule.onNodeWithTag(NavigationTestTags.MuteButton)
            .performClick()

        composeRule.onNodeWithText("Aktifkan petunjuk suara")
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(actions.contains(NavigationAction.MuteClicked))
        }
    }

    @Test
    fun stopControlShowsConfirmationAndCanContinueNavigation() {
        var state by mutableStateOf(navigatingState())
        val actions = mutableListOf<NavigationAction>()
        composeRule.setContent {
            GATHRATheme {
                NavigationScreen(
                    state = state,
                    onAction = { action ->
                        actions += action
                        when (action) {
                            NavigationAction.StopRequested ->
                                state = state.copy(stopConfirmationVisible = true)
                            NavigationAction.StopDismissed ->
                                state = state.copy(stopConfirmationVisible = false)
                            else -> Unit
                        }
                    },
                    mapContent = { modifier, _, _ ->
                        Box(modifier.testTag(MapPlaceholderTag))
                    },
                )
            }
        }

        composeRule.onNodeWithTag(NavigationTestTags.StopButton)
            .performClick()
        composeRule.onNodeWithText("Hentikan navigasi?")
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "Lokasi langsung dan petunjuk suara akan dihentikan.",
        ).assertIsDisplayed()

        composeRule.onNodeWithText("Lanjutkan navigasi")
            .performClick()
        composeRule.onNodeWithText("Hentikan navigasi?")
            .assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(actions.contains(NavigationAction.StopRequested))
            assertTrue(actions.contains(NavigationAction.StopDismissed))
        }
    }

    @Test
    fun arrivedStateShowsArrivalPanelAndDispatchesFinish() {
        val actions = mutableListOf<NavigationAction>()
        setScreen(
            state = navigatingState(
                status = NavigationStatus.ARRIVED,
                isArrived = true,
            ),
            onAction = actions::add,
        )

        composeRule.onNodeWithTag(NavigationTestTags.ArrivalPanel)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Selesai")
            .performClick()

        composeRule.runOnIdle {
            assertTrue(actions.contains(NavigationAction.FinishClicked))
        }
    }

    @Test
    fun compactScreenAtTwoTimesFontKeepsCriticalControlsReachable() {
        val longStreetName =
            "Jalan Pengujian Aksesibilitas dengan Nama yang Sangat Panjang"
        composeRule.setContent {
            GATHRATheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = 1f,
                        fontScale = 2f,
                    ),
                ) {
                    NavigationScreen(
                        state = navigatingState(longStreetName = longStreetName),
                        onAction = {},
                        mapContent = { modifier, _, _ ->
                            Box(modifier.testTag(MapPlaceholderTag))
                        },
                        modifier = Modifier.size(width = 360.dp, height = 640.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(NavigationTestTags.InstructionCard)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTestTags.MuteButton)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTestTags.StopButton)
            .performScrollTo()
            .assertIsDisplayed()
        val instructionBounds = composeRule
            .onNodeWithTag(NavigationTestTags.InstructionCard)
            .fetchSemanticsNode()
            .boundsInRoot
        val bottomBounds = composeRule
            .onNodeWithTag(NavigationTestTags.BottomPanel)
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Instruction card $instructionBounds overlaps bottom panel $bottomBounds",
            instructionBounds.bottom <= bottomBounds.top,
        )
    }

    private fun setScreen(
        state: NavigationUiState,
        onAction: (NavigationAction) -> Unit = {},
    ) {
        composeRule.setContent {
            GATHRATheme {
                NavigationScreen(
                    state = state,
                    onAction = onAction,
                    mapContent = { modifier, _, _ ->
                        Box(modifier.testTag(MapPlaceholderTag))
                    },
                )
            }
        }
    }

    private fun navigatingState(
        status: NavigationStatus = NavigationStatus.NAVIGATING,
        isArrived: Boolean = false,
        longStreetName: String? = null,
    ): NavigationUiState {
        val route = route(longStreetName)
        val progress = NavigationProgress(
            matchedLocation = route.geometry.points[1],
            distanceFromRouteMeters = 3.0,
            travelledDistanceMeters = if (isArrived) 2_100.0 else 650.0,
            remainingDistanceMeters = if (isArrived) 0.0 else 1_450.0,
            remainingDurationSeconds = if (isArrived) 0L else 420L,
            currentStepIndex = if (isArrived) route.steps.lastIndex else 0,
            distanceToNextManoeuvreMeters = if (isArrived) 0.0 else 150.0,
            matchedSegmentIndex = if (isArrived) {
                route.geometry.points.lastIndex - 1
            } else {
                1
            },
            isOffRoute = false,
            shouldReroute = false,
            isArrived = isArrived,
        )
        return NavigationUiState(
            session = NavigationSession(
                id = "navigation-ui-test",
                route = route,
                destination = route.geometry.points.last(),
                travelMode = TravelMode.CAR,
                status = status,
                progress = progress,
                rawLocation = NavigationLocationSample(
                    point = progress.matchedLocation,
                    accuracyMeters = 8.0,
                    bearingDegrees = 90.0,
                    speedMetersPerSecond = 9.0,
                    elapsedRealtimeMillis = 10_000L,
                    epochTimeMillis = 20_000L,
                ),
                startedAtMillis = 1_000L,
            ),
        )
    }

    private fun route(longStreetName: String? = null): RouteOption {
        val points = listOf(
            GeoPoint(latitude = -6.2000, longitude = 106.8000),
            GeoPoint(latitude = -6.2000, longitude = 106.8050),
            GeoPoint(latitude = -6.2050, longitude = 106.8050),
            GeoPoint(latitude = -6.2050, longitude = 106.8100),
        )
        return RouteOption(
            id = "navigation-route",
            geometry = RouteGeometry(points),
            summary = RouteSummary(
                distanceMeters = 2_100,
                etaMinutes = 10,
                durationSeconds = 600,
            ),
            isRecommended = true,
            steps = listOf(
                routeStep(
                    index = 0,
                    instruction = "Mulai menuju Jalan Mawar",
                    streetName = "Jalan Mawar",
                    type = ManeuverType.DEPART,
                    modifier = ManeuverModifier.STRAIGHT,
                    startIndex = 0,
                    endIndex = 1,
                ),
                routeStep(
                    index = 1,
                    instruction = "Belok kanan ke Jalan Merdeka",
                    streetName = longStreetName ?: "Jalan Merdeka",
                    type = ManeuverType.TURN,
                    modifier = ManeuverModifier.RIGHT,
                    startIndex = 1,
                    endIndex = 2,
                ),
                routeStep(
                    index = 2,
                    instruction = "Anda telah tiba",
                    streetName = "",
                    type = ManeuverType.ARRIVE,
                    modifier = ManeuverModifier.NONE,
                    startIndex = 2,
                    endIndex = 3,
                ),
            ),
        )
    }

    private fun routeStep(
        index: Int,
        instruction: String,
        streetName: String,
        type: ManeuverType,
        modifier: ManeuverModifier,
        startIndex: Int,
        endIndex: Int,
    ): RouteStep = RouteStep(
        index = index,
        instruction = instruction,
        streetName = streetName,
        distanceMeters = if (type == ManeuverType.ARRIVE) 0 else 700,
        durationSeconds = if (type == ManeuverType.ARRIVE) 0 else 200,
        maneuver = RouteManeuver(
            type = type,
            modifier = modifier,
            bearingBefore = if (type == ManeuverType.DEPART) null else 90,
            bearingAfter = if (type == ManeuverType.ARRIVE) null else 90,
        ),
        geometryStartIndex = startIndex,
        geometryEndIndex = endIndex,
    )

    private companion object {
        const val MapPlaceholderTag = "navigation_test_map_placeholder"
    }
}
