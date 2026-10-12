package com.pocketpet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import com.pocketpet.MainActivity
import com.pocketpet.R
import com.pocketpet.overlay.PetOverlayManager

class PetService : Service() {

    private lateinit var overlayManager: PetOverlayManager

    override fun onCreate() {
        super.onCreate()
        overlayManager = PetOverlayManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()

        if (!Settings.canDrawOverlays(this)) {
            // 권한이 없으면 펫을 띄우지 않고 서비스를 끝낸다
            stopSelf()
            return START_NOT_STICKY
        }

        overlayManager.show()
        isRunning = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlayManager.hide()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "펫 실행 상태",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.pet_default)
            .setContentTitle("Pocket Pet")
            .setContentText("펫이 화면 위에 나와 있어요")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pet_service"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var isRunning = false
            private set
    }
}
