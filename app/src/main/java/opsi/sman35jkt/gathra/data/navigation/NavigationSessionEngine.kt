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
import opsi.sman35jkt.gathra.domain.navigation.NavigationFloodRouteStatus
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
    private val floodRerouteCooldownMillis: Long = FLOOD_REROUTE_COOLDOWN_MILLIS,
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
    private var floodCooldownJob: Job? = null
    private var progressCalculator: NavigationProgressCalculator? = null
    private var latestAcceptedLocation: NavigationLocationSample? = null
    private var pendingFloodSnapshotId: String? = null
    private var lastFloodRerouteSnapshotId: String? = null
    private var activeFloodRerouteSnapshotId: String? = null
    private var lastFloodRerouteStartedAtMillis: Long = Long.MIN_VALUE

    fun start(scope: CoroutineScope): Boolean {
        val session = sessionRepository.session.value ?: return false
        if (session.route.steps.isEmpty()) return false

        stopJobs()
        serviceScope = scope
        running.set(true)
        latestAcceptedLocation = null
        pendingFloodSnapshotId = null
        lastFloodRerouteSnapshotId = null
        activeFloodRerouteSnapshotId = null
        lastFloodRerouteStartedAtMillis = Long.MIN_VALUE
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
        val session = sessionRepository.session.value
        val floodSnapshotId = session?.floodTargetSnapshotId
        if (
            session?.floodRouteStatus ==
            NavigationFloodRouteStatus.STALE &&
            floodSnapshotId != null
        ) {
            pendingFloodSnapshotId = floodSnapshotId
            requestFloodReroute(location, floodSnapshotId, force = true)
        } else {
            startReroute(location, RerouteReason.OFF_ROUTE)
        }
    }

    fun revalidateFloodSnapshot(snapshotId: String) {
        if (!running.get() || snapshotId.isBlank()) return
        val session = sessionRepository.session.value ?: return
        if (session.route.risk?.hazardSnapshotId == snapshotId) {
            cancelSupersededFloodReroute(snapshotId)
            pendingFloodSnapshotId = null
            sessionRepository.markFloodRouteSynchronized()
            return
        }
        if (
            session.floodTargetSnapshotId == snapshotId &&
            session.floodRouteStatus in setOf(
                NavigationFloodRouteStatus.UPDATING,
                NavigationFloodRouteStatus.STALE,
            )
        ) {
            return
        }

        cancelSupersededFloodReroute(snapshotId)
        pendingFloodSnapshotId = snapshotId
        sessionRepository.markFloodRouteUpdating(snapshotId)
        latestAcceptedLocation?.let { location ->
            requestFloodReroute(location, snapshotId)
        }
    }

    fun stop() {
        running.set(false)
        stopJobs()
        rerouteTracker.invalidate()
        voicePolicy.reset()
        deviationDetector.reset(clearCooldown = true)
        progressCalculator = null
        latestAcceptedLocation = null
        pendingFloodSnapshotId = null
        lastFloodRerouteSnapshotId = null
        activeFloodRerouteSnapshotId = null
        sessionRepository.finish()
    }

    fun pauseAfterArrival() {
        locationJob?.cancel()
        locationJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        rerouteJob?.cancel()
        rerouteJob = null
        floodCooldownJob?.cancel()
        floodCooldownJob = null
        rerouteTracker.invalidate()
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
            pendingFloodSnapshotId != null -> requestFloodReroute(
                sample,
                requireNotNull(pendingFloodSnapshotId),
            )
            progress.shouldReroute -> startReroute(sample, RerouteReason.OFF_ROUTE)
        }
    }

    private fun requestFloodReroute(
        location: NavigationLocationSample,
        snapshotId: String,
        force: Boolean = false,
    ) {
        if (
            !force &&
            lastFloodRerouteSnapshotId == snapshotId
        ) {
            return
        }
        val remainingCooldown = if (lastFloodRerouteStartedAtMillis == Long.MIN_VALUE) {
            0L
        } else {
            floodRerouteCooldownMillis -
                (elapsedRealtimeMillis() - lastFloodRerouteStartedAtMillis)
        }
        if (!force && remainingCooldown > 0L) {
            if (floodCooldownJob?.isActive == true) return
            val scope = serviceScope ?: return
            floodCooldownJob = scope.launch {
                delay(remainingCooldown)
                val latestSnapshotId = pendingFloodSnapshotId
                val latestLocation = latestAcceptedLocation
                floodCooldownJob = null
                if (
                    running.get() &&
                    latestSnapshotId != null &&
                    latestLocation != null
                ) {
                    startReroute(
                        latestLocation,
                        RerouteReason.FLOOD_UPDATE,
                        latestSnapshotId,
                    )
                }
            }
            return
        }
        startReroute(location, RerouteReason.FLOOD_UPDATE, snapshotId)
    }

    private fun startReroute(
        location: NavigationLocationSample,
        reason: RerouteReason,
        targetFloodSnapshotId: String? = null,
    ) {
        val scope = serviceScope ?: return
        val session = sessionRepository.session.value ?: return
        if (rerouteJob?.isActive == true) {
            if (
                reason != RerouteReason.FLOOD_UPDATE ||
                targetFloodSnapshotId == activeFloodRerouteSnapshotId
            ) {
                return
            }
            rerouteJob?.cancel()
            rerouteTracker.invalidate()
        }

        val generation = rerouteTracker.beginRequest()
        if (reason == RerouteReason.FLOOD_UPDATE) {
            val snapshotId = targetFloodSnapshotId ?: return
            pendingFloodSnapshotId = snapshotId
            lastFloodRerouteSnapshotId = snapshotId
            activeFloodRerouteSnapshotId = snapshotId
            lastFloodRerouteStartedAtMillis = elapsedRealtimeMillis()
            sessionRepository.markFloodRouteUpdating(snapshotId)
        }
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
                val floodResultIsCurrent =
                    reason != RerouteReason.FLOOD_UPDATE ||
                        (
                            route.risk?.hazardSnapshotId == targetFloodSnapshotId &&
                                pendingFloodSnapshotId == targetFloodSnapshotId &&
                                sessionRepository.session.value
                                    ?.floodTargetSnapshotId == targetFloodSnapshotId
                            )
                if (!floodResultIsCurrent) {
                    if (
                        pendingFloodSnapshotId == targetFloodSnapshotId &&
                        sessionRepository.session.value
                            ?.floodTargetSnapshotId == targetFloodSnapshotId
                    ) {
                        sessionRepository.markFloodRouteStale(
                            requireNotNull(targetFloodSnapshotId),
                        )
                    }
                    return@launch
                }
                val newCalculator = NavigationProgressCalculator(
                    route = route,
                    deviationDetector = deviationDetector,
                )
                val progress = newCalculator.calculate(location)
                progressCalculator = newCalculator
                voicePolicy.reset()
                sessionRepository.replaceRoute(route, location, progress)
                if (reason == RerouteReason.FLOOD_UPDATE) {
                    pendingFloodSnapshotId = null
                }
                startLocationCollection(route)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (rerouteTracker.isCurrent(generation) && running.get()) {
                    if (
                        reason == RerouteReason.FLOOD_UPDATE &&
                        targetFloodSnapshotId != null
                    ) {
                        sessionRepository.markFloodRouteStale(targetFloodSnapshotId)
                    } else {
                        sessionRepository.markRerouteFailed()
                    }
                }
            } finally {
                if (
                    reason == RerouteReason.FLOOD_UPDATE &&
                    rerouteTracker.isCurrent(generation) &&
                    activeFloodRerouteSnapshotId == targetFloodSnapshotId
                ) {
                    activeFloodRerouteSnapshotId = null
                }
            }
        }
    }

    private fun cancelSupersededFloodReroute(snapshotId: String) {
        floodCooldownJob?.cancel()
        floodCooldownJob = null
        if (
            rerouteJob?.isActive == true &&
            activeFloodRerouteSnapshotId != snapshotId
        ) {
            rerouteTracker.invalidate()
            rerouteJob?.cancel()
            rerouteJob = null
            activeFloodRerouteSnapshotId = null
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
        floodCooldownJob?.cancel()
        locationJob = null
        watchdogJob = null
        rerouteJob = null
        floodCooldownJob = null
    }

    private companion object {
        const val GPS_WATCHDOG_INTERVAL_MILLIS = 5_000L
        const val GPS_UNAVAILABLE_AFTER_MILLIS = 12_000L
        const val LOCATION_SOURCE_RETRY_DELAY_MILLIS = 5_000L
        const val FLOOD_REROUTE_COOLDOWN_MILLIS = 5_000L
    }
}

private enum class RerouteReason {
    OFF_ROUTE,
    FLOOD_UPDATE,
}
