package opsi.sman35jkt.gathra.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviationDetectorTest {
    @Test
    fun `dynamic threshold uses accuracy multiplier when it is larger`() {
        val detector = DeviationDetector()

        assertEquals(35.0, detector.thresholdMeters(10.0), 0.0)
        assertEquals(75.0, detector.thresholdMeters(50.0), 0.0)
    }

    @Test
    fun `requires three consecutive off-route samples`() {
        val detector = DeviationDetector()

        val first = detector.evaluate(60.0, 5.0, 1_000L)
        val second = detector.evaluate(60.0, 5.0, 2_000L)
        val third = detector.evaluate(60.0, 5.0, 3_000L)

        assertFalse(first.isOffRoute)
        assertFalse(second.isOffRoute)
        assertTrue(third.isOffRoute)
        assertTrue(third.shouldReroute)
    }

    @Test
    fun `on-route sample resets the consecutive counter`() {
        val detector = DeviationDetector()
        detector.evaluate(60.0, 5.0, 1_000L)
        detector.evaluate(60.0, 5.0, 2_000L)
        detector.evaluate(10.0, 5.0, 3_000L)

        val next = detector.evaluate(60.0, 5.0, 4_000L)

        assertEquals(1, next.consecutiveOffRouteSamples)
        assertFalse(next.isOffRoute)
    }

    @Test
    fun `reroute cooldown prevents repeated requests`() {
        val detector = DeviationDetector(
            DeviationDetectorConfig(rerouteCooldownMillis = 30_000L),
        )
        detector.evaluate(60.0, 5.0, 1_000L)
        detector.evaluate(60.0, 5.0, 2_000L)
        assertTrue(detector.evaluate(60.0, 5.0, 3_000L).shouldReroute)

        assertFalse(detector.evaluate(60.0, 5.0, 10_000L).shouldReroute)
        assertTrue(detector.evaluate(60.0, 5.0, 33_000L).shouldReroute)
    }
}

