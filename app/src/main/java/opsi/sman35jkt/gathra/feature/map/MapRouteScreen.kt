package opsi.sman35jkt.gathra.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.model.RouteSelectionPoint
import opsi.sman35jkt.gathra.core.model.SelectionPointSource
import opsi.sman35jkt.gathra.feature.map.components.CurrentLocationButton
import opsi.sman35jkt.gathra.feature.map.components.FloodHazardDetailSheet
import opsi.sman35jkt.gathra.feature.map.components.PointSelectionControls
import opsi.sman35jkt.gathra.feature.map.components.RouteBottomSheetContent
import opsi.sman35jkt.gathra.feature.map.components.RouteInputCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapRouteScreen(
    state: MapRouteUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (MapRouteAction) -> Unit,
    mapContent: @Composable (Modifier, Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 700.dp
        val sheetPeekHeight = when {
            state.pointSelectionMode != null && compactHeight -> 104.dp
            state.pointSelectionMode != null -> 120.dp
            else -> 252.dp
        }
        val density = LocalDensity.current
        val sheetState = rememberStandardBottomSheetState(
            initialValue = if (
                state.bottomSheetState == RouteBottomSheetState.EXPANDED
            ) {
                SheetValue.Expanded
            } else {
                SheetValue.PartiallyExpanded
            },
            skipHiddenState = true,
        )
        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = sheetState,
            snackbarHostState = snackbarHostState,
        )
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val sheetClearance by remember(sheetState, screenHeightPx, density, sheetPeekHeight) {
            derivedStateOf {
                runCatching {
                    with(density) {
                        (screenHeightPx - sheetState.requireOffset()).toDp()
                    }
                }.getOrDefault(sheetPeekHeight)
            }
        }

        LaunchedEffect(state.bottomSheetState, state.pointSelectionMode) {
            if (
                state.pointSelectionMode != null ||
                state.bottomSheetState == RouteBottomSheetState.COLLAPSED
            ) {
                sheetState.partialExpand()
            } else {
                sheetState.expand()
            }
        }

        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.currentValue }
                .distinctUntilChanged()
                .collect { value ->
                    when (value) {
                        SheetValue.Expanded -> onAction(
                            MapRouteAction.BottomSheetChanged(
                                RouteBottomSheetState.EXPANDED,
                            ),
                        )
                        SheetValue.PartiallyExpanded -> onAction(
                            MapRouteAction.BottomSheetChanged(
                                RouteBottomSheetState.COLLAPSED,
                            ),
                        )
                        SheetValue.Hidden -> Unit
                    }
                }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = sheetPeekHeight,
            sheetShape = MaterialTheme.shapes.extraLarge,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetContentColor = MaterialTheme.colorScheme.onSurface,
            sheetShadowElevation = 10.dp,
            sheetSwipeEnabled = state.pointSelectionMode == null,
            sheetDragHandle = {
                val expanded = state.bottomSheetState == RouteBottomSheetState.EXPANDED
                val description = stringResource(
                    if (expanded) {
                        R.string.collapse_route_sheet
                    } else {
                        R.string.expand_route_sheet
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp)
                        .clickable {
                            onAction(
                                MapRouteAction.BottomSheetChanged(
                                    if (expanded) {
                                        RouteBottomSheetState.COLLAPSED
                                    } else {
                                        RouteBottomSheetState.EXPANDED
                                    },
                                ),
                            )
                        }
                        .semantics {
                            contentDescription = description
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BottomSheetDefaults.DragHandle()
                }
            },
            sheetContent = {
                RouteBottomSheetContent(
                    selectedTravelMode = state.selectedTravelMode,
                    destinationSelected = state.destination != null,
                    isLoading = state.routeContentState == RouteContentState.LOADING,
                    routeError = state.error.takeIf {
                        state.routeContentState == RouteContentState.ERROR
                    },
                    routes = state.routes,
                    selectedRouteId = state.selectedRouteId,
                    expanded = state.bottomSheetState == RouteBottomSheetState.EXPANDED,
                    onTravelModeSelected = {
                        onAction(MapRouteAction.TravelModeSelected(it))
                    },
                    onRouteSelected = {
                        onAction(MapRouteAction.RouteSelected(it))
                    },
                    onRetry = { onAction(MapRouteAction.RetryRoute) },
                    onPreview = { onAction(MapRouteAction.PreviewClicked) },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                mapContent(Modifier.fillMaxSize(), sheetClearance)

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(
                            horizontal = if (compactHeight) 12.dp else 16.dp,
                            vertical = 10.dp,
                        ),
                ) {
                    RouteInputCard(
                        originValue = routePointLabel(
                            point = state.origin,
                            isOrigin = true,
                        ),
                        destinationValue = routePointLabel(
                            point = state.destination,
                            isOrigin = false,
                        ),
                        selectingOrigin =
                            state.pointSelectionMode == PointSelectionMode.ORIGIN,
                        selectingDestination =
                            state.pointSelectionMode == PointSelectionMode.DESTINATION,
                        swapEnabled = state.origin != null && state.destination != null,
                        onOriginClick = {
                            onAction(
                                MapRouteAction.SearchRequested(
                                    PointSelectionMode.ORIGIN,
                                ),
                            )
                            onAction(
                                MapRouteAction.BottomSheetChanged(
                                    RouteBottomSheetState.COLLAPSED,
                                ),
                            )
                        },
                        onDestinationClick = {
                            onAction(
                                MapRouteAction.SearchRequested(
                                    PointSelectionMode.DESTINATION,
                                ),
                            )
                            onAction(
                                MapRouteAction.BottomSheetChanged(
                                    RouteBottomSheetState.COLLAPSED,
                                ),
                            )
                        },
                        onSwapClick = { onAction(MapRouteAction.SwapPoints) },
                    )

                    if (state.pointSelectionMode != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        PointSelectionControls(
                            selectingOrigin =
                                state.pointSelectionMode == PointSelectionMode.ORIGIN,
                            hasPendingPoint = state.pendingPoint != null,
                            onCancel = {
                                onAction(MapRouteAction.CancelPointSelection)
                            },
                            onConfirm = {
                                onAction(MapRouteAction.ConfirmPointSelection)
                            },
                        )
                    }
                }

                state.selectedFloodHazard?.let { hazard ->
                    FloodHazardDetailSheet(
                        hazard = hazard,
                        onDismiss = {
                            onAction(MapRouteAction.DismissFloodHazardDetails)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = sheetClearance + 12.dp),
                    )
                }

                CurrentLocationButton(
                    isLocating = state.isLocating,
                    onClick = { onAction(MapRouteAction.CurrentLocationClicked) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = sheetClearance + 16.dp,
                        ),
                )
            }
        }
    }
}

@Composable
private fun routePointLabel(
    point: RouteSelectionPoint?,
    isOrigin: Boolean,
): String {
    if (point == null) {
        return stringResource(
            if (isOrigin) {
                R.string.choose_origin_on_map
            } else {
                R.string.choose_destination_on_map
            },
        )
    }
    point.displayName?.takeIf { it.isNotBlank() }?.let { return it }
    return when (point.source) {
        SelectionPointSource.CURRENT_LOCATION -> stringResource(R.string.current_location)
        SelectionPointSource.DEMO_FALLBACK -> if (isOrigin) {
            stringResource(R.string.jakarta_demo_location)
        } else {
            coordinateLabel(point)
        }
        SelectionPointSource.MAP_SELECTION,
        SelectionPointSource.GEOCODING_SEARCH,
        -> coordinateLabel(point)
    }
}

@Composable
private fun coordinateLabel(point: RouteSelectionPoint): String =
    stringResource(
        R.string.coordinate_label,
        point.point.latitude,
        point.point.longitude,
    )
