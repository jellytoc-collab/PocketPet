package com.pocketpet.pet

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class PetState { IDLE, WALK, DRAGGED, FALL, THROWN, SLEEP, WAKE }

/** 어떤 그림 묶음의 몇 번째 컷을 보여 줄지 */
enum class Anim { IDLE, WALK, FLAP, SLEEP }
data class Frame(val anim: Anim, val index: Int)

/**
 * 펫의 위치와 행동 상태를 관리한다.
 * 화면(View)과 관계없는 계산만 담당한다.
 */
class PetController(
    private val definition: PetDefinition,
    private val density: Float,
    private val random: Random = Random.Default
) {
    var x = 0f
        private set
    var y = 0f
        private set
    var state = PetState.IDLE
        private set
    var facingRight = true
        private set

    /** 걷는 빈도: 클수록 자주 걷는다. 0이면 걷지 않는다 */
    var walkFrequency = 5
        set(value) {
            val newValue = value.coerceAtLeast(0)
            if (newValue == field) return
            field = newValue
            // 쉬는 도중에 바뀌면 새 빈도로 쉬는 시간을 다시 정한다
            if (state == PetState.IDLE) stateTimeLeft = randomRest()
        }

    /** 잠자기를 쓸 수 있는지 (VIP+). 꺼지면 자던 펫은 바로 깬다 */
    var canSleep = false
        set(value) {
            if (value == field) return
            field = value
            if (!value && (state == PetState.SLEEP || state == PetState.WAKE)) enter(PetState.IDLE)
            else if (state == PetState.IDLE) stateTimeLeft = randomRest()
        }

    /** 이 펫이 지금 잠들 수 있는지 (잠자기 그림이 있고 등급이 허락할 때) */
    private val sleepPossible: Boolean
        get() = canSleep && definition.sleepStrip != null

    private val sleepFrames: Int get() = definition.sleepFrameCount
    /** 잠드는 데 걸리는 시간: 모든 컷을 한 번씩 보여 준다 */
    private val fallAsleepSec: Float get() = sleepFrames / SLEEP_FPS
    /** 깨어나는 데 걸리는 시간: 잠든 컷 2장을 뺀 나머지를 거꾸로 */
    private val wakeUpSec: Float get() = (sleepFrames - 2) / WAKE_FPS

    /**
     * 화면이 바뀌지 않고 가만히 있을 시간(초). 이 동안은 매 프레임 계산하지 않아도 된다.
     * 쉬는 중: 남은 쉬는 시간. 잠든 중: 다음 숨쉬기 컷까지 남은 시간.
     */
    val restTimeLeft: Float
        get() = when {
            state == PetState.IDLE -> stateTimeLeft.coerceAtLeast(0f)
            state == PetState.SLEEP && animTime >= fallAsleepSec -> {
                val toNextFrame = SLEEP_LOOP_SEC - (animTime - fallAsleepSec) % SLEEP_LOOP_SEC
                minOf(toNextFrame, stateTimeLeft).coerceAtLeast(0f)
            }
            else -> 0f
        }

    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f            // 화면에서 보이는 맨 위 (상태 표시줄 바로 아래)
    private var groundY = 0f         // 바닥. 화면 맨 위보다 높을 수도 있다
    private var velocityX = 0f       // 던져졌을 때 옆으로 가는 속도 (초당 px)
    private var velocityY = 0f       // 떨어지는 속도 (초당 px)
    private var stateTimeLeft = 0f   // 현재 행동의 남은 시간 (초)
    private var animTime = 0f        // 현재 행동을 시작한 뒤 지난 시간 (초)

    /** 끌려 다니거나 공중에 떠 있는 중인지 */
    private val isInAir: Boolean
        get() = state == PetState.DRAGGED || state == PetState.FALL || state == PetState.THROWN

    /** 끌 수 있는 가장 높은 위치: 화면 맨 위와 바닥 중 더 높은 쪽 */
    private val topLimit: Float
        get() = minOf(minY, groundY)

    fun setBounds(minX: Float, maxX: Float, minY: Float, groundY: Float) {
        this.minX = minX
        this.maxX = maxOf(minX, maxX)
        this.minY = minY
        // 바닥 높이에 제한을 두지 않는다. 화면 위로 넘어가면 점점 가려진다
        this.groundY = groundY
        x = x.coerceIn(this.minX, this.maxX)
        y = if (isInAir) {
            y.coerceIn(topLimit, this.groundY)
        } else {
            this.groundY
        }
    }

    fun placeInMiddle() {
        x = (minX + maxX) / 2f
        y = groundY
        enter(PetState.IDLE)
    }

    /** 잠들어 있던 동안 흐른 시간을 쉬는 시간에서 뺀다 */
    fun passTime(sec: Float) {
        when (state) {
            PetState.IDLE -> stateTimeLeft -= sec
            PetState.SLEEP -> {
                stateTimeLeft -= sec
                animTime += sec       // 숨쉬기 컷도 그만큼 넘어간다
            }
            else -> Unit
        }
    }

    /** 손가락으로 누른 순간: 공중에서 휘적거리기 시작 */
    fun startDrag() {
        velocityX = 0f
        velocityY = 0f
        enter(PetState.DRAGGED)
    }

    /** 손가락을 따라 이동 (화면 안, 바닥 위로 제한) */
    fun dragTo(newX: Float, newY: Float) {
        if (state != PetState.DRAGGED) return
        x = newX.coerceIn(minX, maxX)
        y = newY.coerceIn(topLimit, groundY)
    }

    /**
     * 손가락을 뗀 순간.
     * 손가락이 빠르게 움직이고 있었으면(vx, vy: 초당 px) 그 속도로 날아가고,
     * 아니면 그 자리에서 바닥으로 떨어진다.
     */
    fun release(vx: Float = 0f, vy: Float = 0f) {
        if (state != PetState.DRAGGED) return
        val speed = hypot(vx, vy)
        if (speed >= MIN_THROW_DP * density) {
            // 너무 빠르면 눈 깜짝할 새 사라지지 않도록 줄인다
            val maxSpeed = MAX_THROW_DP * density
            val scale = if (speed > maxSpeed) maxSpeed / speed else 1f
            velocityX = vx * scale
            velocityY = vy * scale
            enter(PetState.THROWN)
        } else {
            velocityX = 0f
            velocityY = 0f
            enter(PetState.FALL)
        }
    }

    /** 시험용: 땅 위에 있으면 바로 잠들기 시작한다. 잠들었으면 true */
    fun sleepNow(): Boolean {
        if (definition.sleepStrip == null || isInAir) return false
        enter(PetState.SLEEP)
        return true
    }

    /**
     * 폰을 흔들었을 때: 위쪽 아무 방향으로 휙 날아올라 벽·천장에 튕기다 내려온다.
     * 계속 흔들면 공중에서 또 차올라서 계속 날아다닌다. 손가락으로 잡고 있을 때는 무시한다.
     */
    fun fling() {
        if (state == PetState.DRAGGED) return
        val speed = randomBetween(FLING_MIN_DP, FLING_MAX_DP) * density
        val angle = Math.toRadians(randomBetween(FLING_MIN_ANGLE, FLING_MAX_ANGLE).toDouble())
        velocityX = (cos(angle) * speed).toFloat()
        velocityY = (-sin(angle) * speed).toFloat()   // 화면에서는 위쪽이 -
        enter(PetState.THROWN)
    }

    /** dt: 지난 프레임 이후 흐른 시간 (초) */
    fun update(dt: Float) {
        animTime += dt

        when (state) {
            PetState.DRAGGED -> return               // 위치는 손가락이 정한다
            PetState.FALL, PetState.THROWN -> {
                updateFall(dt)
                return
            }
            PetState.WALK -> if (updateWalk(dt)) return
            PetState.IDLE, PetState.SLEEP, PetState.WAKE -> Unit
        }

        stateTimeLeft -= dt
        if (stateTimeLeft <= 0f) chooseNextState()
    }

    /** 지금 보여 줄 컷 */
    fun currentFrame(): Frame = when (state) {
        PetState.IDLE, PetState.FALL -> Frame(Anim.IDLE, 0)
        PetState.WALK -> Frame(
            Anim.WALK,
            (animTime * definition.walkFps).toInt() % definition.walkFrameCount
        )
        // 끌려 다닐 때와 던져져 날아갈 때는 팔을 휘적거린다
        PetState.DRAGGED, PetState.THROWN -> Frame(
            Anim.FLAP,
            (animTime * definition.flapFps).toInt() % definition.flapFrameCount
        )
        // 한 컷씩 점점 잠들고, 다 잠들면 마지막 두 컷을 번갈아 보여 준다 (숨쉬기)
        PetState.SLEEP -> Frame(
            Anim.SLEEP,
            if (animTime < fallAsleepSec) {
                (animTime * SLEEP_FPS).toInt().coerceAtMost(sleepFrames - 1)
            } else {
                sleepFrames - 2 + ((animTime - fallAsleepSec) / SLEEP_LOOP_SEC).toInt() % 2
            }
        )
        // 깰 때는 잠드는 컷을 거꾸로 빠르게 보여 준다
        PetState.WAKE -> Frame(
            Anim.SLEEP,
            (sleepFrames - 3 - (animTime * WAKE_FPS).toInt()).coerceAtLeast(0)
        )
    }

    /** 중력으로 떨어진다. 던져졌으면 옆으로도 날아가고 벽·천장에 튕긴다 */
    private fun updateFall(dt: Float) {
        velocityY += definition.gravityDp * density * dt
        x += velocityX * dt
        y += velocityY * dt

        // 왼쪽·오른쪽 벽에 닿으면 튕겨 나온다 (튕길 때마다 조금씩 느려진다)
        if (x < minX) {
            x = minX
            velocityX = -velocityX * BOUNCE
        } else if (x > maxX) {
            x = maxX
            velocityX = -velocityX * BOUNCE
        }
        // 화면 위쪽에 닿으면 아래로 튕긴다
        if (y < topLimit && velocityY < 0f) {
            y = topLimit
            velocityY = -velocityY * BOUNCE
        }
        // 바닥에 닿으면 멈춘다
        if (y >= groundY) {
            y = groundY
            velocityX = 0f
            velocityY = 0f
            enter(PetState.IDLE)
        }
    }

    /** 벽에 닿아 멈췄으면 true */
    private fun updateWalk(dt: Float): Boolean {
        val speedPx = definition.walkSpeedDp * density
        x += if (facingRight) speedPx * dt else -speedPx * dt

        val hitEdge = (facingRight && x >= maxX) || (!facingRight && x <= minX)
        if (hitEdge) {
            x = x.coerceIn(minX, maxX)
            facingRight = !facingRight   // 다음에는 반대쪽으로
            enter(PetState.IDLE)
            return true
        }
        return false
    }

    private fun chooseNextState() {
        when (state) {
            // 다 쉬었으면 걷는다. 잠자기를 쓸 수 있으면 가끔은 잠든다
            PetState.IDLE ->
                if (sleepPossible && (walkFrequency <= 0 || random.nextFloat() < SLEEP_CHANCE)) {
                    enter(PetState.SLEEP)
                } else {
                    startWalk()
                }
            PetState.SLEEP -> enter(PetState.WAKE)   // 다 잤으면 깨어난다
            else -> enter(PetState.IDLE)
        }
    }

    private fun startWalk() {
        facingRight = when {
            x <= minX + 1f -> true          // 왼쪽 끝이면 오른쪽으로
            x >= maxX - 1f -> false         // 오른쪽 끝이면 왼쪽으로
            else -> random.nextBoolean()
        }
        enter(PetState.WALK)
    }

    /**
     * 쉬는 시간을 무작위로 정한다.
     * 평균은 (15 ÷ 빈도)초이고, 매번 그 0.5 ~ 1.5배 사이에서 달라진다.
     * 빈도가 0이면 걷지 않고 계속 쉰다. 잠자기를 쓸 수 있으면 한참 쉬다가 잠든다.
     */
    private fun randomRest(): Float {
        if (walkFrequency <= 0) {
            return if (sleepPossible) randomBetween(DOZE_OFF_MIN_SEC, DOZE_OFF_MAX_SEC)
            else Float.POSITIVE_INFINITY
        }
        val average = REST_BASE_SEC / walkFrequency
        return average * (0.5f + random.nextFloat())
    }

    private fun enter(newState: PetState) {
        state = newState
        animTime = 0f
        stateTimeLeft = when (newState) {
            PetState.IDLE -> randomRest()
            PetState.WALK -> 2f + random.nextFloat() * 3f       // 2 ~ 5초
            PetState.DRAGGED, PetState.FALL, PetState.THROWN -> 0f   // 시간 제한 없음
            PetState.SLEEP -> fallAsleepSec + randomBetween(SLEEP_MIN_SEC, SLEEP_MAX_SEC)
            PetState.WAKE -> wakeUpSec
        }
    }

    private fun randomBetween(min: Float, max: Float): Float = min + random.nextFloat() * (max - min)

    companion object {
        private const val SLEEP_CHANCE = 0.3f       // 쉬고 난 뒤 걷는 대신 잠들 확률
        private const val SLEEP_FPS = 2f            // 잠드는 컷이 바뀌는 속도 (초당 장수)
        private const val WAKE_FPS = 8f             // 깨어나는 컷이 바뀌는 속도
        private const val SLEEP_LOOP_SEC = 1.2f     // 잠든 뒤 숨쉬기 컷이 바뀌는 간격
        private const val SLEEP_MIN_SEC = 30f       // 잠자는 시간 (초)
        private const val SLEEP_MAX_SEC = 60f
        private const val DOZE_OFF_MIN_SEC = 20f    // 걷지 않는 펫이 잠들 때까지 쉬는 시간
        private const val DOZE_OFF_MAX_SEC = 40f
        private const val REST_BASE_SEC = 15f
        private const val MIN_THROW_DP = 600f    // 이보다 느리게 놓으면 그냥 떨어진다 (초당 dp)
        private const val MAX_THROW_DP = 4000f   // 던지는 속도 최대치 (초당 dp)
        private const val BOUNCE = 0.6f          // 벽에 튕길 때 남는 속도 비율
        private const val FLING_MIN_DP = 1600f   // 폰을 흔들었을 때 날아오르는 속도 (초당 dp)
        private const val FLING_MAX_DP = 2800f
        private const val FLING_MIN_ANGLE = 30f  // 날아오르는 방향 (0도 = 오른쪽, 90도 = 바로 위)
        private const val FLING_MAX_ANGLE = 150f
    }
}
