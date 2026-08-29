package opsi.sman35jkt.gathra.feature.map

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.map.MapLibreRouteMap
import opsi.sman35jkt.gathra.core.map.RouteMapColors
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.ui.theme.GathraTheme

private const val LOCATION_PERMISSION_PREFERENCES = "gathra_location_permission"
private const val LOCATION_PERMISSION_REQUESTED = "foreground_requested"

@Composable
fun MapRouteRoute(
    viewModel: MapRouteViewModel,
    onStartNavigation: (RouteOption, GeoPoint, TravelMode) -> Boolean,
    onOpenPlaceSearch: (PointSelectionMode, GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val approximatePermissionNotice = stringResource(
        R.string.permission_approximate_notice,
    )
    val deniedPermissionNotice = stringResource(R.string.permission_denied_notice)
    val routeUnavailableMessage = stringResource(
        R.string.navigation_route_unavailable,
    )
    val locationDisabledMessage = stringResource(R.string.location_services_disabled)
    val locationUnavailableMessage = stringResource(
        R.string.current_location_unavailable,
    )
    val mapUnavailableMessage = stringResource(R.string.map_style_unavailable)
    val navigationStartFailedMessage = stringResource(
        R.string.navigation_service_start_failed,
    )
    val floodSnapshotUpdatedNotice = stringResource(R.string.flood_snapshot_updated)
    val floodRouteOutdatedMessage = stringResource(R.string.flood_route_outdated_message)
    val permissionPreferences = remember(context) {
        context.getSharedPreferences(
            LOCATION_PERMISSION_PREFERENCES,
            Context.MODE_PRIVATE,
        )
    }
    var permanentDialogDismissed by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val preciseGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val approximateGranted =
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val permanentlyDenied = !preciseGranted &&
            !approximateGranted &&
            activity != null &&
            !activity.shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) &&
            !activity.shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        viewModel.onAction(
            MapRouteAction.LocationPermissionResult(
                preciseGranted = preciseGranted,
                approximateGranted = approximateGranted,
                permanentlyDenied = permanentlyDenied,
            ),
        )
        when {
            approximateGranted && !preciseGranted -> scope.launch {
                snackbarHostState.showSnackbar(approximatePermissionNotice)
            }
            !preciseGranted && !approximateGranted -> scope.launch {
                snackbarHostState.showSnackbar(deniedPermissionNotice)
            }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // A denied notification permission does not stop a foreground navigation
        // session; Android still exposes the running service in system UI.
    }

    LaunchedEffect(state.isFloodSnapshotOutOfSync) {
        if (state.isFloodSnapshotOutOfSync) {
            snackbarHostState.showSnackbar(floodSnapshotUpdatedNotice)
        }
    }

    LaunchedEffect(viewModel, activity) {
        val preciseGranted = context.hasPermission(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val approximateGranted = context.hasPermission(
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        when {
            preciseGranted || approximateGranted -> viewModel.onAction(
                MapRouteAction.LocationPermissionResult(
                    preciseGranted = preciseGranted,
                    approximateGranted = approximateGranted,
                ),
            )
            permissionPreferences.getBoolean(
                LOCATION_PERMISSION_REQUESTED,
                false,
            ) -> {
                val permanentlyDenied = activity != null &&
                    !activity.shouldShowRequestPermissionRationale(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) &&
                    !activity.shouldShowRequestPermissionRationale(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                viewModel.onAction(
                    MapRouteAction.LocationPermissionResult(
                        preciseGranted = false,
                        approximateGranted = false,
                        permanentlyDenied = permanentlyDenied,
                    ),
                )
            }
            else -> viewModel.onAction(MapRouteAction.CurrentLocationClicked)
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val preciseGranted = context.hasPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
                val approximateGranted = context.hasPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
                if (
                    preciseGranted || approximateGranted
                ) {
                    val currentPermissionState =
                        viewModel.uiState.value.locationPermissionState
                    val expectedPermissionState = if (preciseGranted) {
                        LocationPermissionState.PRECISE
                    } else {
                        LocationPermissionState.APPROXIMATE
                    }
                    if (currentPermissionState != expectedPermissionState) {
                        viewModel.onAction(
                            MapRouteAction.LocationPermissionResult(
                                preciseGranted = preciseGranted,
                                approximateGranted = approximateGranted,
                            ),
                        )
                    }
                } else if (
                    permissionPreferences.getBoolean(
                        LOCATION_PERMISSION_REQUESTED,
                        false,
                    ) &&
                    viewModel.uiState.value.locationPermissionState in setOf(
                        LocationPermissionState.PRECISE,
                        LocationPermissionState.APPROXIMATE,
                    )
                ) {
                    val permanentlyDenied = activity != null &&
                        !activity.shouldShowRequestPermissionRationale(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) &&
                        !activity.shouldShowRequestPermissionRationale(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    viewModel.onAction(
                        MapRouteAction.LocationPermissionResult(
                            preciseGranted = false,
                            approximateGranted = false,
                            permanentlyDenied = permanentlyDenied,
                        ),
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel, activity) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MapRouteEffect.RequestForegroundLocationPermission -> {
                    permissionPreferences.edit {
                        putBoolean(LOCATION_PERMISSION_REQUESTED, true)
                    }
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
                MapRouteEffect.OpenApplicationSettings -> {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }
                is MapRouteEffect.ShowMessage -> snackbarHostState.showSnackbar(
                    when (effect.message) {
                        MapRouteMessage.LOCATION_DISABLED -> locationDisabledMessage
                        MapRouteMessage.LOCATION_UNAVAILABLE -> locationUnavailableMessage
                        MapRouteMessage.NAVIGATION_ROUTE_UNAVAILABLE ->
                            routeUnavailableMessage
                        MapRouteMessage.FLOOD_ROUTE_OUTDATED ->
                            floodRouteOutdatedMessage
                    },
                )
                is MapRouteEffect.OpenPlaceSearch -> onOpenPlaceSearch(
                    effect.mode,
                    effect.proximity,
                )
                is MapRouteEffect.StartNavigation -> {
                    val started = onStartNavigation(
                        effect.route,
                        effect.destination,
                        effect.travelMode,
                    )
                    if (!started) {
                        snackbarHostState.showSnackbar(
                            navigationStartFailedMessage,
                        )
                    } else if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(state.locationPermissionState) {
        if (
            state.locationPermissionState !=
            LocationPermissionState.PERMANENTLY_DENIED
        ) {
            permanentDialogDismissed = false
        }
    }

    val mapColors = GathraTheme.mapColors
    val mapDescription = stringResource(R.string.map_accessibility)
    MapRouteScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        mapContent = { mapModifier, bottomOverlayClearance ->
            MapLibreRouteMap(
                origin = state.origin?.point,
                destination = state.destination?.point,
                pendingPoint = state.pendingPoint,
                routes = state.routes,
                selectedRouteId = state.selectedRouteId,
                selectionEnabled = state.pointSelectionMode != null,
                bottomOverlayClearance = bottomOverlayClearance,
                colors = RouteMapColors(
                    selectedRoute = mapColors.selectedRoute,
                    selectedRouteOutline = mapColors.routeCasing,
                    alternativeRoute = mapColors.alternativeRoute,
                    originMarker = mapColors.originMarker,
                    destinationMarker = mapColors.destinationMarker,
                    pendingMarker = mapColors.pendingMarker,
                    markerStroke = MaterialTheme.colorScheme.surface,
                ),
                floodSnapshot = state.floodHazardSnapshot,
                isFloodLayerVisible = state.isFloodLayerVisible,
                sensorDetail = state.sensorDetail,
                onMapTap = {
                    viewModel.onAction(MapRouteAction.MapPointTapped(it))
                },
                onFloodHazardSelected = { id ->
                    viewModel.onAction(MapRouteAction.FloodHazardSelected(id))
                },
                onSensorSelected = { nodeId ->
                    viewModel.onAction(MapRouteAction.SensorMarkerSelected(nodeId))
                },
                onViewportSettled = { bounds ->
                    viewModel.onAction(MapRouteAction.MapViewportSettled(bounds))
                },
                onMapError = {
                    scope.launch {
                        snackbarHostState.showSnackbar(mapUnavailableMessage)
                    }
                },
                modifier = mapModifier.semanticsMap(mapDescription),
            )
        },
        modifier = modifier,
    )

    if (state.isPermissionRationaleVisible) {
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(MapRouteAction.PermissionRationaleDismissed)
            },
            title = { Text(stringResource(R.string.permission_rationale_title)) },
            text = {
                Text(
                    stringResource(
                        if (state.isNavigationPermissionRequest) {
                            R.string.navigation_permission_rationale_body
                        } else {
                            R.string.permission_rationale_body
                        },
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onAction(MapRouteAction.PermissionRationaleAccepted)
                    },
                ) {
                    Text(stringResource(R.string.permission_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.onAction(MapRouteAction.PermissionRationaleDismissed)
                    },
                ) {
                    Text(stringResource(R.string.permission_not_now))
                }
            },
        )
    }

    if (
        state.locationPermissionState == LocationPermissionState.PERMANENTLY_DENIED &&
        !permanentDialogDismissed
    ) {
        AlertDialog(
            onDismissRequest = { permanentDialogDismissed = true },
            title = {
                Text(stringResource(R.string.permission_permanently_denied_title))
            },
            text = {
                Text(stringResource(R.string.permission_permanently_denied_body))
            },
            confirmButton = {
                Button(
                    onClick = {
                        permanentDialogDismissed = true
                        viewModel.onAction(MapRouteAction.CurrentLocationClicked)
                    },
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { permanentDialogDismissed = true }) {
                    Text(stringResource(R.string.continue_demo))
                }
            },
        )
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Modifier.semanticsMap(description: String): Modifier =
    semantics {
        contentDescription = description
    }
