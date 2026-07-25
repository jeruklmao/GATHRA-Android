package opsi.sman35jkt.gathra.feature.map.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.feature.map.MapRouteTestTags

@Composable
fun CurrentLocationButton(
    isLocating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        if (isLocating) {
            R.string.current_location_locating
        } else {
            R.string.current_location_button
        },
    )
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .testTag(MapRouteTestTags.CurrentLocationButton)
            .semantics { contentDescription = description },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        if (isLocating) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MyLocation,
                contentDescription = null,
            )
        }
    }
}
