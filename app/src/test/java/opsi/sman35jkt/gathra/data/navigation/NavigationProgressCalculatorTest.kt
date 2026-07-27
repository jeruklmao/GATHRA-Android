package opsi.sman35jkt.gathra.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationProgressCalculatorTest {
    @Test
    fun `calculates remaining distance duration current step and manoeuvre distance`() {
        val calculator = NavigationProgressCalculator(testRoute())

        val progress = calculator.calculate(
            location(
                latitude = 0.0,
                longitude = 0.0015,
                elapsedRealtimeMillis = 1_000L,
            ),
        )

        assertEquals(1, progress.currentStepIndex)
        assertEquals(167.0, progress.travelledDistanceMeters, 2.0)
        assertEquals(167.0, progress.remainingDistanceMeters, 2.0)
        assertEquals(90L, progress.remainingDurationSeconds)
        assertEquals(55.7, progress.distanceToNextManoeuvreMeters, 2.0)
        assertFalse(progress.isArrived)
    }

    @Test
    fun `arrival requires three accurate readings`() {
        val calculator = NavigationProgressCalculator(testRoute())
        val first = calculator.calculate(
            location(0.0, 0.003, elapsedRealtimeMillis = 1_000L),
        )
        val second = calculator.calculate(
            location(0.0, 0.003, elapsedRealtimeMillis = 2_000L),
        )
        val third = calculator.calculate(
            location(0.0, 0.003, elapsedRealtimeMillis = 3_000L),
        )

        assertFalse(first.isArrived)
        assertFalse(second.isArrived)
        assertTrue(third.isArrived)
        assertEquals(0.0, third.remainingDistanceMeters, 0.0)
        assertEquals(0L, third.remainingDurationSeconds)
        assertEquals(testRoute().steps.lastIndex, third.currentStepIndex)
    }

    @Test
    fun `inaccurate reading resets stable arrival detection`() {
        val calculator = NavigationProgressCalculator(testRoute())
        calculator.calculate(location(0.0, 0.003, elapsedRealtimeMillis = 1_000L))
        calculator.calculate(
            location(
                0.0,
                0.003,
                elapsedRealtimeMillis = 2_000L,
                accuracyMeters = 80.0,
            ),
        )
        val afterReset = calculator.calculate(
            location(0.0, 0.003, elapsedRealtimeMillis = 3_000L),
        )

        assertFalse(afterReset.isArrived)
    }
}

