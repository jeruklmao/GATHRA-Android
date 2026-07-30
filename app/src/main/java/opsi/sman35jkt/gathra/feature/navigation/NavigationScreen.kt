package opsi.sman35jkt.gathra.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.map.NavigationCameraMode
import opsi.sman35jkt.gathra.core.model.ManeuverModifier
import opsi.sman35jkt.gathra.core.model.RouteStep
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.core.navigation.navigationInstruction
import opsi.sman35jkt.gathra.domain.navigation.NavigationSession
import opsi.sman35jkt.gathra.domain.navigation.NavigationFloodRouteStatus
import opsi.sman35jkt.gathra.domain.navigation.NavigationStatus

@Composable
fun NavigationScreen(
    state: NavigationUiState,
    onAction: (NavigationAction) -> Unit,
    mapContent: @Composable (Modifier, Dp, Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.session
    if (session == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val largeText = density.fontScale >= LARGE_TEXT_FONT_SCALE
        val compact = maxHeight < 700.dp || largeText
        val fractionalBottomPanelMaxHeight = maxHeight * when {
            largeText -> LARGE_TEXT_PANEL_HEIGHT_FRACTION
            compact -> COMPACT_PANEL_HEIGHT_FRACTION
            else -> REGULAR_PANEL_HEIGHT_FRACTION
        }
        var topBottomPx by remember { mutableIntStateOf(0) }
        var bottomHeightPx by remember { mutableIntStateOf(0) }
        val topClearance = with(density) { topBottomPx.toDp() }
        val bottomClearance = with(density) { bottomHeightPx.toDp() }
        val availableBelowTopCard = (
            maxHeight - topClearance - MINIMUM_MAP_GAP
            ).coerceAtLeast(1.dp)
        val bottomPanelMaxHeight = minOf(
            fractionalBottomPanelMaxHeight,
            availableBelowTopCard,
        )

        mapContent(Modifier.fillMaxSize(), topClearance, bottomClearance)

        NavigationInstructionCard(
            state = state,
            compact = compact,
            largeText = largeText,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = 10.dp)
                .onGloballyPositioned { coordinates ->
                    topBottomPx = coordinates.boundsInParent().bottom.roundToInt()
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(
                    end = 16.dp,
                    top = topClearance,
                    bottom = bottomClearance,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            LocationQualityChip(state.locationQuality)
            if (state.cameraMode == NavigationCameraMode.FREE) {
                NavigationMapButton(
                    icon = Icons.Rounded.MyLocation,
                    description = stringResource(R.string.navigation_recenter),
                    onClick = { onAction(NavigationAction.RecenterClicked) },
                )
            }
            NavigationMapButton(
                icon = if (state.cameraMode == NavigationCameraMode.OVERVIEW) {
                    Icons.Rounded.Navigation
                } else {
                    Icons.Rounded.Map
                },
                description = stringResource(
                    if (state.cameraMode == NavigationCameraMode.OVERVIEW) {
                        R.string.navigation_follow
                    } else {
                        R.string.navigation_overview
                    },
                ),
                onClick = { onAction(NavigationAction.OverviewClicked) },
            )
        }

        NavigationBottomPanel(
            session = session,
            state = state,
            compact = compact,
            largeText = largeText,
            maxPanelHeight = bottomPanelMaxHeight,
            onAction = onAction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomHeightPx = it.height },
        )
    }

    if (state.stopConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { onAction(NavigationAction.StopDismissed) },
            title = { Text(stringResource(R.string.navigation_stop_title)) },
            text = { Text(stringResource(R.string.navigation_stop_body)) },
            confirmButton = {
                TextButton(onClick = { onAction(NavigationAction.StopConfirmed) }) {
                    Text(
                        stringResource(R.string.navigation_stop),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                Button(onClick = { onAction(NavigationAction.StopDismissed) }) {
                    Text(stringResource(R.string.navigation_continue))
                }
            },
        )
    }
}

@Composable
private fun NavigationInstructionCard(
    state: NavigationUiState,
    compact: Boolean,
    largeText: Boolean,
    modifier: Modifier = Modifier,
) {
    val session = requireNotNull(state.session)
    val context = LocalContext.current
    val nextStep = state.nextStep
    val progress = session.progress
    val statusText = statusText(session)
    val instruction = statusText?.second ?: nextStep?.let(context::navigationInstruction)
        ?: stringResource(R.string.navigation_preparing_body)
    val distance = when {
        statusText != null -> statusText.first
        progress == null -> stringResource(R.string.navigation_preparing)
        else -> formatDistance(progress.distanceToNextManoeuvreMeters)
    }
    val currentRoad = state.currentStep?.streetName
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.navigation_unnamed_road)
    val containerColor = when (session.status) {
        NavigationStatus.OFF_ROUTE,
        NavigationStatus.RECALCULATING,
        -> MaterialTheme.colorScheme.tertiaryContainer
        NavigationStatus.GPS_UNAVAILABLE,
        NavigationStatus.ERROR,
        -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }

    ElevatedCard(
        modifier = modifier
            .testTag(NavigationTestTags.InstructionCard)
            .semantics {
                liveRegion = if (
                    session.status in setOf(
                        NavigationStatus.OFF_ROUTE,
                        NavigationStatus.GPS_UNAVAILABLE,
                        NavigationStatus.ARRIVED,
                    )
                ) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
                contentDescription = "$distance. $instruction"
            },
        shape = RoundedCornerShape(24.dp),
    ) {
        Surface(color = containerColor) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (compact) 14.dp else 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ManoeuvreGlyph(
                    step = nextStep,
                    status = session.status,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (!compact) {
                        Text(
                            text = stringResource(R.string.navigation_brand),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = distance,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = instruction,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (largeText) 4 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!compact && session.status == NavigationStatus.NAVIGATING) {
                        Text(
                            text = stringResource(
                                R.string.navigation_current_road,
                                currentRoad,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun statusText(session: NavigationSession): Pair<String, String>? =
    when (session.status) {
        NavigationStatus.PREPARING -> stringResource(R.string.navigation_preparing) to
            stringResource(R.string.navigation_preparing_body)
        NavigationStatus.RECALCULATING -> if (
            session.progress?.isOffRoute == true
        ) {
            stringResource(R.string.navigation_off_route) to
                stringResource(R.string.navigation_recalculating_body)
        } else {
            stringResource(R.string.navigation_recalculating) to
                stringResource(R.string.navigation_recalculating_body)
        }
        NavigationStatus.OFF_ROUTE -> stringResource(R.string.navigation_off_route) to
            stringResource(R.string.navigation_off_route_body)
        NavigationStatus.GPS_UNAVAILABLE ->
            stringResource(R.string.navigation_gps_unavailable) to
                stringResource(R.string.navigation_gps_unavailable_body)
        NavigationStatus.ERROR -> stringResource(R.string.navigation_error) to
            stringResource(R.string.navigation_error_body)
        NavigationStatus.ARRIVED -> stringResource(R.string.navigation_arrived) to
            stringResource(R.string.navigation_arrived_body)
        else -> null
    }

@Composable
private fun ManoeuvreGlyph(
    step: RouteStep?,
    status: NavigationStatus,
) {
    val icon = when (status) {
        NavigationStatus.OFF_ROUTE,
        NavigationStatus.GPS_UNAVAILABLE,
        -> Icons.Rounded.WarningAmber
        NavigationStatus.RECALCULATING -> Icons.Rounded.Refresh
        NavigationStatus.ERROR -> Icons.Rounded.ErrorOutline
        NavigationStatus.ARRIVED -> Icons.Rounded.Place
        else -> Icons.Rounded.Navigation
    }
    val rotation = if (icon == Icons.Rounded.Navigation) {
        step?.maneuver?.modifier?.rotationDegrees() ?: 0f
    } else {
        0f
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(14.dp)
                .size(32.dp)
                .rotate(rotation),
        )
    }
}

private fun ManeuverModifier.rotationDegrees(): Float = when (this) {
    ManeuverModifier.STRAIGHT,
    ManeuverModifier.NONE,
    -> 0f
    ManeuverModifier.SLIGHT_LEFT -> -35f
    ManeuverModifier.LEFT -> -90f
    ManeuverModifier.SHARP_LEFT -> -135f
    ManeuverModifier.SLIGHT_RIGHT -> 35f
    ManeuverModifier.RIGHT -> 90f
    ManeuverModifier.SHARP_RIGHT -> 135f
    ManeuverModifier.U_TURN -> 180f
}

@Composable
private fun LocationQualityChip(quality: NavigationLocationQuality) {
    val label = stringResource(
        when (quality) {
            NavigationLocationQuality.GOOD -> R.string.navigation_location_good
            NavigationLocationQuality.APPROXIMATE ->
                R.string.navigation_location_approximate
            NavigationLocationQuality.WEAK -> R.string.navigation_location_weak
            NavigationLocationQuality.UNAVAILABLE ->
                R.string.navigation_location_unavailable
        },
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.semantics {
            stateDescription = label
            contentDescription = label
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (quality == NavigationLocationQuality.GOOD) {
                    Icons.Rounded.MyLocation
                } else {
                    Icons.Rounded.WarningAmber
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun NavigationMapButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun NavigationBottomPanel(
    session: NavigationSession,
    state: NavigationUiState,
    compact: Boolean,
    largeText: Boolean,
    maxPanelHeight: Dp,
    onAction: (NavigationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(max = maxPanelHeight)
            .testTag(NavigationTestTags.BottomPanel),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (compact) 14.dp else 20.dp,
                    vertical = if (compact) 12.dp else 18.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            if (session.status == NavigationStatus.ARRIVED) {
                ArrivalContent(onFinish = { onAction(NavigationAction.FinishClicked) })
                return@Column
            }
            NavigationMetrics(session, compact)
            FloodNavigationStatus(
                session = session,
                onRetry = { onAction(NavigationAction.RetryReroute) },
            )
            if (session.status == NavigationStatus.RECALCULATING) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.navigation_recalculating),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (session.rerouteError != null) {
                OutlinedButton(
                    onClick = { onAction(NavigationAction.RetryReroute) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.navigation_retry_reroute))
                }
            }
            if (state.simulationEnabled) {
                DemoControls(state, onAction)
            }
            NavigationActionButtons(
                session = session,
                stackVertically = largeText,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun FloodNavigationStatus(
    session: NavigationSession,
    onRetry: () -> Unit,
) {
    val message = when (session.floodRouteStatus) {
        NavigationFloodRouteStatus.UPDATING ->
            R.string.navigation_flood_route_updating
        NavigationFloodRouteStatus.STALE ->
            R.string.navigation_flood_route_stale
        NavigationFloodRouteStatus.NOT_EVALUATED ->
            R.string.navigation_flood_route_not_evaluated
        NavigationFloodRouteStatus.SYNCHRONIZED -> return
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null)
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (session.floodRouteStatus == NavigationFloodRouteStatus.STALE) {
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun NavigationActionButtons(
    session: NavigationSession,
    stackVertically: Boolean,
    onAction: (NavigationAction) -> Unit,
) {
    val muteButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        FilledTonalButton(
            onClick = { onAction(NavigationAction.MuteClicked) },
            modifier = buttonModifier.testTag(NavigationTestTags.MuteButton),
        ) {
            Icon(
                if (session.muted) {
                    Icons.AutoMirrored.Rounded.VolumeOff
                } else {
                    Icons.AutoMirrored.Rounded.VolumeUp
                },
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (session.muted) {
                        R.string.navigation_unmute
                    } else {
                        R.string.navigation_mute
                    },
                ),
                maxLines = 2,
            )
        }
    }
    val stopButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        OutlinedButton(
            onClick = { onAction(NavigationAction.StopRequested) },
            modifier = buttonModifier.testTag(NavigationTestTags.StopButton),
        ) {
            Icon(Icons.Rounded.StopCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.navigation_stop))
        }
    }

    if (stackVertically) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            muteButton(Modifier.fillMaxWidth())
            stopButton(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            muteButton(Modifier.weight(1f))
            stopButton(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavigationMetrics(
    session: NavigationSession,
    compact: Boolean,
) {
    val progress = session.progress
    val durationSeconds = progress?.remainingDurationSeconds
        ?: session.route.summary.durationSeconds.toLong()
    val distanceMeters = progress?.remainingDistanceMeters
        ?: session.route.summary.distanceMeters.toDouble()
    val etaMinutes = ceil(durationSeconds / 60.0).toInt().coerceAtLeast(0)
    val arrivalTime = remember(durationSeconds) {
        SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(
            Date(System.currentTimeMillis() + durationSeconds * 1_000L),
        )
    }
    val metrics = listOf(
        Triple(
            R.string.navigation_remaining_eta_label,
            stringResource(R.string.navigation_eta_minutes, etaMinutes),
            NavigationTestTags.RemainingEta,
        ),
        Triple(
            R.string.navigation_remaining_distance_label,
            formatDistance(distanceMeters),
            NavigationTestTags.RemainingDistance,
        ),
        Triple(
            R.string.navigation_arrival_label,
            arrivalTime,
            "",
        ),
    )
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            metrics.forEach { (label, value, tag) ->
                NavigationMetric(label, value, tag, Modifier.weight(1f))
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            metrics.forEach { (label, value, tag) ->
                NavigationMetric(label, value, tag, Modifier.weight(1f))
            }
            Icon(
                imageVector = if (session.travelMode == TravelMode.CAR) {
                    Icons.Rounded.DirectionsCar
                } else {
                    Icons.Rounded.TwoWheeler
                },
                contentDescription = stringResource(
                    if (session.travelMode == TravelMode.CAR) {
                        R.string.navigation_mode_car
                    } else {
                        R.string.navigation_mode_motorcycle
                    },
                ),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun NavigationMetric(
    labelResource: Int,
    value: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.then(if (testTag.isBlank()) Modifier else Modifier.testTag(testTag))) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(labelResource),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DemoControls(
    state: NavigationUiState,
    onAction: (NavigationAction) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.navigation_simulation_badge),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { onAction(NavigationAction.ToggleSimulationPause) },
                ) {
                    Icon(
                        if (state.simulationPaused) {
                            Icons.Rounded.PlayArrow
                        } else {
                            Icons.Rounded.Pause
                        },
                        contentDescription = null,
                    )
                    Text(
                        stringResource(
                            if (state.simulationPaused) {
                                R.string.navigation_simulation_resume
                            } else {
                                R.string.navigation_simulation_pause
                            },
                        ),
                    )
                }
                listOf(1.0, 2.0, 4.0).forEach { speed ->
                    FilterChip(
                        selected = state.simulationSpeed == speed,
                        onClick = {
                            onAction(NavigationAction.SimulationSpeedSelected(speed))
                        },
                        label = {
                            Text(
                                stringResource(
                                    R.string.navigation_simulation_speed_short,
                                    speed.toInt(),
                                ),
                            )
                        },
                    )
                }
            }
            TextButton(
                onClick = { onAction(NavigationAction.SimulateOffRoute) },
            ) {
                Text(stringResource(R.string.navigation_simulation_off_route))
            }
        }
    }
}

@Composable
private fun ArrivalContent(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NavigationTestTags.ArrivalPanel)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(42.dp),
        )
        Text(
            stringResource(R.string.navigation_arrived),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(stringResource(R.string.navigation_arrived_body))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.navigation_finish))
        }
    }
}

@Composable
private fun formatDistance(distanceMeters: Double): String =
    if (distanceMeters < 1_000.0) {
        stringResource(
            R.string.navigation_distance_meters,
            distanceMeters.toInt().coerceAtLeast(0),
        )
    } else {
        stringResource(
            R.string.navigation_distance_kilometers,
            distanceMeters / 1_000.0,
        )
    }

private const val LARGE_TEXT_FONT_SCALE = 1.3f
private val MINIMUM_MAP_GAP = 16.dp
private const val LARGE_TEXT_PANEL_HEIGHT_FRACTION = 0.64f
private const val COMPACT_PANEL_HEIGHT_FRACTION = 0.55f
private const val REGULAR_PANEL_HEIGHT_FRACTION = 0.48f
