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

    /** 한 번 걷고 나서 쉬는 시간 (초) */
    var restSec: Float
        get() = prefs.getFloat(KEY_REST, DEFAULT_REST_SEC)
        set(value) {
            prefs.edit().putFloat(KEY_REST, value.coerceAtLeast(0f)).apply()
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
        const val KEY_REST = "rest_sec"

        const val DEFAULT_PET = "shiro"

        const val BASE_SIZE_DP = 120
        const val DEFAULT_SIZE_PERCENT = 100
        const val MIN_SIZE_PERCENT = 10

        const val DEFAULT_FLOOR_DP = 0
        const val DEFAULT_REST_SEC = 3f
    }
}
