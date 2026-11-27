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

/**
 * 펫을 화면에 띄워 두는 서비스.
 * 보통은 한 마리, VIP+는 여러 마리를 함께 띄운다 (펫마다 창이 하나씩).
 */
class PetService : Service() {

    private lateinit var settings: PetSettingsRepository
    // 화면에 나와 있는 펫들 (펫 id → 창). 소환한 순서를 지킨다
    private val overlays = LinkedHashMap<String, PetOverlayManager>()
    private var stopping = false
    private var screenOn = true
    // 폰을 흔들면 펫들이 날아오른다 (VIP+)
    private val shakeDetector by lazy { ShakeDetector(this) { overlays.values.forEach { it.fling() } } }

    /** 말풍선 대사: VIP+이고 설정에서 켜 두었을 때 */
    private val canTalk: Boolean get() = settings.tier.plusFeatures && settings.talkEnabled

    /** 여러 마리 소환을 쓸 수 있는 등급인지 */
    private val multi: Boolean get() = settings.tier.maxPets > 1

    // 설정 화면에서 값이 바뀌면 실행 중인 펫에 바로 반영한다
    private val settingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PetSettingsRepository.KEY_PET -> {
                    // 한 마리 모드: 다른 펫을 고르면 바꿔 띄운다. 잠긴 펫을 구경만 하는 중이면 그대로 둔다
                    if (!multi && Pets.isUnlocked(Pets.byId(settings.selectedPetId), settings.tier)) {
                        syncPets()
                    }
                }
                PetSettingsRepository.KEY_SUMMONED -> if (multi) syncPets()
                PetSettingsRepository.KEY_TALK -> overlays.values.forEach { it.setCanTalk(canTalk) }
                PetSettingsRepository.KEY_TIER -> {
                    // VIP+가 되면 지금 나와 있는 펫부터 여러 마리 목록에 넣는다
                    if (multi && settings.summonedPets.isEmpty()) {
                        settings.summonedPets = overlays.keys.toList()
                    }
                    // 등급이 바뀌면 던지기·잠자기 가능 여부와 펫 잠금을 다시 적용한다
                    overlays.values.forEach {
                        it.setCanThrow(settings.tier.canThrow)
                        it.setCanSleep(settings.tier.plusFeatures)
                        it.setCanTalk(canTalk)
                    }
                    syncPets()
                    updateShake()
                }
                PetSettingsRepository.KEY_SIZE ->
                    overlays.values.forEach { it.setSize(settings.sizeDp) }
                PetSettingsRepository.KEY_FLOOR ->
                    overlays.values.forEach { it.setFloor(settings.floorDp) }
                PetSettingsRepository.KEY_WALK_FREQ ->
                    overlays.values.forEach { it.setWalkFrequency(settings.walkFrequency) }
            }
        }

    // 화면이 꺼지면 멈추고, 켜지면 다시 움직인다 (배터리 절약)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    overlays.values.forEach { it.pause() }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    screenOn = true
                    // resume()에서 권한이 없으면 서비스가 정리되므로 복사본으로 돈다
                    overlays.values.toList().forEach { it.resume() }
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
        settings.registerListener(settingsListener)
        registerScreenReceiver()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 알림의 ‘소환 해제’ 버튼을 누른 경우: 모두 돌려보낸다
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 시험용 ‘지금 잠들기’ 버튼 (펫이 이미 나와 있을 때만 보낸다)
        if (intent?.action == ACTION_SLEEP_NOW) {
            if (isRunning) overlays.values.forEach { it.sleepNow() }
            return START_NOT_STICKY
        }

        // 여러 마리 모드인데 목록이 비어 있으면 고른 펫 한 마리로 시작한다
        if (multi && settings.summonedPets.isEmpty()) {
            settings.summonedPets = listOf(Pets.usable(settings.selectedPetId, settings.tier).id)
        }
        syncPets()
        if (overlays.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startAsForeground()
        } catch (e: Exception) {
            // 시스템이 포그라운드 실행을 허용하지 않은 경우
            stopSelf()
            return START_NOT_STICKY
        }

        // 아직 안 띄운 펫을 띄운다 (이미 떠 있는 펫은 그대로). 두 번째부터는 겹치지 않게 흩어 둔다
        overlays.values.toList().forEachIndexed { index, overlay ->
            if (!overlay.show(spread = index > 0)) {
                // 권한이 없거나 창을 만들 수 없으면 정리한다
                onPetLost()
                return START_NOT_STICKY
            }
        }

        isRunning = true
        updateShake()
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 화면 회전 등으로 크기가 바뀌면 이동 범위를 다시 계산한다
        overlays.values.forEach { it.refreshBounds() }
    }

    override fun onDestroy() {
        settings.unregisterListener(settingsListener)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            // 이미 해제된 경우는 무시한다
        }
        shakeDetector.stop()
        overlays.values.forEach { it.hide() }
        overlays.clear()
        // 다음에 소환할 때는 처음부터 (고른 펫 한 마리로) 시작한다
        settings.summonedPets = emptyList()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 지금 화면에 있어야 할 펫들 */
    private fun desiredPets(): List<PetDefinition> {
        val tier = settings.tier
        if (!multi) return listOf(Pets.usable(settings.selectedPetId, tier))
        return settings.summonedPets
            .map { Pets.byId(it) }
            .filter { Pets.isUnlocked(it, tier) }
            .distinctBy { it.id }
            .take(tier.maxPets)
    }

    /** 화면의 펫들을 원하는 목록에 맞춘다: 빠진 펫은 돌려보내고, 새 펫은 띄운다 */
    private fun syncPets() {
        if (stopping) return
        val wanted = desiredPets()
        if (wanted.isEmpty()) {
            // 여러 마리 모드에서 마지막 펫까지 돌려보냈다
            if (isRunning) stopSelf()
            return
        }
        val wantedIds = wanted.map { it.id }.toSet()
        overlays.keys.filter { it !in wantedIds }.forEach { overlays.remove(it)?.hide() }

        for (pet in wanted) {
            if (overlays.containsKey(pet.id)) continue
            val overlay = createOverlay(pet)
            overlays[pet.id] = overlay
            // 이미 실행 중이면 바로 띄운다. 처음 시작할 때는 onStartCommand에서 띄운다
            if (isRunning && !overlay.show(spread = overlays.size > 1)) {
                onPetLost()
                return
            }
        }
        if (isRunning) updateNotification()   // 알림의 펫 이름·마릿수를 고친다
    }

    private fun createOverlay(pet: PetDefinition): PetOverlayManager {
        return PetOverlayManager(this, pet) { onPetLost() }.apply {
            setSize(settings.sizeDp)
            setFloor(settings.floorDp)
            setWalkFrequency(settings.walkFrequency)
            setCanThrow(settings.tier.canThrow)
            setCanSleep(settings.tier.plusFeatures)
            setCanTalk(canTalk)
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

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
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
        // 알림에 붙는 ‘소환 해제’ 버튼: 앱을 열지 않고 바로 펫을 (모두) 돌려보낸다
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

        // 한 마리면 "시로 소환 중", 여러 마리면 "시로 외 2마리 소환 중"
        val first = overlays.keys.firstOrNull() ?: settings.selectedPetId
        val firstName = getString(Pets.byId(first).nameRes)
        val text = if (overlays.size > 1) {
            getString(R.string.notification_text_multi, firstName, overlays.size - 1)
        } else {
            getString(R.string.notification_text, firstName)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
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
