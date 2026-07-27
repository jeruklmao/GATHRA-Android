package opsi.sman35jkt.gathra.service.navigation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Application-context boundary for starting and stopping the navigation service.
 *
 * Session data is kept in the application-scoped repository and is deliberately
 * not serialized through an Intent, which avoids Binder size limits for route geometry.
 */
class NavigationServiceController(context: Context) {
    private val applicationContext = context.applicationContext

    fun start(): Boolean = runCatching {
        ContextCompat.startForegroundService(
            applicationContext,
            NavigationForegroundService.intent(
                applicationContext,
                NavigationForegroundService.ACTION_START,
            ),
        )
    }.isSuccess

    fun stop(): Boolean {
        val stopIntent = NavigationForegroundService.intent(
            applicationContext,
            NavigationForegroundService.ACTION_STOP,
        )
        return runCatching {
            applicationContext.startService(stopIntent)
            true
        }.getOrElse {
            // If delivering an action is restricted, stopping the existing
            // component still invokes onDestroy(), which cancels the engine.
            runCatching {
                applicationContext.stopService(stopIntent)
            }.getOrDefault(false)
        }
    }

    fun retryReroute(): Boolean = runCatching {
        sendAction(NavigationForegroundService.ACTION_RETRY_REROUTE)
    }.isSuccess

    fun setSimulationPaused(paused: Boolean): Boolean = runCatching {
        applicationContext.startService(
            NavigationForegroundService.intent(
                applicationContext,
                NavigationForegroundService.ACTION_SET_SIMULATION_PAUSED,
            ).putExtra(NavigationForegroundService.EXTRA_PAUSED, paused),
        )
    }.isSuccess

    fun setSimulationSpeed(multiplier: Double): Boolean = runCatching {
        applicationContext.startService(
            NavigationForegroundService.intent(
                applicationContext,
                NavigationForegroundService.ACTION_SET_SIMULATION_SPEED,
            ).putExtra(NavigationForegroundService.EXTRA_SPEED_MULTIPLIER, multiplier),
        )
    }.isSuccess

    fun simulateOffRoute(): Boolean = runCatching {
        sendAction(NavigationForegroundService.ACTION_SIMULATE_OFF_ROUTE)
    }.isSuccess

    private fun sendAction(action: String) {
        applicationContext.startService(
            NavigationForegroundService.intent(applicationContext, action),
        )
    }
}
