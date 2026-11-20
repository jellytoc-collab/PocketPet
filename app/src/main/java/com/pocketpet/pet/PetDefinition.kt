package com.pocketpet.pet

import com.pocketpet.R

/** 펫 한 마리의 외형과 행동 설정 */
data class PetDefinition(
    val id: String,
    val name: String,
    val idle: Int,                  // 가만히 있기 그림 (1장)
    val walkStrip: Int,             // 걷기 컷을 가로로 이어 붙인 그림
    val flapStrip: Int,             // 팔 휘적거리기 컷을 가로로 이어 붙인 그림
    val walkFrameCount: Int = 8,    // 걷기 컷 수
    val flapFrameCount: Int = 8,    // 휘적거리기 컷 수
    val walkSpeedDp: Float = 40f,   // 걷는 속도 (초당 dp)
    val walkFps: Float = 12f,       // 걷기 그림이 바뀌는 속도 (초당 장수)
    val flapFps: Float = 12f,       // 휘적거리기 그림이 바뀌는 속도 (초당 장수)
    val gravityDp: Float = 2500f,   // 떨어질 때 중력 가속도
    val heightDp: Int = 120         // 화면에 표시되는 키
)

object Pets {
    val ALL = listOf(
        PetDefinition("shiro", "시로", R.drawable.shiro_idle, R.drawable.shiro_walk, R.drawable.shiro_flap),
        PetDefinition("kuro", "쿠로", R.drawable.kuro_idle, R.drawable.kuro_walk, R.drawable.kuro_flap),
        PetDefinition("momo", "모모", R.drawable.momo_idle, R.drawable.momo_walk, R.drawable.momo_flap),
        PetDefinition("luna", "루나", R.drawable.luna_idle, R.drawable.luna_walk, R.drawable.luna_flap),
        PetDefinition("tashu", "타슈", R.drawable.tashu_idle, R.drawable.tashu_walk, R.drawable.tashu_flap),
        PetDefinition("mashi", "마시", R.drawable.mashi_idle, R.drawable.mashi_walk, R.drawable.mashi_flap),
        PetDefinition("hari", "하리", R.drawable.hari_idle, R.drawable.hari_walk, R.drawable.hari_flap),
        PetDefinition("miru", "미루", R.drawable.miru_idle, R.drawable.miru_walk, R.drawable.miru_flap),
        PetDefinition("leon", "레온", R.drawable.leon_idle, R.drawable.leon_walk, R.drawable.leon_flap),
        PetDefinition("noel", "노엘", R.drawable.noel_idle, R.drawable.noel_walk, R.drawable.noel_flap),
        PetDefinition("neon", "네온", R.drawable.neon_idle, R.drawable.neon_walk, R.drawable.neon_flap),
        PetDefinition("yuki", "유키", R.drawable.yuki_idle, R.drawable.yuki_walk, R.drawable.yuki_flap),
        PetDefinition("maren", "마렌", R.drawable.maren_idle, R.drawable.maren_walk, R.drawable.maren_flap)
    )

    fun byId(id: String?): PetDefinition = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
