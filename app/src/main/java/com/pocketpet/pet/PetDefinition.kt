package com.pocketpet.pet

import com.pocketpet.R
import com.pocketpet.data.Tier

/** 펫 한 마리의 외형과 행동 설정 */
data class PetDefinition(
    val id: String,
    val nameRes: Int,               // 이름 (폰 언어에 따라 바뀜)
    val idle: Int,                  // 가만히 있기 그림 (1장)
    val walkStrip: Int,             // 걷기 컷을 가로로 이어 붙인 그림
    val flapStrip: Int,             // 팔 휘적거리기 컷을 가로로 이어 붙인 그림
    val walkFrameCount: Int = 8,    // 걷기 컷 수
    val flapFrameCount: Int = 8,    // 휘적거리기 컷 수
    val walkSpeedDp: Float = 20f,   // 걷는 속도 (초당 dp)
    val walkFps: Float = 6f,        // 걷기 그림이 바뀌는 속도 (초당 장수)
    val flapFps: Float = 12f,       // 휘적거리기 그림이 바뀌는 속도 (초당 장수)
    val gravityDp: Float = 2500f,   // 떨어질 때 중력 가속도
    val heightDp: Int = 120,        // 화면에 표시되는 키
    val free: Boolean = false,      // 일반 등급에서도 쓸 수 있는 펫인지
    val sleepStrip: Int? = null,    // 잠드는 컷을 가로로 이어 붙인 그림 (없으면 잠자지 않음)
    val sleepFrameCount: Int = 8,   // 잠드는 컷 수
    val talk: Int? = null           // 말풍선 대사 목록 (res/values*/talk.xml, VIP+)
)

object Pets {
    val ALL = listOf(
        PetDefinition("shiro", R.string.pet_shiro, R.drawable.shiro_idle, R.drawable.shiro_walk, R.drawable.shiro_flap, free = true, sleepStrip = R.drawable.shiro_sleep, talk = R.array.talk_shiro),
        PetDefinition("kuro", R.string.pet_kuro, R.drawable.kuro_idle, R.drawable.kuro_walk, R.drawable.kuro_flap, free = true, sleepStrip = R.drawable.kuro_sleep, talk = R.array.talk_kuro),
        PetDefinition("momo", R.string.pet_momo, R.drawable.momo_idle, R.drawable.momo_walk, R.drawable.momo_flap, sleepStrip = R.drawable.momo_sleep, talk = R.array.talk_momo),
        PetDefinition("luna", R.string.pet_luna, R.drawable.luna_idle, R.drawable.luna_walk, R.drawable.luna_flap, sleepStrip = R.drawable.luna_sleep, talk = R.array.talk_luna),
        PetDefinition("tashu", R.string.pet_tashu, R.drawable.tashu_idle, R.drawable.tashu_walk, R.drawable.tashu_flap, sleepStrip = R.drawable.tashu_sleep, talk = R.array.talk_tashu),
        PetDefinition("mashi", R.string.pet_mashi, R.drawable.mashi_idle, R.drawable.mashi_walk, R.drawable.mashi_flap, sleepStrip = R.drawable.mashi_sleep, talk = R.array.talk_mashi),
        PetDefinition("hari", R.string.pet_hari, R.drawable.hari_idle, R.drawable.hari_walk, R.drawable.hari_flap, sleepStrip = R.drawable.hari_sleep, talk = R.array.talk_hari),
        PetDefinition("miru", R.string.pet_miru, R.drawable.miru_idle, R.drawable.miru_walk, R.drawable.miru_flap, sleepStrip = R.drawable.miru_sleep, talk = R.array.talk_miru),
        PetDefinition("leon", R.string.pet_leon, R.drawable.leon_idle, R.drawable.leon_walk, R.drawable.leon_flap, sleepStrip = R.drawable.leon_sleep, talk = R.array.talk_leon),
        PetDefinition("noel", R.string.pet_noel, R.drawable.noel_idle, R.drawable.noel_walk, R.drawable.noel_flap, sleepStrip = R.drawable.noel_sleep, talk = R.array.talk_noel),
        PetDefinition("neon", R.string.pet_neon, R.drawable.neon_idle, R.drawable.neon_walk, R.drawable.neon_flap, sleepStrip = R.drawable.neon_sleep, talk = R.array.talk_neon),
        PetDefinition("yuki", R.string.pet_yuki, R.drawable.yuki_idle, R.drawable.yuki_walk, R.drawable.yuki_flap, sleepStrip = R.drawable.yuki_sleep, talk = R.array.talk_yuki),
        PetDefinition("maren", R.string.pet_maren, R.drawable.maren_idle, R.drawable.maren_walk, R.drawable.maren_flap, sleepStrip = R.drawable.maren_sleep, talk = R.array.talk_maren)
    )

    fun byId(id: String?): PetDefinition = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /** 이 등급에서 쓸 수 있는 펫인지 (잠기지 않았는지) */
    fun isUnlocked(pet: PetDefinition, tier: Tier): Boolean = pet.free || tier.allPets

    /** 고른 펫이 잠겨 있으면 첫 번째 무료 펫(시로)을 대신 돌려준다 */
    fun usable(id: String?, tier: Tier): PetDefinition {
        val pet = byId(id)
        return if (isUnlocked(pet, tier)) pet else ALL.first { it.free }
    }
}
