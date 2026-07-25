package opsi.sman35jkt.gathra.feature.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.feature.map.MapRouteTestTags
import androidx.compose.ui.platform.testTag

@Composable
fun RouteInputCard(
    originValue: String,
    destinationValue: String,
    selectingOrigin: Boolean,
    selectingDestination: Boolean,
    swapEnabled: Boolean,
    onOriginClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MapRouteTestTags.RouteInputCard),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.brand_route_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 40.dp, bottom = 4.dp),
                )
                RoutePointRow(
                    icon = Icons.Rounded.TripOrigin,
                    title = stringResource(R.string.origin_label),
                    value = originValue,
                    selected = selectingOrigin,
                    accessibilityLabel = stringResource(
                        R.string.origin_accessibility,
                        originValue,
                    ),
                    onClick = onOriginClick,
                    testTag = MapRouteTestTags.OriginField,
                    connectsUp = false,
                    connectsDown = true,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 40.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                )
                RoutePointRow(
                    icon = Icons.Rounded.LocationOn,
                    title = stringResource(R.string.destination_label),
                    value = destinationValue,
                    selected = selectingDestination,
                    accessibilityLabel = stringResource(
                        R.string.destination_accessibility,
                        destinationValue,
                    ),
                    onClick = onDestinationClick,
                    testTag = MapRouteTestTags.DestinationField,
                    connectsUp = true,
                    connectsDown = false,
                )
            }
            IconButton(
                onClick = onSwapClick,
                enabled = swapEnabled,
                modifier = Modifier
                    .size(56.dp)
                    .testTag(MapRouteTestTags.SwapButton),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = stringResource(R.string.swap_points),
                )
            }
        }
    }
}

@Composable
private fun RoutePointRow(
    icon: ImageVector,
    title: String,
    value: String,
    selected: Boolean,
    accessibilityLabel: String,
    onClick: () -> Unit,
    testTag: String,
    connectsUp: Boolean,
    connectsDown: Boolean,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = accessibilityLabel
                role = Role.Button
            }
            .testTag(testTag)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (connectsUp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-11).dp)
                        .width(2.dp)
                        .height(26.dp)
                        .background(
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(1.dp),
                        ),
                )
            }
            if (connectsDown) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 11.dp)
                        .width(2.dp)
                        .height(26.dp)
                        .background(
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(1.dp),
                        ),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
