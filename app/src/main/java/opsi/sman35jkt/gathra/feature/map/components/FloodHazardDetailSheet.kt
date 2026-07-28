package opsi.sman35jkt.gathra.feature.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import kotlin.math.roundToInt

@Composable
fun FloodHazardDetailSheet(
    hazard: FloodHazardPolygon,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.flood_hazard_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloodRiskBadge(level = hazard.level.toRouteFloodRiskLevel())
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = hazard.levelText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            hazard.description?.let { desc ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }

            hazard.confidence?.let { conf ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.flood_hazard_confidence_format,
                        (conf * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when (hazard.source) {
                    FloodHazardSource.SIMULATED -> stringResource(R.string.flood_source_simulated)
                    FloodHazardSource.SENSOR -> stringResource(R.string.flood_source_sensor)
                    FloodHazardSource.UNKNOWN -> stringResource(R.string.flood_source_unknown)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.flood_disclaimer_coverage_incomplete),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = stringResource(R.string.action_dismiss))
            }
        }
    }
}

@Composable
private fun FloodHazardPolygon.levelText(): String = when (level) {
    FloodHazardLevel.LOW -> stringResource(R.string.flood_risk_low_desc)
    FloodHazardLevel.MEDIUM -> stringResource(R.string.flood_risk_medium_desc)
    FloodHazardLevel.HIGH -> stringResource(R.string.flood_risk_high_desc)
    FloodHazardLevel.BLOCKED -> stringResource(R.string.flood_risk_blocked_desc)
    FloodHazardLevel.UNKNOWN -> stringResource(R.string.flood_risk_unknown_desc)
}

private fun FloodHazardLevel.toRouteFloodRiskLevel() = when (this) {
    FloodHazardLevel.LOW -> opsi.sman35jkt.gathra.core.model.FloodRiskLevel.LOW
    FloodHazardLevel.MEDIUM -> opsi.sman35jkt.gathra.core.model.FloodRiskLevel.MEDIUM
    FloodHazardLevel.HIGH -> opsi.sman35jkt.gathra.core.model.FloodRiskLevel.HIGH
    FloodHazardLevel.BLOCKED -> opsi.sman35jkt.gathra.core.model.FloodRiskLevel.BLOCKED
    FloodHazardLevel.UNKNOWN -> opsi.sman35jkt.gathra.core.model.FloodRiskLevel.LOW
}
