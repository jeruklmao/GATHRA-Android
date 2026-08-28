package opsi.sman35jkt.gathra.feature.map.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardSource

internal enum class FloodRelativeAgeUnit {
    SECONDS,
    MINUTES,
    HOURS,
    DAYS,
}

internal data class FloodRelativeAge(
    val value: Long,
    val unit: FloodRelativeAgeUnit,
)

internal fun floodRelativeAge(
    observedAtEpochMillis: Long,
    nowEpochMillis: Long,
): FloodRelativeAge {
    val ageSeconds = ((nowEpochMillis - observedAtEpochMillis) / 1_000L)
        .coerceAtLeast(0L)
    return when {
        ageSeconds < SECONDS_PER_MINUTE ->
            FloodRelativeAge(ageSeconds, FloodRelativeAgeUnit.SECONDS)
        ageSeconds < SECONDS_PER_HOUR ->
            FloodRelativeAge(ageSeconds / SECONDS_PER_MINUTE, FloodRelativeAgeUnit.MINUTES)
        ageSeconds < SECONDS_PER_DAY ->
            FloodRelativeAge(ageSeconds / SECONDS_PER_HOUR, FloodRelativeAgeUnit.HOURS)
        else -> FloodRelativeAge(ageSeconds / SECONDS_PER_DAY, FloodRelativeAgeUnit.DAYS)
    }
}

internal fun formatFloodObservationTime(
    epochMillis: Long,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String = SimpleDateFormat("d MMM yyyy · HH:mm", locale).apply {
    this.timeZone = timeZone
}.format(Date(epochMillis))

internal fun formatFloodClockTime(
    epochMillis: Long,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String = SimpleDateFormat("HH:mm", locale).apply {
    this.timeZone = timeZone
}.format(Date(epochMillis))

internal fun floodDescriptionStringResource(
    source: FloodHazardSource,
    level: FloodHazardLevel,
): Int = when (source) {
    FloodHazardSource.SENSOR -> when (level) {
        FloodHazardLevel.LOW -> R.string.flood_sensor_low_desc
        FloodHazardLevel.MEDIUM -> R.string.flood_sensor_medium_desc
        FloodHazardLevel.HIGH -> R.string.flood_sensor_high_desc
        FloodHazardLevel.BLOCKED -> R.string.flood_sensor_blocked_desc
        FloodHazardLevel.UNKNOWN -> R.string.flood_sensor_unknown_desc
    }
    FloodHazardSource.SIMULATED -> when (level) {
        FloodHazardLevel.LOW -> R.string.flood_simulated_low_desc
        FloodHazardLevel.MEDIUM -> R.string.flood_simulated_medium_desc
        FloodHazardLevel.HIGH -> R.string.flood_simulated_high_desc
        FloodHazardLevel.BLOCKED -> R.string.flood_simulated_blocked_desc
        FloodHazardLevel.UNKNOWN -> R.string.flood_simulated_unknown_desc
    }
    FloodHazardSource.UNKNOWN -> R.string.flood_unknown_source_desc
}

internal fun floodFreshnessStringResource(freshness: FloodHazardFreshness?): Int =
    when (freshness) {
        FloodHazardFreshness.FRESH -> R.string.flood_freshness_fresh
        FloodHazardFreshness.STALE -> R.string.flood_freshness_stale
        FloodHazardFreshness.NO_TELEMETRY -> R.string.flood_freshness_no_telemetry
        null -> R.string.flood_freshness_unavailable
    }

internal fun floodRoutingEffectStringResource(routingMultiplier: Double): Int = when {
    routingMultiplier == 0.0 -> R.string.flood_routing_excluded
    routingMultiplier == 1.0 -> R.string.flood_routing_no_effect
    else -> R.string.flood_routing_penalty
}

internal fun floodReasonStringResource(reasonCode: String): Int = when (reasonCode) {
    "NO_TELEMETRY" -> R.string.flood_reason_no_telemetry
    "STALE" -> R.string.flood_reason_stale
    "REFERENCE_DISTANCE_MISSING" -> R.string.flood_reason_reference_distance_missing
    "ACCEPTED_DISTANCE_MISSING" -> R.string.flood_reason_accepted_distance_missing
    "FILTER_INVALID" -> R.string.flood_reason_filter_invalid
    "SENSOR_UNHEALTHY" -> R.string.flood_reason_sensor_unhealthy
    "DEPLOYMENT_DISABLED" -> R.string.flood_reason_deployment_disabled
    else -> R.string.flood_reason_generic
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
private const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR
