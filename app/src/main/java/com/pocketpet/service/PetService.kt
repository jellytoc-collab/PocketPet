package com.pocketpet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import com.pocketpet.MainActivity
import com.pocketpet.R
import com.pocketpet.data.PetSettingsRepository
import com.pocketpet.overlay.PetOverlayManager
import com.pocketpet.pet.PetDefinition
import com.pocketpet.pet.Pets

class PetService : Service() {

    private lateinit var settings: PetSettingsRepository
    private lateinit var currentPet: PetDefinition
    private lateinit var overlayManager: PetOverlayManager
    private var stopping = false
    private var screenOn = true
    // 폰을 흔들면 펫이 날아오른다 (VIP+)
    private val shakeDetector by lazy { ShakeDetector(this) { overlayManager.fling() } }

    // 설정 화면에서 값이 바뀌면 실행 중인 펫에 바로 반영한다
    private val settingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PetSettingsRepository.KEY_PET -> {
                    // 잠긴 펫을 구경만 하는 중이면 지금 펫을 그대로 둔다
                    val pet = Pets.byId(settings.selectedPetId)
                    if (Pets.isUnlocked(pet, settings.tier)) switchPet(pet)
                }
                PetSettingsRepository.KEY_TIER -> {
                    // 등급이 바뀌면 던지기 가능 여부와 펫 잠금을 다시 적용한다
                    overlayManager.setCanThrow(settings.tier.canThrow)
                    overlayManager.setCanSleep(settings.tier.plusFeatures)
                    switchPet(Pets.usable(settings.selectedPetId, settings.tier))
                    updateShake()
                }
                PetSettingsRepository.KEY_SIZE ->
                    overlayManager.setSize(settings.sizeDp)
                PetSettingsRepository.KEY_FLOOR ->
                    overlayManager.setFloor(settings.floorDp)
                PetSettingsRepository.KEY_WALK_FREQ ->
                    overlayManager.setWalkFrequency(settings.walkFrequency)
            }
        }

    // 화면이 꺼지면 멈추고, 켜지면 다시 움직인다 (배터리 절약)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    overlayManager.pause()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    screenOn = true
                    overlayManager.resume()
                }
            }
            updateShake()
        }
    }

    /** 흔들기 감지는 펫이 나와 있고, 화면이 켜져 있고, 등급이 허락할 때만 켠다 (배터리 절약) */
    private fun updateShake() {
        if (isRunning && !stopping && screenOn && settings.tier.canShakeFly) {
            shakeDetector.start()
        } else {
            shakeDetector.stop()
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = PetSettingsRepository(this)
        screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        currentPet = Pets.usable(settings.selectedPetId, settings.tier)
        overlayManager = createOverlay(currentPet)
        settings.registerListener(settingsListener)
        registerScreenReceiver()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 알림의 ‘소환 해제’ 버튼을 누른 경우
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 시험용 ‘지금 잠들기’ 버튼 (펫이 이미 나와 있을 때만 보낸다)
        if (intent?.action == ACTION_SLEEP_NOW) {
            if (isRunning) overlayManager.sleepNow()
            return START_NOT_STICKY
        }

        try {
            startAsForeground()
        } catch (e: Exception) {
            // 시스템이 포그라운드 실행을 허용하지 않은 경우
            stopSelf()
            return START_NOT_STICKY
        }

        if (!overlayManager.show()) {
            // 권한이 없거나 창을 만들 수 없으면 정리한다
            onPetLost()
            return START_NOT_STICKY
        }

        isRunning = true
        updateShake()
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 화면 회전 등으로 크기가 바뀌면 이동 범위를 다시 계산한다
        overlayManager.refreshBounds()
    }

    override fun onDestroy() {
        settings.unregisterListener(settingsListener)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            // 이미 해제된 경우는 무시한다
        }
        shakeDetector.stop()
        overlayManager.hide()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay(pet: PetDefinition): PetOverlayManager {
        return PetOverlayManager(this, pet) { onPetLost() }.apply {
            setSize(settings.sizeDp)
            setFloor(settings.floorDp)
            setWalkFrequency(settings.walkFrequency)
            setCanThrow(settings.tier.canThrow)
            setCanSleep(settings.tier.plusFeatures)
        }
    }

    /** 펫을 더 이상 표시할 수 없을 때: 안내하고 서비스를 끝낸다 */
    private fun onPetLost() {
        if (stopping) return
        stopping = true
        isRunning = false
        shakeDetector.stop()
        val message = if (!Settings.canDrawOverlays(this)) {
            R.string.toast_permission_lost
        } else {
            R.string.toast_cannot_show
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    /** 펫이 나와 있는 동안 다른 펫을 고르면 바로 바꿔 띄운다 */
    private fun switchPet(pet: PetDefinition) {
        if (pet.id == currentPet.id) return
        overlayManager.hide()
        currentPet = pet
        overlayManager = createOverlay(pet)
        if (isRunning) {
            if (!overlayManager.show()) {
                onPetLost()
                return
            }
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

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
            getString(R.string.notification_channel),
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
        // 알림에 붙는 ‘소환 해제’ 버튼: 앱을 열지 않고 바로 펫을 돌려보낸다
        val stopPet = PendingIntent.getService(
            this,
            1,
            Intent(this, PetService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notification),
            getString(R.string.dismiss),
            stopPet
        ).build()

        val petName = getString(currentPet.nameRes)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text, petName))
            .setContentIntent(openApp)
            .addAction(stopAction)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pet_service"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.pocketpet.action.STOP_PET"
        const val ACTION_SLEEP_NOW = "com.pocketpet.action.SLEEP_NOW"

        @Volatile
        var isRunning = false
            private set
    }
}
