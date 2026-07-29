package opsi.sman35jkt.gathra.feature.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.map.MapLibreNavigationMap
import opsi.sman35jkt.gathra.core.map.NavigationMapColors
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.ui.theme.GathraTheme

@Composable
fun NavigationRoute(
    viewModel: NavigationViewModel,
    floodHazardSnapshot: FloodHazardSnapshot? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val voiceUnavailableMessage = stringResource(R.string.navigation_tts_unavailable)
    val serviceFailureMessage = stringResource(R.string.navigation_service_start_failed)
    val mapUnavailableMessage = stringResource(R.string.map_style_unavailable)
    val mapDescription = stringResource(R.string.navigation_map_accessibility)
    val themedMapColors = GathraTheme.mapColors
    val materialColors = MaterialTheme.colorScheme

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            snackbarHostState.showSnackbar(
                when (effect) {
                    NavigationEffect.VOICE_UNAVAILABLE -> voiceUnavailableMessage
                    NavigationEffect.SERVICE_ACTION_FAILED -> serviceFailureMessage
                },
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavigationScreen(
            state = state,
            onAction = viewModel::onAction,
            mapContent = { mapModifier, topClearance, bottomClearance ->
                val session = state.session
                if (session != null) {
                    val rawLocation = session.rawLocation
                    val reliableBearing = rawLocation
                        ?.takeIf {
                            (it.speedMetersPerSecond ?: 0.0) >=
                                MIN_BEARING_SPEED_METERS_PER_SECOND &&
                                it.accuracyMeters <=
                                MAX_BEARING_ACCURACY_METERS &&
                                !it.isApproximate
                        }
                        ?.bearingDegrees
                    MapLibreNavigationMap(
                        activeRoute = session.route,
                        travelledDistanceMeters =
                            session.progress?.travelledDistanceMeters ?: 0.0,
                        matchedLocation = session.progress?.matchedLocation,
                        rawLocation = rawLocation?.point,
                        bearingDegrees = reliableBearing,
                        accuracyMeters = rawLocation?.accuracyMeters,
                        destination = session.destination,
                        cameraMode = state.cameraMode,
                        topOverlayClearance = topClearance,
                        bottomOverlayClearance = bottomClearance,
                        colors = NavigationMapColors(
                            remainingRoute = themedMapColors.selectedRoute,
                            remainingRouteOutline = themedMapColors.routeCasing,
                            completedRoute = themedMapColors.completedRoute,
                            accuracyHalo = themedMapColors.navigationAccuracy,
                            accuracyHaloOutline = themedMapColors.navigationRawLocation,
                            userPuck = themedMapColors.navigationPuck,
                            userPuckHeading = materialColors.primary,
                            userPuckStroke = materialColors.surface,
                            destinationMarker = themedMapColors.destinationMarker,
                            destinationMarkerStroke = materialColors.surface,
                        ),
                        floodSnapshot = floodHazardSnapshot,
                        onManualPan = {
                            viewModel.onAction(NavigationAction.MapPanned)
                        },
                        onMapError = {
                            scope.launch {
                                snackbarHostState.showSnackbar(mapUnavailableMessage)
                            }
                        },
                        modifier = mapModifier.semantics {
                            contentDescription = mapDescription
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private const val MIN_BEARING_SPEED_METERS_PER_SECOND = 1.5
private const val MAX_BEARING_ACCURACY_METERS = 25.0
