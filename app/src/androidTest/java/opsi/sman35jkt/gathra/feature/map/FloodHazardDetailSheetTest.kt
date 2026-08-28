package opsi.sman35jkt.gathra.feature.map

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import opsi.sman35jkt.gathra.core.model.FloodHazardFreshness
import opsi.sman35jkt.gathra.core.model.FloodHazardLevel
import opsi.sman35jkt.gathra.core.model.FloodHazardPolygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSource
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.feature.map.components.FloodHazardDetailSheet
import opsi.sman35jkt.gathra.ui.theme.GATHRATheme
import org.junit.Rule
import org.junit.Test

class FloodHazardDetailSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sensorLowFreshShowsCurrentSensorAndActualNoPenaltyWording() {
        setHazard(
            sensorHazard(
                level = FloodHazardLevel.LOW,
                freshness = FloodHazardFreshness.FRESH,
                routingMultiplier = 1.0,
            ),
        )

        composeRule.onNodeWithText("Risiko banjir rendah").assertIsDisplayed()
        composeRule.onNodeWithText("Data terbaru").assertExists()
        composeRule.onNodeWithText("Sensor GATHRA").assertExists()
        composeRule.onNodeWithText("GTH-10003BD4BCFC").assertExists()
        composeRule.onNodeWithText("Area ini tidak memengaruhi pemilihan rute.").assertExists()
        composeRule.onNodeWithText(
            "Area berwarna menunjukkan cakupan sensor GATHRA.",
            substring = true,
        ).assertExists()
        composeRule.onNodeWithText("Tutup").assertIsDisplayed()
        assertNoSimulationLeak()
    }

    @Test
    fun sensorUnknownFreshShowsHealthReasonAndNeverLooksLow() {
        setHazard(
            sensorHazard(
                level = FloodHazardLevel.UNKNOWN,
                freshness = FloodHazardFreshness.FRESH,
                routingMultiplier = 1.0,
                reasonCodes = listOf("SENSOR_UNHEALTHY"),
            ),
        )

        composeRule.onNodeWithText("Risiko belum diketahui").assertIsDisplayed()
        composeRule.onNodeWithText("Data terbaru").assertExists()
        composeRule.onNodeWithText(
            "Sensor sedang mengalami masalah pada pembacaan ketinggian air.",
        ).assertExists()
        composeRule.onAllNodesWithText("Risiko banjir rendah").assertCountEquals(0)
        composeRule.onAllNodesWithText("SENSOR_UNHEALTHY", substring = true)
            .assertCountEquals(0)
        assertNoSimulationLeak()
    }

    @Test
    fun staleSensorShowsOldDataAndUnknownCondition() {
        setHazard(
            sensorHazard(
                level = FloodHazardLevel.UNKNOWN,
                freshness = FloodHazardFreshness.STALE,
                routingMultiplier = 1.0,
                reasonCodes = listOf("STALE"),
            ),
        )

        composeRule.onNodeWithText("Risiko belum diketahui").assertIsDisplayed()
        composeRule.onNodeWithText("Data sudah lama").assertExists()
        composeRule.onNodeWithText(
            "Pengukuran terakhir sudah terlalu lama untuk menentukan kondisi saat ini.",
        ).assertExists()
        composeRule.onNodeWithText("Masa berlaku data berakhir", substring = true)
            .assertExists()
        assertNoSimulationLeak()
    }

    @Test
    fun noTelemetrySensorAcceptsMissingTimesWithoutInventingAnAge() {
        setHazard(
            sensorHazard(
                level = FloodHazardLevel.UNKNOWN,
                freshness = FloodHazardFreshness.NO_TELEMETRY,
                routingMultiplier = 1.0,
                reasonCodes = listOf("NO_TELEMETRY"),
                observedAtEpochMillis = null,
                validUntilEpochMillis = null,
            ),
        )

        composeRule.onNodeWithText("Belum ada data sensor").assertExists()
        composeRule.onNodeWithText("Belum ada waktu pengukuran").assertExists()
        composeRule.onNodeWithText("Belum ada pengukuran dari sensor.").assertExists()
        composeRule.onAllNodesWithText("0 menit lalu", substring = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("NO_TELEMETRY", substring = true)
            .assertCountEquals(0)
        assertNoSimulationLeak()
    }

    @Test
    fun simulatedFallbackKeepsSimulationWordingWithoutFakeSensorIdentity() {
        setHazard(
            sensorHazard(
                level = FloodHazardLevel.LOW,
                freshness = null,
                routingMultiplier = 1.0,
                source = FloodHazardSource.SIMULATED,
                sourceNodeIds = emptyList(),
            ),
        )

        composeRule.onNodeWithText("Simulasi menunjukkan risiko banjir rendah", substring = true)
            .assertExists()
        composeRule.onNodeWithText("Data banjir simulasi").assertExists()
        composeRule.onNodeWithText("Area berwarna menunjukkan cakupan data simulasi", substring = true)
            .assertExists()
        composeRule.onAllNodesWithText("GTH-10003BD4BCFC", substring = true)
            .assertCountEquals(0)
    }

    private fun setHazard(hazard: FloodHazardPolygon) {
        composeRule.setContent {
            GATHRATheme {
                FloodHazardDetailSheet(
                    hazard = hazard,
                    onDismiss = {},
                    nowEpochMillis = NOW_EPOCH_MILLIS,
                )
            }
        }
    }

    private fun assertNoSimulationLeak() {
        composeRule.onAllNodesWithText("simulasi", substring = true, ignoreCase = true)
            .assertCountEquals(0)
    }

    private fun sensorHazard(
        level: FloodHazardLevel,
        freshness: FloodHazardFreshness?,
        routingMultiplier: Double,
        reasonCodes: List<String> = emptyList(),
        observedAtEpochMillis: Long? = OBSERVED_AT_EPOCH_MILLIS,
        validUntilEpochMillis: Long? = VALID_UNTIL_EPOCH_MILLIS,
        source: FloodHazardSource = FloodHazardSource.SENSOR,
        sourceNodeIds: List<String> = listOf("GTH-10003BD4BCFC"),
    ) = FloodHazardPolygon(
        id = "sensor_GTH-10003BD4BCFC",
        level = level,
        rings = listOf(
            listOf(
                GeoPoint(-6.2, 106.8),
                GeoPoint(-6.2, 106.81),
                GeoPoint(-6.19, 106.81),
                GeoPoint(-6.2, 106.8),
            ),
        ),
        confidence = 1.0,
        description = "Backend provider description",
        observedAtEpochMillis = observedAtEpochMillis,
        validUntilEpochMillis = validUntilEpochMillis,
        source = source,
        sourceNodeIds = sourceNodeIds,
        routingMultiplier = routingMultiplier,
        reasonCodes = reasonCodes,
        freshness = freshness,
    )

    private companion object {
        const val OBSERVED_AT_EPOCH_MILLIS = 1_787_935_454_836L
        const val NOW_EPOCH_MILLIS = OBSERVED_AT_EPOCH_MILLIS + 2 * 60_000L
        const val VALID_UNTIL_EPOCH_MILLIS = OBSERVED_AT_EPOCH_MILLIS + 30 * 60_000L
    }
}
