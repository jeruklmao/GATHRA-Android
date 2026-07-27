package opsi.sman35jkt.gathra.data.navigation

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opsi.sman35jkt.gathra.core.location.NavigationLocationEvent
import opsi.sman35jkt.gathra.core.location.NavigationLocationSource
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.core.model.RouteRequest
import opsi.sman35jkt.gathra.domain.navigation.NavigationLocationSample
import opsi.sman35jkt.gathra.domain.navigation.NavigationStatus
import opsi.sman35jkt.gathra.domain.navigation.VoiceInstructionEvent
import opsi.sman35jkt.gathra.domain.route.RouteRepository

/**
 * Active execution owned by NavigationForegroundService.
 *
 * The engine retains only the latest accepted location. Every job is tied to the
 * service-provided scope and is cancelled as soon as navigation ends.
 */
class NavigationSessionEngine(
    private val sessionRepository: NavigationSessionRepository,
    private val routeRepository: RouteRepository,
    private val locationSource: NavigationLocationSource,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _voiceEvents = MutableSharedFlow<VoiceInstructionEvent>(
        extraBufferCapacity = 8,
    )
    val voiceEvents: SharedFlow<VoiceInstructionEvent> = _voiceEvents.asSharedFlow()

    private val locationFilter = NavigationLocationFilter(
        NavigationLocationFilterConfig(maximumAccuracyMeters = 2_000.0),
    )
    private val deviationDetector = DeviationDetector()
    private val rerouteTracker = RerouteRequestTracker()
    private val voicePolicy = VoiceInstructionPolicy()
    private val running = AtomicBoolean(false)

    private var serviceScope: CoroutineScope? = null
    private var locationJob: Job? = null
    private var watchdogJob: Job? = null
    private var rerouteJob: Job? = null
    private var progressCalculator: NavigationProgressCalculator? = null
    private var latestAcceptedLocation: NavigationLocationSample? = null

    fun start(scope: CoroutineScope): Boolean {
        val session = sessionRepository.session.value ?: return false
        if (session.route.steps.isEmpty()) return false

        stopJobs()
        serviceScope = scope
        running.set(true)
        latestAcceptedLocation = null
        deviationDetector.reset(clearCooldown = true)
        progressCalculator = NavigationProgressCalculator(
            route = session.route,
            deviationDetector = deviationDetector,
        )
        voicePolicy.reset()
        sessionRepository.transitionTo(NavigationStatus.PREPARING)
        startLocationCollection(session.route)
        startGpsWatchdog()
        return true
    }

    fun retryReroute() {
        val location = latestAcceptedLocation ?: return
        if (rerouteJob?.isActive == true) return
        startReroute(location)
    }

    fun stop() {
        running.set(false)
        stopJobs()
        rerouteTracker.invalidate()
        voicePolicy.reset()
        deviationDetector.reset(clearCooldown = true)
        progressCalculator = null
        latestAcceptedLocation = null
        sessionRepository.finish()
    }

    fun pauseAfterArrival() {
        locationJob?.cancel()
        locationJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        rerouteJob?.cancel()
        rerouteJob = null
        rerouteTracker.invalidate()
    }

    fun setSimulationPaused(paused: Boolean) {
        locationSource.setSimulationPaused(paused)
    }

    fun setSimulationSpeed(multiplier: Double) {
        locationSource.setSimulationSpeed(multiplier)
    }

    fun simulateOffRoute() {
        locationSource.simulateOffRoute()
    }

    private fun startLocationCollection(route: RouteOption) {
        val scope = serviceScope ?: return
        locationJob?.cancel()
        locationJob = scope.launch {
            while (isActive && running.get()) {
                try {
                    locationSource.updates(route).collect(::handleLocationEvent)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: SecurityException) {
                    markGpsUnavailable()
                } catch (_: RuntimeException) {
                    markGpsUnavailable()
                }
                if (isActive && running.get()) {
                    markGpsUnavailable()
                    delay(LOCATION_SOURCE_RETRY_DELAY_MILLIS)
                }
            }
        }
    }

    private fun startGpsWatchdog() {
        val scope = serviceScope ?: return
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive && running.get()) {
                delay(GPS_WATCHDOG_INTERVAL_MILLIS)
                val latest = latestAcceptedLocation
                if (
                    latest == null ||
                    elapsedRealtimeMillis() - latest.elapsedRealtimeMillis >
                    GPS_UNAVAILABLE_AFTER_MILLIS
                ) {
                    markGpsUnavailable()
                }
            }
        }
    }

    private fun handleLocationEvent(event: NavigationLocationEvent) {
        when (event) {
            is NavigationLocationEvent.Location -> handleLocation(event.sample)
            NavigationLocationEvent.LocationDisabled,
            NavigationLocationEvent.PermissionDenied,
            NavigationLocationEvent.TemporarilyUnavailable,
            -> markGpsUnavailable()
        }
    }

    private fun handleLocation(sample: NavigationLocationSample) {
        if (!running.get()) return
        val acceptable = locationFilter.isAcceptable(
            candidate = sample,
            previousAccepted = latestAcceptedLocation,
            nowElapsedRealtimeMillis = elapsedRealtimeMillis(),
        )
        if (!acceptable) return

        latestAcceptedLocation = sample
        val session = sessionRepository.session.value ?: return
        val calculator = progressCalculator ?: NavigationProgressCalculator(
            route = session.route,
            deviationDetector = deviationDetector,
        ).also { progressCalculator = it }
        val progress = calculator.calculate(
            location = sample,
            rerouteInProgress = rerouteJob?.isActive == true,
        )
        sessionRepository.updateProgress(sample, progress)

        val updatedSession = sessionRepository.session.value ?: return
        val voiceStep = if (progress.isArrived) {
            updatedSession.route.steps.last()
        } else {
            updatedSession.route.steps.getOrElse(
                (progress.currentStepIndex + 1).coerceAtMost(
                    updatedSession.route.steps.lastIndex,
                ),
            ) {
                updatedSession.route.steps.last()
            }
        }
        voicePolicy.nextEvent(
            routeId = updatedSession.route.id,
            step = voiceStep,
            distanceToManoeuvreMeters = progress.distanceToNextManoeuvreMeters,
            isArrived = progress.isArrived,
        )?.let(_voiceEvents::tryEmit)

        when {
            progress.isArrived -> pauseAfterArrival()
            progress.shouldReroute -> startReroute(sample)
        }
    }

    private fun startReroute(location: NavigationLocationSample) {
        val scope = serviceScope ?: return
        val session = sessionRepository.session.value ?: return
        if (rerouteJob?.isActive == true) return

        val generation = rerouteTracker.beginRequest()
        sessionRepository.transitionTo(NavigationStatus.RECALCULATING)
        rerouteJob = scope.launch {
            try {
                val routes = withContext(workDispatcher) {
                    routeRepository.getRoutes(
                        RouteRequest(
                            origin = location.point,
                            destination = session.destination,
                            travelMode = session.travelMode,
                        ),
                    )
                }
                if (!rerouteTracker.isCurrent(generation) || !running.get()) return@launch
                val route = routes.firstOrNull { it.isRecommended }
                    ?: routes.firstOrNull()
                    ?: error("Reroute response contained no routes.")
                if (route.steps.isEmpty()) {
                    error("Reroute response contained no navigation steps.")
                }
                val newCalculator = NavigationProgressCalculator(
                    route = route,
                    deviationDetector = deviationDetector,
                )
                val progress = newCalculator.calculate(location)
                progressCalculator = newCalculator
                voicePolicy.reset()
                sessionRepository.replaceRoute(route, location, progress)
                startLocationCollection(route)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (rerouteTracker.isCurrent(generation) && running.get()) {
                    sessionRepository.markRerouteFailed()
                }
            }
        }
    }

    private fun markGpsUnavailable() {
        val status = sessionRepository.session.value?.status ?: return
        if (
            status != NavigationStatus.ARRIVED &&
            status != NavigationStatus.STOPPED
        ) {
            sessionRepository.transitionTo(NavigationStatus.GPS_UNAVAILABLE)
        }
    }

    private fun stopJobs() {
        locationJob?.cancel()
        watchdogJob?.cancel()
        rerouteJob?.cancel()
        locationJob = null
        watchdogJob = null
        rerouteJob = null
    }

    private companion object {
        const val GPS_WATCHDOG_INTERVAL_MILLIS = 5_000L
        const val GPS_UNAVAILABLE_AFTER_MILLIS = 12_000L
        const val LOCATION_SOURCE_RETRY_DELAY_MILLIS = 5_000L
    }
}
