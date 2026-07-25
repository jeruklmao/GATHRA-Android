package opsi.sman35jkt.gathra.feature.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.feature.map.MapRouteTestTags

@Composable
fun TravelModeSelector(
    selectedMode: TravelMode,
    onModeSelected: (TravelMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TravelModeButton(
            label = stringResource(R.string.travel_mode_car),
            contentDescription = stringResource(R.string.travel_mode_car_description),
            icon = Icons.Rounded.DirectionsCar,
            selected = selectedMode == TravelMode.CAR,
            onClick = { onModeSelected(TravelMode.CAR) },
            testTag = MapRouteTestTags.CarMode,
            modifier = Modifier.weight(1f),
        )
        TravelModeButton(
            label = stringResource(R.string.travel_mode_motorcycle),
            contentDescription = stringResource(R.string.travel_mode_motorcycle_description),
            icon = Icons.Rounded.TwoWheeler,
            selected = selectedMode == TravelMode.MOTORCYCLE,
            onClick = { onModeSelected(TravelMode.MOTORCYCLE) },
            testTag = MapRouteTestTags.MotorcycleMode,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TravelModeButton(
    label: String,
    contentDescription: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .testTag(testTag)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
