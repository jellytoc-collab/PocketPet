package com.pocketpet.data

import android.content.Context
import android.content.SharedPreferences

/** 펫 설정을 폰에 저장하고 불러온다 */
class PetSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 펫의 키 (dp) */
    var sizeDp: Int
        get() = prefs.getInt(KEY_SIZE_DP, DEFAULT_SIZE_DP)
        set(value) {
            prefs.edit().putInt(KEY_SIZE_DP, value.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)).apply()
        }

    /** 움직임 빈도: 1(아주 얌전함) ~ 5(아주 활발함) */
    var activityLevel: Int
        get() = prefs.getInt(KEY_ACTIVITY, DEFAULT_ACTIVITY)
        set(value) {
            prefs.edit().putInt(KEY_ACTIVITY, value.coerceIn(MIN_ACTIVITY, MAX_ACTIVITY)).apply()
        }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS_NAME = "pet_settings"
        const val KEY_SIZE_DP = "size_dp"
        const val KEY_ACTIVITY = "activity_level"

        const val MIN_SIZE_DP = 80
        const val MAX_SIZE_DP = 200
        const val DEFAULT_SIZE_DP = 120

        const val MIN_ACTIVITY = 1
        const val MAX_ACTIVITY = 5
        const val DEFAULT_ACTIVITY = 3
    }
}
