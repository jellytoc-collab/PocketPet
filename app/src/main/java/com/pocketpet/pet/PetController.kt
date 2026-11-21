package com.pocketpet.pet

import kotlin.math.hypot
import kotlin.random.Random

enum class PetState { IDLE, WALK, DRAGGED, FALL, THROWN }

/** 어떤 그림 묶음의 몇 번째 컷을 보여 줄지 */
enum class Anim { IDLE, WALK, FLAP }
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

    /** 쉬는 중이면 남은 쉬는 시간(초), 아니면 0 */
    val restTimeLeft: Float
        get() = if (state == PetState.IDLE) stateTimeLeft.coerceAtLeast(0f) else 0f

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
        if (state == PetState.IDLE) stateTimeLeft -= sec
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
            PetState.IDLE -> Unit
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
            PetState.IDLE -> startWalk()          // 다 쉬었으면 걷는다
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
     * 빈도가 0이면 걷지 않고 계속 쉰다.
     */
    private fun randomRest(): Float {
        if (walkFrequency <= 0) return Float.POSITIVE_INFINITY
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
        }
    }

    companion object {
        private const val REST_BASE_SEC = 15f
        private const val MIN_THROW_DP = 600f    // 이보다 느리게 놓으면 그냥 떨어진다 (초당 dp)
        private const val MAX_THROW_DP = 4000f   // 던지는 속도 최대치 (초당 dp)
        private const val BOUNCE = 0.6f          // 벽에 튕길 때 남는 속도 비율
    }
}
