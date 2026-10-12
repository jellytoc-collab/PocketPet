package com.pocketpet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import com.pocketpet.MainActivity
import com.pocketpet.R
import com.pocketpet.data.PetSettingsRepository
import com.pocketpet.overlay.PetOverlayManager
import com.pocketpet.pet.Pets

class PetService : Service() {

    private lateinit var overlayManager: PetOverlayManager
    private lateinit var settings: PetSettingsRepository

    // 설정 화면에서 값이 바뀌면 실행 중인 펫에 바로 반영한다
    private val settingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PetSettingsRepository.KEY_SIZE_DP ->
                    overlayManager.setSize(settings.sizeDp)
                PetSettingsRepository.KEY_ACTIVITY ->
                    overlayManager.setActivityLevel(settings.activityLevel)
            }
        }

    override fun onCreate() {
        super.onCreate()
        settings = PetSettingsRepository(this)
        overlayManager = PetOverlayManager(this, Pets.SHIRO)
        overlayManager.setSize(settings.sizeDp)
        overlayManager.setActivityLevel(settings.activityLevel)
        settings.registerListener(settingsListener)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 화면 회전 등으로 크기가 바뀌면 이동 범위를 다시 계산한다
        overlayManager.refreshBounds()
    }

    override fun onDestroy() {
        settings.unregisterListener(settingsListener)
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
            .setSmallIcon(R.drawable.pet_idle_01)
            .setContentTitle("Pocket Pet")
            .setContentText("${Pets.SHIRO.name}가 화면 위에 나와 있어요")
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
