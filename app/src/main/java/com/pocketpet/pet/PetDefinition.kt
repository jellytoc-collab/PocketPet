package com.pocketpet.pet

import com.pocketpet.R

/** 펫 한 마리의 외형과 행동 설정 */
data class PetDefinition(
    val id: String,
    val name: String,
    val idleFrames: List<Int>,
    val walkFrames: List<Int>,
    val flapFrames: List<Int>,
    val walkSpeedDp: Float,   // 걷는 속도 (초당 dp)
    val walkFps: Float,       // 걷기 그림이 바뀌는 속도 (초당 장수)
    val flapFps: Float,       // 드래그 중 퍼덕이는 속도 (초당 장수)
    val gravityDp: Float,     // 떨어질 때 중력 가속도 (초당 dp/초)
    val heightDp: Int         // 화면에 표시되는 키
)

object Pets {
    val SHIRO = PetDefinition(
        id = "shiro",
        name = "시로",
        idleFrames = listOf(R.drawable.pet_idle_01),
        walkFrames = listOf(
            R.drawable.pet_walk_01,
            R.drawable.pet_walk_mid,
            R.drawable.pet_walk_02,
            R.drawable.pet_walk_mid
        ),
        flapFrames = listOf(
            R.drawable.pet_flap_up,
            R.drawable.pet_flap_mid,
            R.drawable.pet_flap_down,
            R.drawable.pet_flap_mid
        ),
        walkSpeedDp = 40f,
        walkFps = 6f,
        flapFps = 14f,
        gravityDp = 2500f,
        heightDp = 120
    )
}
