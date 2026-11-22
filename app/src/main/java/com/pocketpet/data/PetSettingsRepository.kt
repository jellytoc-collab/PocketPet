package com.pocketpet.data

import android.content.Context
import android.content.SharedPreferences

/** 펫 설정을 폰에 저장하고 불러온다 */
class PetSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 선택한 펫 */
    var selectedPetId: String
        get() = prefs.getString(KEY_PET, DEFAULT_PET) ?: DEFAULT_PET
        set(value) {
            prefs.edit().putString(KEY_PET, value).apply()
        }

    /** 이용 등급 (일반 / VIP / VIP+) */
    var tier: Tier
        get() = Tier.fromName(prefs.getString(KEY_TIER, null))
        set(value) {
            prefs.edit().putString(KEY_TIER, value.name).apply()
        }

    /** 크기 (기본 크기 대비 %) — 최솟값만 있고 최댓값은 없다 */
    var sizePercent: Int
        get() = prefs.getInt(KEY_SIZE, DEFAULT_SIZE_PERCENT)
        set(value) {
            prefs.edit().putInt(KEY_SIZE, value.coerceAtLeast(MIN_SIZE_PERCENT)).apply()
        }

    /** 화면에 표시되는 키 (dp) */
    val sizeDp: Int
        get() = BASE_SIZE_DP * sizePercent / 100

    /** 바닥을 화면 아래에서 띄우는 높이 (dp) */
    var floorDp: Int
        get() = prefs.getInt(KEY_FLOOR, DEFAULT_FLOOR_DP)
        set(value) {
            prefs.edit().putInt(KEY_FLOOR, value.coerceAtLeast(0)).apply()
        }

    /** 걷는 빈도: 클수록 자주 걷는다. 0이면 걷지 않는다 */
    var walkFrequency: Int
        get() = prefs.getInt(KEY_WALK_FREQ, DEFAULT_WALK_FREQ)
        set(value) {
            prefs.edit().putInt(KEY_WALK_FREQ, value.coerceAtLeast(0)).apply()
        }

    /** 설정 화면의 값을 모두 기본값으로 되돌린다 (선택한 펫은 그대로 둔다) */
    fun resetToDefaults() {
        prefs.edit()
            .remove(KEY_SIZE)
            .remove(KEY_FLOOR)
            .remove(KEY_WALK_FREQ)
            .apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS_NAME = "pet_settings"
        const val KEY_PET = "selected_pet"
        const val KEY_SIZE = "size_percent"
        const val KEY_FLOOR = "floor_dp"
        const val KEY_WALK_FREQ = "walk_frequency"
        const val KEY_TIER = "tier"

        const val DEFAULT_PET = "shiro"

        const val BASE_SIZE_DP = 120
        const val DEFAULT_SIZE_PERCENT = 100
        const val MIN_SIZE_PERCENT = 10

        const val DEFAULT_FLOOR_DP = 0
        const val DEFAULT_WALK_FREQ = 5
    }
}
