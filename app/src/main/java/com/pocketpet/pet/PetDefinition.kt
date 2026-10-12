package com.pocketpet.pet

import com.pocketpet.R

/** 펫 한 마리의 외형과 행동 설정 */
data class PetDefinition(
    val id: String,
    val name: String,
    val idleFrames: List<Int>,
    val walkFrames: List<Int>,
    val walkSpeedDp: Float,   // 걷는 속도 (초당 dp)
    val walkFps: Float,       // 걷기 그림이 바뀌는 속도 (초당 장수)
    val heightDp: Int         // 화면에 표시되는 키
)

object Pets {
    val SHIRO = PetDefinition(
        id = "shiro",
        name = "시로",
        idleFrames = listOf(R.drawable.pet_idle_01),
        walkFrames = listOf(R.drawable.pet_walk_01, R.drawable.pet_walk_02),
        walkSpeedDp = 40f,
        walkFps = 4f,
        heightDp = 120
    )
}
