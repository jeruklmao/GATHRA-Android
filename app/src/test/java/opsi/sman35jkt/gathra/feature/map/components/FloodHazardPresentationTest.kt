package opsi.sman35jkt.gathra.feature.map.components

import java.util.Locale
import java.util.TimeZone
import opsi.sman35jkt.gathra.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FloodHazardPresentationTest {
    @Test
    fun `relative age uses a controlled time for seconds minutes and hours`() {
        val now = 10_000_000L

        assertEquals(
            FloodRelativeAge(42, FloodRelativeAgeUnit.SECONDS),
            floodRelativeAge(now - 42_000L, now),
        )
        assertEquals(
            FloodRelativeAge(2, FloodRelativeAgeUnit.MINUTES),
            floodRelativeAge(now - 120_000L, now),
        )
        assertEquals(
            FloodRelativeAge(3, FloodRelativeAgeUnit.HOURS),
            floodRelativeAge(now - 3 * 60 * 60 * 1_000L, now),
        )
    }

    @Test
    fun `future observation does not produce a negative age`() {
        assertEquals(
            FloodRelativeAge(0, FloodRelativeAgeUnit.SECONDS),
            floodRelativeAge(observedAtEpochMillis = 2_000L, nowEpochMillis = 1_000L),
        )
    }

    @Test
    fun `absolute time formatting honors the supplied locale and timezone`() {
        assertEquals(
            "28 Aug 2026 · 16:44",
            formatFloodObservationTime(
                epochMillis = 1_787_935_454_836L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC"),
            ),
        )
    }

    @Test
    fun `all current sensor reasons map to human phrases`() {
        val expected = mapOf(
            "NO_TELEMETRY" to R.string.flood_reason_no_telemetry,
            "STALE" to R.string.flood_reason_stale,
            "REFERENCE_DISTANCE_MISSING" to R.string.flood_reason_reference_distance_missing,
            "ACCEPTED_DISTANCE_MISSING" to R.string.flood_reason_accepted_distance_missing,
            "FILTER_INVALID" to R.string.flood_reason_filter_invalid,
            "SENSOR_UNHEALTHY" to R.string.flood_reason_sensor_unhealthy,
            "DEPLOYMENT_DISABLED" to R.string.flood_reason_deployment_disabled,
        )

        expected.forEach { (reason, stringResource) ->
            assertEquals(stringResource, floodReasonStringResource(reason))
        }
    }

    @Test
    fun `future reason code maps to a generic phrase without exposing it`() {
        assertEquals(
            R.string.flood_reason_generic,
            floodReasonStringResource("NEW_BACKEND_REASON"),
        )
    }

    @Test
    fun `routing effect comes only from the actual multiplier`() {
        assertEquals(R.string.flood_routing_no_effect, floodRoutingEffectStringResource(1.0))
        assertEquals(R.string.flood_routing_penalty, floodRoutingEffectStringResource(0.35))
        assertEquals(R.string.flood_routing_penalty, floodRoutingEffectStringResource(0.05))
        assertEquals(R.string.flood_routing_excluded, floodRoutingEffectStringResource(0.0))
    }
}
