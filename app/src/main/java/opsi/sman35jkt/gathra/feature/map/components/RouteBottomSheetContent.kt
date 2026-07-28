package opsi.sman35jkt.gathra.feature.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.model.FloodRiskLevel
import opsi.sman35jkt.gathra.core.model.RouteFloodRisk
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.feature.map.MapRouteError
import opsi.sman35jkt.gathra.feature.map.MapRouteTestTags

@Composable
fun RouteBottomSheetContent(
    selectedTravelMode: TravelMode,
    destinationSelected: Boolean,
    isLoading: Boolean,
    routeError: MapRouteError?,
    routes: List<RouteOption>,
    selectedRouteId: String?,
    expanded: Boolean,
    onTravelModeSelected: (TravelMode) -> Unit,
    onRouteSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedRoute = routes.firstOrNull { it.id == selectedRouteId }
        ?: routes.firstOrNull { it.isRecommended }
        ?: routes.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MapRouteTestTags.RouteSummary)
            .then(if (expanded) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .navigationBarsPadding()
            .padding(
                start = 20.dp,
                end = 20.dp,
                bottom = if (expanded) 16.dp else 8.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 12.dp else 6.dp),
    ) {
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.route_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.route_data_badge),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        TravelModeSelector(
            selectedMode = selectedTravelMode,
            onModeSelected = onTravelModeSelected,
        )

        when {
            isLoading -> LoadingRouteContent()
            routeError != null -> ErrorRouteContent(
                error = routeError,
                onRetry = onRetry,
            )
            selectedRoute != null -> ReadyRouteContent(
                selectedRoute = selectedRoute,
                routes = routes,
                selectedRouteId = selectedRouteId,
                expanded = expanded,
                onRouteSelected = onRouteSelected,
            )
            else -> EmptyRouteContent(destinationSelected = destinationSelected)
        }

        Button(
            onClick = onPreview,
            enabled = selectedRoute != null && !isLoading && routeError == null,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (expanded) 52.dp else 48.dp)
                .testTag(MapRouteTestTags.PreviewButton),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.start_navigation))
        }
    }
}

@Composable
private fun EmptyRouteContent(destinationSelected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Route,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.route_empty_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (destinationSelected) {
                Text(
                    text = stringResource(R.string.route_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.route_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadingRouteContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.route_loading),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.route_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorRouteContent(
    error: MapRouteError,
    onRetry: () -> Unit,
) {
    val title = when (error) {
        MapRouteError.ROUTE_OFFLINE -> R.string.route_offline_title
        MapRouteError.ROUTE_TIMEOUT -> R.string.route_timeout_title
        MapRouteError.ROUTE_NOT_FOUND -> R.string.route_not_found_title
        MapRouteError.ROUTE_INVALID_RESPONSE -> R.string.route_invalid_response_title
        MapRouteError.ROUTE_SERVICE_UNAVAILABLE -> R.string.route_service_unavailable_title
        else -> R.string.route_error_title
    }
    val body = when (error) {
        MapRouteError.ROUTE_OFFLINE -> R.string.route_offline_body
        MapRouteError.ROUTE_TIMEOUT -> R.string.route_timeout_body
        MapRouteError.ROUTE_NOT_FOUND -> R.string.route_not_found_body
        MapRouteError.ROUTE_INVALID_RESPONSE -> R.string.route_invalid_response_body
        MapRouteError.ROUTE_SERVICE_UNAVAILABLE -> R.string.route_service_unavailable_body
        else -> R.string.route_error_body
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun ReadyRouteContent(
    selectedRoute: RouteOption,
    routes: List<RouteOption>,
    selectedRouteId: String?,
    expanded: Boolean,
    onRouteSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.eta_minutes,
                    selectedRoute.summary.etaMinutes,
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(
                    R.string.distance_kilometers,
                    selectedRoute.summary.distanceMeters / 1_000f,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            FloodRiskBadge(risk = selectedRoute.risk)
        }
        val floodExplanationRes = when (selectedRoute.risk?.level) {
            FloodRiskLevel.LOW -> R.string.flood_risk_explanation_low
            FloodRiskLevel.MEDIUM -> R.string.flood_risk_explanation_medium
            FloodRiskLevel.HIGH -> R.string.flood_risk_explanation_high
            FloodRiskLevel.BLOCKED -> R.string.flood_risk_explanation_blocked
            else -> if (selectedRoute.isRecommended) {
                R.string.fastest_route_explanation
            } else {
                R.string.selected_route_explanation
            }
        }
        Text(
            text = stringResource(floodExplanationRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded) {
            Spacer(modifier = Modifier.height(2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val actualSelectedRouteId = selectedRouteId ?: selectedRoute.id
                routes.forEach { route ->
                    val isSelected = route.id == actualSelectedRouteId
                    RouteChoiceRow(
                        route = route,
                        selected = isSelected,
                        label = stringResource(
                            when {
                                isSelected -> R.string.selected_route_label
                                route.isRecommended -> R.string.recommended_route_label
                                else -> R.string.alternative_route_label
                            },
                        ),
                        onClick = { onRouteSelected(route.id) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.flood_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun RouteChoiceRow(
    route: RouteOption,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val accessibility = stringResource(
        R.string.route_option_accessibility,
        label,
        route.summary.etaMinutes,
        route.summary.distanceMeters / 1_000f,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(16.dp),
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .semantics { contentDescription = accessibility }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.AltRoute,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            },
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                FloodRiskBadge(risk = route.risk)
            }
            Text(
                text = stringResource(
                    R.string.distance_kilometers,
                    route.summary.distanceMeters / 1_000f,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = stringResource(
                R.string.eta_minutes,
                route.summary.etaMinutes,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun FloodRiskBadge(
    risk: RouteFloodRisk?,
    modifier: Modifier = Modifier,
) {
    val (labelRes, containerColor, contentColor) = when (risk?.level) {
        FloodRiskLevel.LOW -> Triple(
            R.string.flood_risk_low,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        FloodRiskLevel.MEDIUM -> Triple(
            R.string.flood_risk_medium,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        FloodRiskLevel.HIGH -> Triple(
            R.string.flood_risk_high,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        FloodRiskLevel.BLOCKED -> Triple(
            R.string.flood_risk_blocked,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
        )
        FloodRiskLevel.UNKNOWN -> Triple(
            R.string.flood_risk_unknown,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FloodRiskLevel.NOT_EVALUATED, null -> Triple(
            R.string.flood_risk_not_evaluated,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
