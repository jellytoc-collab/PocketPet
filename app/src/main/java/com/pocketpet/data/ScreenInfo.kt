package com.pocketpet.data

import android.content.Context
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager

object ScreenInfo {

    /** 펫 아래쪽 기본 여백 (PetOverlayManager와 같은 값) */
    private const val BOTTOM_MARGIN_DP = 8

    /**
     * 바닥 높이 때문에 펫이 화면 위로 완전히 나가서 보이지 않으면 true.
     * 펫의 발끝(아래쪽 끝)이 화면 맨 위보다 높으면 보이지 않는다.
     */
    fun isPetHidden(context: Context, floorDp: Int): Boolean {
        val density = context.resources.displayMetrics.density
        val usableBottom = usableBottomPx(context)
        val petBottom = usableBottom - (BOTTOM_MARGIN_DP + floorDp) * density
        return petBottom <= 0f
    }

    /** 내비게이션 바를 뺀 화면 아래쪽 끝 위치 (px) */
    private fun usableBottomPx(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(WindowManager::class.java)
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            metrics.bounds.bottom - insets.bottom
        } else {
            context.resources.displayMetrics.heightPixels
        }
    }
}
