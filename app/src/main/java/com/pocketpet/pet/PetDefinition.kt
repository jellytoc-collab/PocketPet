package com.pocketpet.pet

import com.pocketpet.R

/** 펫 한 마리의 외형과 행동 설정 */
data class PetDefinition(
    val id: String,
    val name: String,
    val idleFrames: List<Int>,
    val walkFrames: List<Int>,
    val flapFrames: List<Int>,
    val walkSpeedDp: Float = 40f,   // 걷는 속도 (초당 dp)
    val walkFps: Float = 6f,        // 걷기 그림이 바뀌는 속도 (초당 장수)
    val flapFps: Float = 6f,        // 드래그 중 퍼덕이는 속도 (초당 장수)
    val gravityDp: Float = 2500f,   // 떨어질 때 중력 가속도
    val heightDp: Int = 120         // 화면에 표시되는 키
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
        )
    )

    val KURO = PetDefinition(
        id = "kuro",
        name = "쿠로",
        idleFrames = listOf(R.drawable.kuro_idle),
        walkFrames = listOf(
            R.drawable.kuro_walk_01,
            R.drawable.kuro_walk_mid,
            R.drawable.kuro_walk_02,
            R.drawable.kuro_walk_mid
        ),
        flapFrames = listOf(
            R.drawable.kuro_flap_up,
            R.drawable.kuro_flap_mid,
            R.drawable.kuro_flap_down,
            R.drawable.kuro_flap_mid
        )
    )

    val MOMO = PetDefinition(
        id = "momo",
        name = "모모",
        idleFrames = listOf(R.drawable.momo_idle),
        walkFrames = listOf(
            R.drawable.momo_walk_01,
            R.drawable.momo_walk_mid,
            R.drawable.momo_walk_02,
            R.drawable.momo_walk_mid
        ),
        flapFrames = listOf(
            R.drawable.momo_flap_up,
            R.drawable.momo_flap_mid,
            R.drawable.momo_flap_down,
            R.drawable.momo_flap_mid
        )
    )

    /** 선택 화면에서 넘겨 볼 순서 */
    val ALL = listOf(SHIRO, KURO, MOMO)

    fun byId(id: String?): PetDefinition = ALL.firstOrNull { it.id == id } ?: SHIRO
}
