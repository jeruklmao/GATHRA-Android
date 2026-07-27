package opsi.sman35jkt.gathra.service.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import opsi.sman35jkt.gathra.MainActivity
import opsi.sman35jkt.gathra.R

class NavigationNotificationFactory(context: Context) {
    private val applicationContext = context.applicationContext
    private val notificationManager = applicationContext.getSystemService(
        NotificationManager::class.java,
    )

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(
                    R.string.navigation_notification_channel,
                ),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = applicationContext.getString(
                    R.string.navigation_notification_channel_description,
                )
                setShowBadge(false)
            },
        )
    }

    fun create(
        instruction: String,
        progressText: String?,
        isOngoing: Boolean = true,
    ): Notification {
        val openAppIntent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openApp = PendingIntent.getActivity(
            applicationContext,
            OPEN_APP_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = NavigationForegroundService.intent(
            applicationContext,
            NavigationForegroundService.ACTION_STOP,
        )
        val stopAction = PendingIntent.getService(
            applicationContext,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_navigation_notification)
            .setContentTitle(
                applicationContext.getString(
                    R.string.navigation_notification_title,
                ),
            )
            .setContentText(instruction)
            .setSubText(progressText)
            .setContentIntent(openApp)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                0,
                applicationContext.getString(
                    R.string.navigation_notification_stop,
                ),
                stopAction,
            )
            .build()
    }

    fun notify(notification: Notification) {
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "gathra_active_navigation"
        const val NOTIFICATION_ID = 3107
        private const val OPEN_APP_REQUEST_CODE = 3108
        private const val STOP_REQUEST_CODE = 3109
    }
}
