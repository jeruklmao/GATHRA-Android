package opsi.sman35jkt.gathra.data.navigation

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import opsi.sman35jkt.gathra.core.location.NavigationLocationEvent
import opsi.sman35jkt.gathra.core.location.NavigationLocationSource
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.core.model.TravelMode
import opsi.sman35jkt.gathra.domain.navigation.NavigationStatus
import opsi.sman35jkt.gathra.domain.route.RouteRepository

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionEngineTest {
    @Test
    fun `start consumes locations and stop cancels collection and stops session`() = runTest {
        val sessionRepository = preparedSessionRepository()
        val locationSource = RecordingLocationSource()
        var now = 0L
        val engine = NavigationSessionEngine(
            sessionRepository = sessionRepository,
            routeRepository = StubRouteRepository { emptyList() },
            locationSource = locationSource,
            workDispatcher = StandardTestDispatcher(testScheduler),
            elapsedRealtimeMillis = { now },
        )

        assertTrue(engine.start(backgroundScope))
        runCurrent()
        assertEquals(1, locationSource.activeCollectors.get())

        now = 1_000L
        locationSource.emit(location(0.0, 0.0005, elapsedRealtimeMillis = now))
        runCurrent()
        assertEquals(
            NavigationStatus.NAVIGATING,
            sessionRepository.session.value?.status,
        )

        engine.stop()
        runCurrent()

        assertEquals(NavigationStatus.STOPPED, sessionRepository.session.value?.status)
        assertEquals(0, locationSource.activeCollectors.get())
        assertEquals(1, locationSource.completedCollectors.get())
    }

    @Test
    fun `three off route samples replace route only after successful reroute`() = runTest {
        val originalRoute = testRoute()
        val reroutedRoute = testRoute().copy(id = "rerouted-route")
        val sessionRepository = preparedSessionRepository(originalRoute)
        val locationSource = RecordingLocationSource()
        val requests = mutableListOf<RouteRequest>()
        var now = 0L
        val engine = NavigationSessionEngine(
            sessionRepository = sessionRepository,
            routeRepository = StubRouteRepository { request ->
                requests += request
                listOf(reroutedRoute)
            },
            locationSource = locationSource,
            workDispatcher = StandardTestDispatcher(testScheduler),
            elapsedRealtimeMillis = { now },
        )
        assertTrue(engine.start(backgroundScope))
        runCurrent()

        fun emitOffRoute(atMillis: Long) {
            now = atMillis
            locationSource.emit(
                location(
                    latitude = 0.0005,
                    longitude = 0.0015,
                    elapsedRealtimeMillis = atMillis,
                ),
            )
            runCurrent()
        }

        emitOffRoute(1_000L)
        emitOffRoute(2_000L)
        assertEquals(originalRoute.id, sessionRepository.session.value?.route?.id)
        assertTrue(requests.isEmpty())

        emitOffRoute(3_000L)

        assertEquals(1, requests.size)
        assertEquals(reroutedRoute.id, sessionRepository.session.value?.route?.id)
        assertEquals(
            NavigationStatus.NAVIGATING,
            sessionRepository.session.value?.status,
        )
        assertEquals(2, locationSource.startedCollectors.get())
        engine.stop()
    }

    @Test
    fun `successful reroute preserves cooldown before another backend request`() = runTest {
        val originalRoute = testRoute()
        val reroutedRoute = testRoute().copy(id = "rerouted-route")
        val sessionRepository = preparedSessionRepository(originalRoute)
        val locationSource = RecordingLocationSource()
        val requestCount = AtomicInteger()
        var now = 0L
        val engine = NavigationSessionEngine(
            sessionRepository = sessionRepository,
            routeRepository = StubRouteRepository {
                requestCount.incrementAndGet()
                listOf(reroutedRoute)
            },
            locationSource = locationSource,
            workDispatcher = StandardTestDispatcher(testScheduler),
            elapsedRealtimeMillis = { now },
        )
        assertTrue(engine.start(backgroundScope))
        runCurrent()

        repeat(6) { index ->
            now = (index + 1) * 1_000L
            locationSource.emit(
                location(
                    latitude = 0.0005,
                    longitude = 0.0015,
                    elapsedRealtimeMillis = now,
                ),
            )
            runCurrent()
        }

        assertEquals(1, requestCount.get())
        assertEquals(reroutedRoute.id, sessionRepository.session.value?.route?.id)
        engine.stop()
    }

    @Test
    fun `location disabled source is retried and recovers`() = runTest {
        assertLocationSourceRecovery(NavigationLocationEvent.LocationDisabled)
    }

    @Test
    fun `revoked permission source is retried and recovers`() = runTest {
        assertLocationSourceRecovery(NavigationLocationEvent.PermissionDenied)
    }

    @Test
    fun `failed reroute retains current route and exposes recoverable error state`() = runTest {
        val originalRoute = testRoute()
        val sessionRepository = preparedSessionRepository(originalRoute)
        val locationSource = RecordingLocationSource()
        var now = 0L
        val engine = NavigationSessionEngine(
            sessionRepository = sessionRepository,
            routeRepository = StubRouteRepository {
                throw IllegalStateException("offline")
            },
            locationSource = locationSource,
            workDispatcher = StandardTestDispatcher(testScheduler),
            elapsedRealtimeMillis = { now },
        )
        assertTrue(engine.start(backgroundScope))
        runCurrent()

        repeat(3) { index ->
            now = (index + 1) * 1_000L
            locationSource.emit(
                location(
                    latitude = 0.0005,
                    longitude = 0.0015,
                    elapsedRealtimeMillis = now,
                ),
            )
            runCurrent()
        }

        val failed = requireNotNull(sessionRepository.session.value)
        assertEquals(originalRoute.id, failed.route.id)
        assertEquals(NavigationStatus.ERROR, failed.status)
        assertEquals("reroute_failed", failed.rerouteError)

        now = 4_000L
        locationSource.emit(
            location(
                latitude = 0.0005,
                longitude = 0.0015,
                elapsedRealtimeMillis = now,
            ),
        )
        runCurrent()
        val stillRecoverable = requireNotNull(sessionRepository.session.value)
        assertEquals(NavigationStatus.ERROR, stillRecoverable.status)
        assertEquals("reroute_failed", stillRecoverable.rerouteError)
        assertEquals(originalRoute.id, stillRecoverable.route.id)
        engine.stop()
    }

    @Test
    fun `stop cancels pending reroute so late response cannot replace route`() = runTest {
        val originalRoute = testRoute()
        val reroutedRoute = testRoute().copy(id = "late-rerouted-route")
        val pendingResponse = CompletableDeferred<List<RouteOption>>()
        val cancellationCount = AtomicInteger()
        val requestCount = AtomicInteger()
        val sessionRepository = preparedSessionRepository(originalRoute)
        val locationSource = RecordingLocationSource()
        var now = 0L
        val engine = NavigationSessionEngine(
            sessionRepository = sessionRepository,
            routeRepository = StubRouteRepository {
                requestCount.incrementAndGet()
                try {
                    pendingResponse.await()
                } catch (cancelled: CancellationException) {
                    cancellationCount.incrementAndGet()
                    throw cancelled
                }
            },
            locationSource = locationSource,
            workDispatcher = StandardTestDispatcher(testScheduler),
            elapsedRealtimeMillis = { now },
        )
        assertTrue(engine.start(backgroundScope))
        runCurrent()

        repeat(3) { index ->
            now = (index + 1) * 1_000L
            locationSource.emit(
                location(
                    latitude = 0.0005,
                    longitude = 0.0015,
                    elapsedRealtimeMillis = now,
                ),
            )
            runCurrent()
        }
        assertEquals(1, requestCount.get())
        assertEquals(
            NavigationStatus.RECALCULATING,
            sessionRepository.session.value?.status,
        )

        engine.stop()
        runCurrent()
        pendingResponse.complete(listOf(reroutedRoute))
        runCurrent()

        val stopped = requireNotNull(sessionRepository.session.value)
        assertEquals(NavigationStatus.STOPPED, stopped.status)
        assertEquals(originalRoute.id, stopped.route.id)
        assertEquals(1, cancellationCount.get())
        assertFalse(locationSource.activeCollectors.get() > 0)
    }

    private fun preparedSessionRepository(
        route: RouteOption = testRoute(),
    ): NavigationSessionRepository = NavigationSessionRepository().also {
        it.prepare(
            route = route,
            destination = route.geometry.points.last(),
            travelMode = TravelMode.CAR,
        )
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertLocationSourceRecovery(
        initialEvent: NavigationLocationEvent,
    ) {
        val sessionRepository = preparedSessionRepository()
        val locationSource = RecoveringLocationSource(initialEvent)
        var now = 0L
        val engine = NavigationSessionEngine(
            sessionRepository = sessionRepository,
            routeRepository = StubRouteRepository { emptyList() },
            locationSource = locationSource,
            workDispatcher = StandardTestDispatcher(testScheduler),
            elapsedRealtimeMillis = { now },
        )
        assertTrue(engine.start(backgroundScope))
        runCurrent()
        assertEquals(
            NavigationStatus.GPS_UNAVAILABLE,
            sessionRepository.session.value?.status,
        )
        assertEquals(1, locationSource.subscriptionCount.get())

        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(2, locationSource.subscriptionCount.get())
        now = 5_000L
        locationSource.emit(
            location(
                latitude = 0.0,
                longitude = 0.0005,
                elapsedRealtimeMillis = now,
            ),
        )
        runCurrent()

        assertEquals(
            NavigationStatus.NAVIGATING,
            sessionRepository.session.value?.status,
        )
        engine.stop()
    }

    private class RecordingLocationSource : NavigationLocationSource {
        private val events = MutableSharedFlow<NavigationLocationEvent>(
            extraBufferCapacity = 16,
        )
        val activeCollectors = AtomicInteger()
        val startedCollectors = AtomicInteger()
        val completedCollectors = AtomicInteger()

        override fun updates(route: RouteOption): Flow<NavigationLocationEvent> =
            events
                .onStart {
                    startedCollectors.incrementAndGet()
                    activeCollectors.incrementAndGet()
                }
                .onCompletion {
                    activeCollectors.decrementAndGet()
                    completedCollectors.incrementAndGet()
                }

        fun emit(sample: opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample) {
            check(events.tryEmit(NavigationLocationEvent.Location(sample)))
        }
    }

    private class RecoveringLocationSource(
        private val initialEvent: NavigationLocationEvent,
    ) : NavigationLocationSource {
        private val recoveredEvents = MutableSharedFlow<NavigationLocationEvent>(
            extraBufferCapacity = 4,
        )
        val subscriptionCount = AtomicInteger()

        override fun updates(route: RouteOption): Flow<NavigationLocationEvent> {
            val subscription = subscriptionCount.incrementAndGet()
            return if (subscription == 1) {
                flow { emit(initialEvent) }
            } else {
                recoveredEvents
            }
        }

        fun emit(
            sample: opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample,
        ) {
            check(recoveredEvents.tryEmit(NavigationLocationEvent.Location(sample)))
        }
    }

    private class StubRouteRepository(
        private val response: suspend (RouteRequest) -> List<RouteOption>,
    ) : RouteRepository {
        override suspend fun getRoutes(request: RouteRequest): List<RouteOption> =
            response(request)
    }
}
