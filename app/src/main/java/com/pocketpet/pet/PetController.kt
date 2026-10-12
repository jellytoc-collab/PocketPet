package com.pocketpet.pet

import kotlin.random.Random

enum class PetState { IDLE, WALK }

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
    var state = PetState.IDLE
        private set
    var facingRight = true
        private set

    private var minX = 0f
    private var maxX = 0f
    private var stateTimeLeft = 0f   // 현재 행동의 남은 시간 (초)
    private var animTime = 0f        // 현재 행동을 시작한 뒤 지난 시간 (초)

    fun setBounds(minX: Float, maxX: Float) {
        this.minX = minX
        this.maxX = maxOf(minX, maxX)
        x = x.coerceIn(this.minX, this.maxX)
    }

    fun placeInMiddle() {
        x = (minX + maxX) / 2f
        enter(PetState.IDLE)
    }

    /** dt: 지난 프레임 이후 흐른 시간 (초) */
    fun update(dt: Float) {
        stateTimeLeft -= dt
        animTime += dt

        if (state == PetState.WALK) {
            val speedPx = definition.walkSpeedDp * density
            x += if (facingRight) speedPx * dt else -speedPx * dt

            val hitEdge = (facingRight && x >= maxX) || (!facingRight && x <= minX)
            if (hitEdge) {
                x = x.coerceIn(minX, maxX)
                facingRight = !facingRight   // 다음에는 반대쪽으로
                enter(PetState.IDLE)
                return
            }
        }

        if (stateTimeLeft <= 0f) chooseNextState()
    }

    /** 지금 보여 줄 그림 */
    fun currentFrame(): Int {
        val frames = if (state == PetState.WALK) definition.walkFrames else definition.idleFrames
        val index = (animTime * definition.walkFps).toInt() % frames.size
        return frames[index]
    }

    private fun chooseNextState() {
        when (state) {
            PetState.IDLE -> if (random.nextFloat() < 0.65f) startWalk() else enter(PetState.IDLE)
            PetState.WALK -> enter(PetState.IDLE)
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
            PetState.IDLE -> 1.5f + random.nextFloat() * 2.5f   // 1.5 ~ 4초
            PetState.WALK -> 2f + random.nextFloat() * 3f       // 2 ~ 5초
        }
    }
}
