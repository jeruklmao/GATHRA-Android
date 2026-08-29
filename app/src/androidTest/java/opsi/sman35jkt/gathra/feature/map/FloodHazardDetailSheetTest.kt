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
import opsi.sman35jkt.gathra.domain.sensor.BackendDeliveryStatus
import opsi.sman35jkt.gathra.domain.sensor.GatewayStatus
import opsi.sman35jkt.gathra.domain.sensor.RadioReceptionStatus
import opsi.sman35jkt.gathra.domain.sensor.SensorCurrentState
import opsi.sman35jkt.gathra.domain.sensor.SensorGatewaySummary
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

    @Test
    fun currentSensorShowsOnlyPhaseTwoPublicInformation() {
        setHazard(
            sensorHazard(FloodHazardLevel.LOW, FloodHazardFreshness.FRESH, 1.0),
            sensor = sensor(),
        )
        for (text in listOf(
            "Ketinggian air", "123 mm", "Jarak permukaan ke sensor", "1602 mm",
            "Lokasi sensor", "-6.235149, 106.720401", "31.2 °C · 72.4% RH",
            "Koneksi Gateway", "Online", "Radio Node → Gateway", "Baik",
            "RSSI -79 dBm · SNR 8.4 dB", "Pengiriman ke server", "Normal",
        )) composeRule.onNodeWithText(text).assertExists()
        for (forbidden in listOf(
            "rawDistance", "Baterai", "filterState", "qualityFlags", "healthFlags",
            "ACK", "SSID", "Grafik", "Riwayat",
        )) composeRule.onAllNodesWithText(forbidden, substring = true, ignoreCase = true)
            .assertCountEquals(0)
        for (forbidden in listOf("IP", "MAC")) {
            composeRule.onAllNodesWithText(forbidden).assertCountEquals(0)
        }
    }

    @Test
    fun GatewayAndRadioUnavailableStatesRemainExplicit() {
        setHazard(
            sensorHazard(FloodHazardLevel.UNKNOWN, FloodHazardFreshness.STALE, 1.0),
            sensor = sensor(
                temperatureC = null,
                humidityPercent = null,
                gateway = SensorGatewaySummary(
                    GatewayStatus.UNAVAILABLE, null, RadioReceptionStatus.UNAVAILABLE,
                    null, null, BackendDeliveryStatus.UNAVAILABLE,
                ),
            ),
        )
        composeRule.onAllNodesWithText("Tidak tersedia").assertCountEquals(4)
        composeRule.onNodeWithText("RSSI — dBm · SNR — dB").assertExists()
    }

    private fun setHazard(hazard: FloodHazardPolygon, sensor: SensorCurrentState? = null) {
        composeRule.setContent {
            GATHRATheme {
                FloodHazardDetailSheet(
                    hazard = hazard,
                    sensor = sensor,
                    onDismiss = {},
                    nowEpochMillis = NOW_EPOCH_MILLIS,
                )
            }
        }
    }

    private fun sensor(
        temperatureC: Double? = 31.2,
        humidityPercent: Double? = 72.4,
        gateway: SensorGatewaySummary? = SensorGatewaySummary(
            GatewayStatus.ONLINE,
            NOW_EPOCH_MILLIS - 35_000,
            RadioReceptionStatus.RECENT,
            -79.0,
            8.4,
            BackendDeliveryStatus.NORMAL,
        ),
    ) = SensorCurrentState(
        nodeId = "GTH-10003BD4BCFC",
        position = GeoPoint(-6.235149, 106.720401),
        waterHeightMm = 123,
        effectiveLevel = FloodHazardLevel.LOW,
        freshness = FloodHazardFreshness.FRESH,
        observedAtEpochMillis = OBSERVED_AT_EPOCH_MILLIS,
        acceptedDistanceMm = 1602,
        temperatureC = temperatureC,
        humidityPercent = humidityPercent,
        gateway = gateway,
    )

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
