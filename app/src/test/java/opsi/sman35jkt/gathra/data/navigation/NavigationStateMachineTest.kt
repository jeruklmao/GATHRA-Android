package opsi.sman35jkt.gathra.data.navigation

import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.domain.navigation.NavigationSession
import opsi.sman35jkt.gathra.domain.navigation.NavigationStateMachine
import opsi.sman35jkt.gathra.domain.navigation.NavigationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationStateMachineTest {
    @Test
    fun `valid lifecycle transitions are deterministic`() {
        val idle = NavigationSession(
            id = "session",
            route = testRoute(),
            travelMode = TravelMode.CAR,
        )

        val preparing = NavigationStateMachine.transition(idle, NavigationStatus.PREPARING)
        val navigating = NavigationStateMachine.transition(
            preparing,
            NavigationStatus.NAVIGATING,
        )
        val arrived = NavigationStateMachine.transition(navigating, NavigationStatus.ARRIVED)
        val stopped = NavigationStateMachine.transition(arrived, NavigationStatus.STOPPED)

        assertEquals(NavigationStatus.STOPPED, stopped.status)
        assertTrue(
            NavigationStateMachine.canTransition(
                NavigationStatus.OFF_ROUTE,
                NavigationStatus.RECALCULATING,
            ),
        )
        assertTrue(
            NavigationStateMachine.canTransition(
                NavigationStatus.RECALCULATING,
                NavigationStatus.ARRIVED,
            ),
        )
        assertTrue(
            NavigationStateMachine.canTransition(
                NavigationStatus.GPS_UNAVAILABLE,
                NavigationStatus.ARRIVED,
            ),
        )
        assertFalse(
            NavigationStateMachine.canTransition(
                NavigationStatus.ARRIVED,
                NavigationStatus.NAVIGATING,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid transition is rejected`() {
        val session = NavigationSession(
            id = "session",
            route = testRoute(),
            travelMode = TravelMode.CAR,
        )

        NavigationStateMachine.transition(session, NavigationStatus.ARRIVED)
    }
}
