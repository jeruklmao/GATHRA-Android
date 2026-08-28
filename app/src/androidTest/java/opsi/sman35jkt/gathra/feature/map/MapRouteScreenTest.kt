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
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import opsi.sman35jkt.gathra.core.model.FloodRiskLevel
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteFloodRisk
import opsi.sman35jkt.gathra.core.model.RouteGeometry
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteSelectionPoint
import opsi.sman35jkt.gathra.core.model.RouteSummary
import opsi.sman35jkt.gathra.core.model.SelectionPointSource
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.feature.map.components.FloodHazardDetailSheet
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

    @Test
    fun tappingDestinationRequestsSearchInsteadOfRemovingMapFallback() {
        var requestedMode: PointSelectionMode? = null
        setReadyScreen { action ->
            if (action is MapRouteAction.SearchRequested) {
                requestedMode = action.mode
            }
        }

        composeRule.onNodeWithTag(MapRouteTestTags.DestinationField)
            .performClick()

        assertEquals(PointSelectionMode.DESTINATION, requestedMode)
    }

    @Test
    fun reverseGeocodedNameReplacesCoordinateLabel() {
        val state = readyState().copy(
            destination = readyState().destination?.copy(
                displayName = "Jalan Karet Pasar Baru",
            ),
        )
        composeRule.setContent {
            GATHRATheme {
                MapRouteScreen(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onAction = {},
                    mapContent = { _, _ -> Box(Modifier.fillMaxSize()) },
                )
            }
        }

        composeRule.onNodeWithText("Jalan Karet Pasar Baru")
            .assertIsDisplayed()
    }

    @Test
    fun newerFloodSnapshotHidesOldLowBadgeWhileRouteUpdates() {
        setScreen(
            readyState().copy(
                floodRouteSyncState = FloodRouteSyncState.UPDATING,
                floodRouteTargetSnapshotId = "snapshot-b",
            ),
        )

        composeRule.onNodeWithText(
            "Kondisi banjir berubah. Rute sedang diperbarui.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Risiko belum dievaluasi")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Risiko banjir rendah")
            .assertDoesNotExist()
    }

    @Test
    fun unknownRouteRiskIsNotRenderedAsLow() {
        setScreen(readyState(riskLevel = FloodRiskLevel.UNKNOWN))

        composeRule.onNodeWithText("Risiko belum diketahui")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Risiko banjir rendah")
            .assertDoesNotExist()
    }

    @Test
    fun unknownHazardDetailUsesUnknownWording() {
        composeRule.setContent {
            GATHRATheme {
                FloodHazardDetailSheet(
                    hazard = FloodHazardPolygon(
                        id = "unknown-hazard",
                        level = FloodHazardLevel.UNKNOWN,
                        rings = listOf(
                            listOf(
                                GeoPoint(-6.2, 106.8),
                                GeoPoint(-6.2, 106.81),
                                GeoPoint(-6.19, 106.81),
                                GeoPoint(-6.2, 106.8),
                            ),
                        ),
                        confidence = null,
                        description = "Raw provider description",
                        observedAtEpochMillis = 1_000L,
                        validUntilEpochMillis = 2_000L,
                        source = FloodHazardSource.UNKNOWN,
                        sourceNodeIds = emptyList(),
                        routingMultiplier = 1.0,
                        reasonCodes = emptyList(),
                        freshness = FloodHazardFreshness.FRESH,
                    ),
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Risiko belum diketahui")
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "Kondisi banjir di area ini belum dapat ditentukan dari data yang tersedia.",
        )
            .assertIsDisplayed()
        composeRule.onNodeWithText("Risiko banjir rendah")
            .assertDoesNotExist()
        composeRule.onNodeWithText("Raw provider description")
            .assertDoesNotExist()
    }

    private fun setReadyScreen(
        onAction: (MapRouteAction) -> Unit = {},
    ) {
        setScreen(readyState(), onAction)
    }

    private fun setScreen(
        state: MapRouteUiState,
        onAction: (MapRouteAction) -> Unit = {},
    ) {
        composeRule.setContent {
            GATHRATheme {
                MapRouteScreen(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onAction = onAction,
                    mapContent = { _, _ -> Box(Modifier.fillMaxSize()) },
                )
            }
        }
    }

    private fun readyState(
        riskLevel: FloodRiskLevel = FloodRiskLevel.LOW,
    ): MapRouteUiState {
        val destination = JakartaDemoPoints.suggestedDestination
        val risk = routeRisk(riskLevel)
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
            risk = risk,
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
            risk = risk,
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
            floodHazardSnapshot = FloodHazardSnapshot(
                snapshotId = TEST_SNAPSHOT_ID,
                generatedAtEpochMillis = 1_000L,
                validUntilEpochMillis = 10_000L,
                source = FloodHazardSource.SIMULATED,
                hazards = emptyList(),
            ),
            floodRefreshStatus = FloodRefreshStatus.SUCCEEDED,
            floodRouteSyncState = FloodRouteSyncState.SYNCHRONIZED,
        )
    }

    private fun routeRisk(level: FloodRiskLevel): RouteFloodRisk =
        RouteFloodRisk(
            level = level,
            score = if (level == FloodRiskLevel.LOW) 0.0 else 0.5,
            intersectsBlockedArea = false,
            affectedDistanceMeters = 0,
            confidence = 0.9,
            reasonCodes = listOf("TEST_FIXTURE"),
            evaluatedAtEpochMillis = 1_000L,
            validUntilEpochMillis = 10_000L,
            hazardSnapshotId = TEST_SNAPSHOT_ID,
        )

    private companion object {
        const val TEST_SNAPSHOT_ID = "snapshot-a"
    }
}
