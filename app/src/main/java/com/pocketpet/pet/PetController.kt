package com.pocketpet.pet

import kotlin.random.Random

enum class PetState { IDLE, WALK, DRAGGED, FALL }

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
    
    /** 움직임 빈도: 1(아주 얌전함) ~ 5(아주 활발함) */
    var activityLevel = 3
        set(value) {
            field = value.coerceIn(1, 5)
        }

    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var groundY = 0f
    private var velocityY = 0f       // 떨어지는 속도 (초당 px)
    private var stateTimeLeft = 0f   // 현재 행동의 남은 시간 (초)
    private var animTime = 0f        // 현재 행동을 시작한 뒤 지난 시간 (초)

    fun setBounds(minX: Float, maxX: Float, minY: Float, groundY: Float) {
        this.minX = minX
        this.maxX = maxOf(minX, maxX)
        this.minY = minY
        this.groundY = maxOf(minY, groundY)
        x = x.coerceIn(this.minX, this.maxX)
        y = if (state == PetState.DRAGGED || state == PetState.FALL) {
            y.coerceIn(this.minY, this.groundY)
        } else {
            this.groundY
        }
    }

    fun placeInMiddle() {
        x = (minX + maxX) / 2f
        y = groundY
        enter(PetState.IDLE)
    }

    /** 손가락으로 누른 순간: 공중에서 퍼덕이기 시작 */
    fun startDrag() {
        velocityY = 0f
        enter(PetState.DRAGGED)
    }

    /** 손가락을 따라 이동 (화면 안, 바닥 위로 제한) */
    fun dragTo(newX: Float, newY: Float) {
        if (state != PetState.DRAGGED) return
        x = newX.coerceIn(minX, maxX)
        y = newY.coerceIn(minY, groundY)
    }

    /** 손가락을 뗀 순간: 바닥으로 떨어지기 시작 */
    fun release() {
        if (state != PetState.DRAGGED) return
        velocityY = 0f
        enter(PetState.FALL)
    }

    /** dt: 지난 프레임 이후 흐른 시간 (초) */
    fun update(dt: Float) {
        animTime += dt

        when (state) {
            PetState.DRAGGED -> return               // 위치는 손가락이 정한다
            PetState.FALL -> {
                updateFall(dt)
                return
            }
            PetState.WALK -> if (updateWalk(dt)) return
            PetState.IDLE -> Unit
        }

        stateTimeLeft -= dt
        if (stateTimeLeft <= 0f) chooseNextState()
    }

    /** 지금 보여 줄 그림 */
    fun currentFrame(): Int {
        val (frames, fps) = when (state) {
            PetState.IDLE, PetState.FALL -> definition.idleFrames to 1f
            PetState.WALK -> definition.walkFrames to definition.walkFps
            PetState.DRAGGED -> definition.flapFrames to definition.flapFps
        }
        val index = (animTime * fps).toInt() % frames.size
        return frames[index]
    }

    private fun updateFall(dt: Float) {
        velocityY += definition.gravityDp * density * dt
        y += velocityY * dt
        if (y >= groundY) {
            y = groundY
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
                PetState.IDLE -> {
                // 활발할수록 쉬고 나서 걸을 확률이 높다 (35% ~ 95%)
                val walkChance = 0.35f + 0.15f * (activityLevel - 1)
                if (random.nextFloat() < walkChance) startWalk() else enter(PetState.IDLE)
            }
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

    private fun enter(newState: PetState) {
        state = newState
        animTime = 0f
        stateTimeLeft = when (newState) {
                PetState.IDLE -> {
                // 활발할수록 짧게 쉰다 (보통일 때 1.5 ~ 4초)
                val restScale = 1f + 0.35f * (3 - activityLevel)
                (1.5f + random.nextFloat() * 2.5f) * restScale
            }
            PetState.WALK -> 2f + random.nextFloat() * 3f       // 2 ~ 5초
            PetState.DRAGGED, PetState.FALL -> 0f               // 시간 제한 없음
        }
    }
}
