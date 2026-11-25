package com.pocketpet.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * 폰을 빠르고 크게 흔드는 것을 알아챈다.
 * 가속도가 중력의 몇 배를 넘는 순간(한 번 휘두름)이 짧은 간격으로 여러 번 이어지면 흔든 것으로 본다.
 * 살짝 들거나 걸을 때는 반응하지 않는다.
 */
class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var listening = false
    private var peaks = 0              // 이어서 휘두른 횟수
    private var lastPeakMs = 0L
    private var lastShakeMs = 0L

    /** 감지 시작 (이미 듣고 있으면 그대로) */
    fun start() {
        if (listening || sensor == null) return
        peaks = 0
        listening = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI) == true
    }

    /** 감지 멈춤 (배터리 절약: 화면이 꺼지거나 펫이 돌아가면 끈다) */
    fun stop() {
        if (!listening) return
        sensorManager?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (ax, ay, az) = Triple(event.values[0], event.values[1], event.values[2])
        val gForce = sqrt(ax * ax + ay * ay + az * az) / SensorManager.GRAVITY_EARTH
        if (gForce < THRESHOLD_G) return

        val now = SystemClock.elapsedRealtime()
        val gap = now - lastPeakMs
        if (gap < PEAK_MIN_GAP_MS) return              // 같은 휘두름의 연속 값
        peaks = if (gap > PEAK_MAX_GAP_MS) 1 else peaks + 1
        lastPeakMs = now

        if (peaks >= PEAKS_NEEDED && now - lastShakeMs >= COOLDOWN_MS) {
            lastShakeMs = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val THRESHOLD_G = 2.5f        // 이만큼 세게 휘둘러야 한 번으로 친다 (중력의 배수)
        private const val PEAKS_NEEDED = 3          // 이만큼 이어서 휘둘러야 흔든 것
        private const val PEAK_MIN_GAP_MS = 80L
        private const val PEAK_MAX_GAP_MS = 500L    // 휘두름 사이가 이보다 길면 처음부터 다시 센다
        private const val COOLDOWN_MS = 600L        // 계속 흔들면 이 간격마다 다시 날아오른다
    }
}
