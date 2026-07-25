package opsi.sman35jkt.gathra.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import opsi.sman35jkt.gathra.core.map.JakartaDemoPoints
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteGeometry
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteSelectionPoint
import opsi.sman35jkt.gathra.core.model.RouteSummary
import opsi.sman35jkt.gathra.core.model.SelectionPointSource
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.ui.theme.GATHRATheme

class MapRouteScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun topCardSummaryAndEtaAreVisibleForReadyRoute() {
        setReadyScreen()

        composeRule.onNodeWithTag(MapRouteTestTags.RouteInputCard)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MapRouteTestTags.RouteSummary)
            .assertIsDisplayed()
        composeRule.onNodeWithText("12 mnt")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MapRouteTestTags.PreviewButton)
            .assertIsDisplayed()
    }

    @Test
    fun transportModeCanBeChangedToMotorcycle() {
        var state by mutableStateOf(readyState())
        composeRule.setContent {
            GATHRATheme {
                MapRouteScreen(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onAction = { action ->
                        if (action is MapRouteAction.TravelModeSelected) {
                            state = state.copy(selectedTravelMode = action.mode)
                        }
                    },
                    mapContent = { _, _ -> Box(Modifier.fillMaxSize()) },
                )
            }
        }

        composeRule.onNodeWithTag(MapRouteTestTags.MotorcycleMode)
            .performClick()
            .assertIsSelected()
        assertEquals(TravelMode.MOTORCYCLE, state.selectedTravelMode)
    }

    @Test
    fun previewButtonOnlyEmitsPreviewAction() {
        var previewActions = 0
        setReadyScreen { action ->
            if (action == MapRouteAction.PreviewClicked) {
                previewActions += 1
            }
        }

        composeRule.onNodeWithTag(MapRouteTestTags.PreviewButton).performClick()

        assertEquals(1, previewActions)
    }

    private fun setReadyScreen(
        onAction: (MapRouteAction) -> Unit = {},
    ) {
        composeRule.setContent {
            GATHRATheme {
                MapRouteScreen(
                    state = readyState(),
                    snackbarHostState = SnackbarHostState(),
                    onAction = onAction,
                    mapContent = { _, _ -> Box(Modifier.fillMaxSize()) },
                )
            }
        }
    }

    private fun readyState(): MapRouteUiState {
        val destination = JakartaDemoPoints.suggestedDestination
        val route = RouteOption(
            id = "selected",
            geometry = RouteGeometry(
                points = listOf(JakartaDemoPoints.origin, destination),
            ),
            summary = RouteSummary(
                distanceMeters = 5_200,
                etaMinutes = 12,
            ),
            isRecommended = true,
        )
        val alternative = RouteOption(
            id = "alternative",
            geometry = RouteGeometry(
                points = listOf(
                    JakartaDemoPoints.origin,
                    GeoPoint(-6.19, 106.83),
                    destination,
                ),
            ),
            summary = RouteSummary(
                distanceMeters = 5_800,
                etaMinutes = 15,
            ),
        )
        return MapRouteUiState(
            origin = RouteSelectionPoint(
                point = JakartaDemoPoints.origin,
                source = SelectionPointSource.DEMO_FALLBACK,
            ),
            destination = RouteSelectionPoint(
                point = destination,
                source = SelectionPointSource.MAP_SELECTION,
            ),
            selectedTravelMode = TravelMode.CAR,
            routes = listOf(route, alternative),
            selectedRouteId = route.id,
            routeContentState = RouteContentState.READY,
        )
    }
}
