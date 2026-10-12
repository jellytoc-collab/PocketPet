package com.pocketpet.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import com.pocketpet.R

class PetOverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var petView: ImageView? = null

    fun show() {
        // 이미 펫이 떠 있으면 새로 만들지 않는다 (중복 생성 방지)
        if (petView != null) return

        val density = context.resources.displayMetrics.density
        val drawable = context.getDrawable(R.drawable.pet_idle_01) ?: return

        // 그림의 가로세로 비율을 유지해서 창 크기를 정한다
        val heightPx = (PET_HEIGHT_DP * density).toInt()
        val widthPx = heightPx * drawable.intrinsicWidth / drawable.intrinsicHeight

        val view = ImageView(context).apply {
            setImageDrawable(drawable)
        }

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (START_X_DP * density).toInt()
            y = (START_Y_DP * density).toInt()
        }

        windowManager.addView(view, params)
        petView = view
    }

    fun hide() {
        petView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: IllegalArgumentException) {
                // 이미 제거된 경우는 무시한다
            }
        }
        petView = null
    }

    companion object {
        private const val PET_HEIGHT_DP = 120
        private const val START_X_DP = 40
        private const val START_Y_DP = 200
    }
}
