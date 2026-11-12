package com.gesturebridge.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.gesturebridge.R
import com.gesturebridge.ui.MainActivity

/**
 * Optional keep-alive ("Stay ready" on the config screen).
 *
 * What it is for: while it runs, Android treats the app as doing user-visible
 * work, so the process is not frozen or killed in the background. That keeps
 * the HTTP client warm (faster first press), lets the launcher-shortcut and
 * intent triggers finish their request reliably, and lets haptics play while
 * the app is off screen. Its notification is published as a Wear OS Ongoing
 * Activity, which puts an icon on the watch face that opens the config screen.
 *
 * What it is NOT: it does not itself receive key presses. That is
 * [KeyInterceptService], which the system keeps running on its own.
 */
class BridgeForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            // LOW importance: no sound, no vibration, no heads-up.
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bridge)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(openApp)

        // Turns the notification into an Ongoing Activity (watch-face indicator
        // and an entry in the recents launcher). Must be applied before the
        // notification is built and posted.
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notification)
            .setStaticIcon(R.drawable.ic_bridge)
            .setTouchIntent(openApp)
            .setStatus(Status.Builder().addTemplate(getString(R.string.notification_text)).build())
            .build()
            .apply(applicationContext)

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    // START_STICKY: if the system has to kill the process, recreate the service
    // when memory allows.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BridgeForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BridgeForegroundService::class.java))
        }
    }
}
