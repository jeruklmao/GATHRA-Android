package opsi.sman35jkt.gathra.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLocationSimulatorTest {
    @Test
    fun `simulation moves predictably and supports pause resume and speed`() {
        val simulator = DemoLocationSimulator(
            geometry = testRoute().geometry,
            intervalMillis = 1_000L,
            baseSpeedMetersPerSecond = 10.0,
        )

        val start = simulator.nextLocation()
        val tenMeters = simulator.nextLocation()
        simulator.pause()
        val paused = simulator.nextLocation()
        val pausedAgain = simulator.nextLocation()
        simulator.resume()
        simulator.setSpeedMultiplier(2.0)
        val resumed = simulator.nextLocation()
        val afterFastStep = simulator.nextLocation()

        assertEquals(0.0, start.point.longitude, 0.000001)
        assertEquals(10.0, GeoMath.distanceMeters(start.point, tenMeters.point), 0.2)
        assertEquals(paused.point, pausedAgain.point)
        assertEquals(paused.point, resumed.point)
        assertEquals(20.0, GeoMath.distanceMeters(resumed.point, afterFastStep.point), 0.2)
    }

    @Test
    fun `simulation reaches route destination deterministically`() {
        val simulator = DemoLocationSimulator(
            geometry = testRoute().geometry,
            intervalMillis = 1_000L,
            baseSpeedMetersPerSecond = 100.0,
        )

        repeat(5) { simulator.nextLocation() }
        val destination = simulator.nextLocation()

        assertTrue(simulator.isFinished)
        assertEquals(testRoute().geometry.points.last(), destination.point)
        assertEquals(0.0, destination.speedMetersPerSecond ?: -1.0, 0.0)
        assertFalse(simulator.isPaused)
    }

    @Test
    fun `simulated arrival satisfies stable arrival detector`() {
        val route = testRoute()
        val simulator = DemoLocationSimulator(
            geometry = route.geometry,
            intervalMillis = 1_000L,
            baseSpeedMetersPerSecond = 500.0,
        )
        val calculator = NavigationProgressCalculator(route)

        simulator.nextLocation()
        val firstDestinationReading = calculator.calculate(simulator.nextLocation())
        val secondDestinationReading = calculator.calculate(simulator.nextLocation())
        val thirdDestinationReading = calculator.calculate(simulator.nextLocation())

        assertFalse(firstDestinationReading.isArrived)
        assertFalse(secondDestinationReading.isArrived)
        assertTrue(thirdDestinationReading.isArrived)
    }
}
