package opsi.sman35jkt.gathra.feature.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import kotlin.math.roundToInt

@Composable
fun FloodHazardDetailSheet(
    hazard: FloodHazardPolygon,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    nowEpochMillis: Long = System.currentTimeMillis(),
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp)
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(
                        floodDescriptionStringResource(hazard.source, hazard.level),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                hazard.confidence?.let { confidence ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.flood_hazard_confidence_format,
                            (confidence * 100).roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }

                DetailDivider()
                FloodSourceContent(hazard)

                if (hazard.source == FloodHazardSource.SENSOR || hazard.freshness != null) {
                    DetailField(
                        label = stringResource(R.string.flood_detail_freshness_label),
                        value = stringResource(
                            floodFreshnessStringResource(hazard.freshness),
                        ),
                    )
                }

                FloodObservationContent(hazard, nowEpochMillis)

                hazard.validUntilEpochMillis?.let { validUntil ->
                    val validityText = when (hazard.freshness) {
                        FloodHazardFreshness.STALE -> stringResource(
                            R.string.flood_expired_at_format,
                            formatFloodClockTime(validUntil),
                        )
                        FloodHazardFreshness.NO_TELEMETRY -> null
                        FloodHazardFreshness.FRESH,
                        null,
                        -> stringResource(
                            R.string.flood_valid_until_format,
                            formatFloodClockTime(validUntil),
                        )
                    }
                    validityText?.let { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                DetailField(
                    label = stringResource(R.string.flood_detail_routing_label),
                    value = stringResource(
                        floodRoutingEffectStringResource(hazard.routingMultiplier),
                    ),
                )

                val reasonResources = hazard.reasonCodes
                    .map(::floodReasonStringResource)
                    .distinct()
                    .ifEmpty {
                        if (
                            hazard.source == FloodHazardSource.SENSOR &&
                            hazard.level == FloodHazardLevel.UNKNOWN
                        ) {
                            listOf(R.string.flood_reason_generic)
                        } else {
                            emptyList()
                        }
                    }
                if (reasonResources.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.flood_detail_explanation_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    reasonResources.forEachIndexed { index, reasonResource ->
                        if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(reasonResource),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = stringResource(
                        when (hazard.source) {
                            FloodHazardSource.SENSOR ->
                                R.string.flood_disclaimer_sensor_coverage
                            FloodHazardSource.SIMULATED ->
                                R.string.flood_disclaimer_simulated_coverage
                            FloodHazardSource.UNKNOWN ->
                                R.string.flood_disclaimer_coverage_incomplete
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
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
private fun FloodSourceContent(hazard: FloodHazardPolygon) {
    Text(
        text = stringResource(R.string.flood_detail_source_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    )
    Spacer(modifier = Modifier.height(2.dp))
    val sourceText = when (hazard.source) {
        FloodHazardSource.SIMULATED -> stringResource(R.string.flood_source_simulated)
        FloodHazardSource.SENSOR -> if (hazard.sourceNodeIds.size > 1) {
            pluralStringResource(
                R.plurals.flood_source_sensor_count,
                hazard.sourceNodeIds.size,
                hazard.sourceNodeIds.size,
            )
        } else {
            stringResource(R.string.flood_source_sensor)
        }
        FloodHazardSource.UNKNOWN -> stringResource(R.string.flood_source_unknown)
    }
    Text(
        text = sourceText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (hazard.source == FloodHazardSource.SENSOR && hazard.sourceNodeIds.size == 1) {
        Text(
            text = hazard.sourceNodeIds.single(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun FloodObservationContent(
    hazard: FloodHazardPolygon,
    nowEpochMillis: Long,
) {
    val observedAt = hazard.observedAtEpochMillis
    if (observedAt == null) {
        Text(
            text = stringResource(R.string.flood_observed_at_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        return
    }

    val relativeAge = floodRelativeAge(observedAt, nowEpochMillis)
    val relativeText = when (relativeAge.unit) {
        FloodRelativeAgeUnit.SECONDS -> pluralStringResource(
            R.plurals.flood_observed_seconds_ago,
            relativeAge.value.toInt(),
            relativeAge.value,
        )
        FloodRelativeAgeUnit.MINUTES -> pluralStringResource(
            R.plurals.flood_observed_minutes_ago,
            relativeAge.value.toInt(),
            relativeAge.value,
        )
        FloodRelativeAgeUnit.HOURS -> pluralStringResource(
            R.plurals.flood_observed_hours_ago,
            relativeAge.value.toInt(),
            relativeAge.value,
        )
        FloodRelativeAgeUnit.DAYS -> pluralStringResource(
            R.plurals.flood_observed_days_ago,
            relativeAge.value.toInt(),
            relativeAge.value,
        )
    }
    Text(
        text = relativeText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = formatFloodObservationTime(observedAt),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    )
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun DetailField(label: String, value: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun DetailDivider() {
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(10.dp))
}

private fun FloodHazardLevel.toRouteFloodRiskLevel() = when (this) {
    FloodHazardLevel.LOW -> opsi.sman35jkt.gathra.core.model.FloodRiskLevel.LOW
    FloodHazardLevel.MEDIUM -> opsi.sman35jkt.gathra.core.model.FloodRiskLevel.MEDIUM
    FloodHazardLevel.HIGH -> opsi.sman35jkt.gathra.core.model.FloodRiskLevel.HIGH
    FloodHazardLevel.BLOCKED -> opsi.sman35jkt.gathra.core.model.FloodRiskLevel.BLOCKED
    FloodHazardLevel.UNKNOWN -> opsi.sman35jkt.gathra.core.model.FloodRiskLevel.UNKNOWN
}
