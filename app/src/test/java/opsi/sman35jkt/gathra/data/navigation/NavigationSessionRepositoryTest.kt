package opsi.sman35jkt.gathra.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.domain.navigation.NavigationStatus

class NavigationSessionRepositoryTest {
    @Test
    fun `prepare publishes one immutable preparing session`() {
        val repository = NavigationSessionRepository()
        val route = testRoute()

        val prepared = repository.prepare(
            route = route,
            destination = route.geometry.points.last(),
            travelMode = TravelMode.CAR,
        )

        assertSame(prepared, repository.session.value)
        assertEquals(route, prepared.route)
        assertEquals(route.geometry.points.last(), prepared.destination)
        assertEquals(TravelMode.CAR, prepared.travelMode)
        assertEquals(NavigationStatus.PREPARING, prepared.status)
        assertNotNull(prepared.startedAtMillis)
        assertFalse(prepared.muted)
    }

    @Test
    fun `session settings and stop preserve the prepared route`() {
        val repository = NavigationSessionRepository()
        val route = testRoute()
        repository.prepare(
            route = route,
            destination = route.geometry.points.last(),
            travelMode = TravelMode.MOTORCYCLE,
        )

        repository.setMuted(true)
        repository.markVoiceUnavailable()
        repository.finish()

        val stopped = requireNotNull(repository.session.value)
        assertEquals(NavigationStatus.STOPPED, stopped.status)
        assertEquals(route, stopped.route)
        assertEquals(TravelMode.MOTORCYCLE, stopped.travelMode)
        assertTrue(stopped.muted)
        assertTrue(stopped.voiceUnavailable)
    }

    @Test
    fun `prepare rejects a preview route without navigation steps`() {
        val repository = NavigationSessionRepository()
        val previewOnlyRoute = testRoute().copy(steps = emptyList())

        val failure = runCatching {
            repository.prepare(
                route = previewOnlyRoute,
                destination = previewOnlyRoute.geometry.points.last(),
                travelMode = TravelMode.CAR,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(null, repository.session.value)
    }
}
