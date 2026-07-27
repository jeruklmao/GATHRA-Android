package opsi.sman35jkt.gathra.service.navigation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import opsi.sman35jkt.gathra.GathraApplication
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.navigation.navigationInstruction
import opsi.sman35jkt.gathra.core.navigation.navigationVoiceInstruction
import opsi.sman35jkt.gathra.domain.navigation.NavigationSession
import opsi.sman35jkt.gathra.domain.navigation.NavigationStatus

class NavigationForegroundService : Service() {
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )
    private val stopping = AtomicBoolean(false)
    private var foregroundStarted = false

    private val container by lazy {
        (application as GathraApplication).appContainer
    }
    private val repository by lazy { container.navigationSessionRepository }
    private val engine by lazy { container.navigationSessionEngine }
    private lateinit var notificationFactory: NavigationNotificationFactory
    private lateinit var voiceManager: NavigationVoiceManager

    override fun onCreate() {
        super.onCreate()
        notificationFactory = NavigationNotificationFactory(applicationContext)
        notificationFactory.createChannel()
        voiceManager = NavigationVoiceManager(applicationContext) {
            repository.markVoiceUnavailable()
        }
        serviceScope.launch {
            repository.session.filterNotNull().collect(::handleSessionUpdate)
        }
        serviceScope.launch {
            engine.voiceEvents.collect { event ->
                val session = repository.session.value ?: return@collect
                voiceManager.setMuted(session.muted)
                voiceManager.speak(
                    text = applicationContext.navigationVoiceInstruction(event),
                    utteranceId = "${event.routeId}:${event.step.index}:${event.cue}",
                )
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> startNavigation()
            ACTION_STOP -> stopNavigation()
            ACTION_RETRY_REROUTE -> engine.retryReroute()
            ACTION_SET_SIMULATION_PAUSED -> engine.setSimulationPaused(
                intent.getBooleanExtra(EXTRA_PAUSED, false),
            )
            ACTION_SET_SIMULATION_SPEED -> engine.setSimulationSpeed(
                intent.getDoubleExtra(EXTRA_SPEED_MULTIPLIER, 1.0),
            )
            ACTION_SIMULATE_OFF_ROUTE -> engine.simulateOffRoute()
            else -> if (!foregroundStarted) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (!stopping.get()) {
            engine.stop()
        }
        voiceManager.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startNavigation() {
        if (foregroundStarted) return
        stopping.set(false)
        val preparingNotification = notificationFactory.create(
            instruction = getString(R.string.navigation_notification_preparing),
            progressText = null,
        )
        val foregroundResult = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NavigationNotificationFactory.NOTIFICATION_ID,
                    preparingNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(
                    NavigationNotificationFactory.NOTIFICATION_ID,
                    preparingNotification,
                )
            }
        }
        if (foregroundResult.isFailure) {
            repository.transitionTo(NavigationStatus.ERROR)
            stopSelf()
            return
        }
        foregroundStarted = true
        if (!engine.start(serviceScope)) {
            stopNavigation()
        }
    }

    private fun handleSessionUpdate(session: NavigationSession) {
        voiceManager.setMuted(session.muted)
        if (session.status == NavigationStatus.ARRIVED) {
            engine.pauseAfterArrival()
        }
        if (session.status == NavigationStatus.STOPPED) {
            stopServiceInternals()
            return
        }
        if (!foregroundStarted) return

        notificationFactory.notify(
            notificationFactory.create(
                instruction = notificationInstruction(session),
                progressText = notificationProgress(session),
            ),
        )
    }

    private fun notificationInstruction(session: NavigationSession): String =
        when (session.status) {
            NavigationStatus.PREPARING ->
                getString(R.string.navigation_notification_preparing)
            NavigationStatus.RECALCULATING ->
                getString(R.string.navigation_recalculating)
            NavigationStatus.OFF_ROUTE ->
                getString(R.string.navigation_off_route)
            NavigationStatus.GPS_UNAVAILABLE ->
                getString(R.string.navigation_gps_unavailable)
            NavigationStatus.ARRIVED ->
                getString(R.string.navigation_arrived)
            NavigationStatus.ERROR ->
                getString(R.string.navigation_error)
            else -> {
                val currentIndex = session.progress?.currentStepIndex ?: 0
                val nextStep = session.route.steps[
                    (currentIndex + 1).coerceAtMost(session.route.steps.lastIndex)
                ]
                applicationContext.navigationInstruction(nextStep)
            }
        }

    private fun notificationProgress(session: NavigationSession): String? {
        val progress = session.progress ?: return null
        val distance = if (progress.remainingDistanceMeters < 1_000.0) {
            getString(
                R.string.navigation_distance_meters,
                progress.remainingDistanceMeters.toInt().coerceAtLeast(0),
            )
        } else {
            getString(
                R.string.navigation_distance_kilometers,
                progress.remainingDistanceMeters / 1_000.0,
            )
        }
        val minutes = ceil(progress.remainingDurationSeconds / 60.0)
            .toInt()
            .coerceAtLeast(0)
        return getString(
            R.string.navigation_notification_remaining,
            distance,
            getString(R.string.navigation_eta_minutes, minutes),
        )
    }

    private fun stopNavigation() {
        if (!stopping.compareAndSet(false, true)) return
        engine.stop()
        stopServiceInternals()
    }

    private fun stopServiceInternals() {
        if (!stopping.compareAndSet(false, true) && !foregroundStarted) return
        foregroundStarted = false
        voiceManager.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START =
            "opsi.sman35jkt.gathra.navigation.action.START"
        const val ACTION_STOP =
            "opsi.sman35jkt.gathra.navigation.action.STOP"
        const val ACTION_RETRY_REROUTE =
            "opsi.sman35jkt.gathra.navigation.action.RETRY_REROUTE"
        const val ACTION_SET_SIMULATION_PAUSED =
            "opsi.sman35jkt.gathra.navigation.action.SET_SIMULATION_PAUSED"
        const val ACTION_SET_SIMULATION_SPEED =
            "opsi.sman35jkt.gathra.navigation.action.SET_SIMULATION_SPEED"
        const val ACTION_SIMULATE_OFF_ROUTE =
            "opsi.sman35jkt.gathra.navigation.action.SIMULATE_OFF_ROUTE"
        const val EXTRA_PAUSED = "paused"
        const val EXTRA_SPEED_MULTIPLIER = "speed_multiplier"

        fun intent(context: Context, action: String): Intent =
            Intent(context, NavigationForegroundService::class.java)
                .setAction(action)
    }
}
