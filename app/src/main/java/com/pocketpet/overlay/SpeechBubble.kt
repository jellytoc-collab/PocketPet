package com.pocketpet.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * 펫 머리 위에 뜨는 말풍선 (VIP+).
 * 펫 창과 따로 작은 창을 하나 더 띄우고, 펫이 움직이면 따라간다. 터치는 통과시킨다.
 * " >> " 로 나뉜 대사는 앞부분을 보여 주고 잠깐 사라졌다가 뒷부분을 보여 준다.
 */
class SpeechBubble(private val context: Context, private val windowManager: WindowManager) {

    private val density = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    // 말풍선을 붙일 곳: 펫 머리 가운데 x, 펫 창 맨 위 y, 화면 좌우 끝
    private var centerX = 0f
    private var topY = 0f
    private var screenLeft = 0
    private var screenRight = Int.MAX_VALUE

    /** 대사를 말하는 중인지 (두 부분 대사의 사이 쉬는 시간 포함) */
    var busy = false
        private set

    /** 대사를 띄운다. 말하던 대사가 있으면 끊고 새로 말한다 */
    fun say(line: String) {
        clear()
        val parts = line.split(SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        busy = true
        showPart(parts, 0)
    }

    /** 펫 위치가 바뀌었을 때 말풍선을 따라 옮긴다 */
    fun moveTo(centerX: Float, topY: Float, screenLeft: Int, screenRight: Int) {
        this.centerX = centerX
        this.topY = topY
        this.screenLeft = screenLeft
        this.screenRight = screenRight
        if (place()) relayout()
    }

    /** 말풍선을 지우고 예약된 다음 대사도 취소한다. 여러 번 불러도 안전하다 */
    fun clear() {
        handler.removeCallbacksAndMessages(null)
        removeView()
        busy = false
    }

    private fun showPart(parts: List<String>, index: Int) {
        val text = parts[index]
        showText(text)
        handler.postDelayed({
            removeView()
            if (index + 1 < parts.size) {
                handler.postDelayed({ showPart(parts, index + 1) }, PART_GAP_MS)
            } else {
                busy = false
            }
        }, durationFor(text))
    }

    /** 글자가 많을수록 조금 더 오래 보여 준다 */
    private fun durationFor(text: String): Long =
        (BASE_MS + text.length * PER_CHAR_MS).coerceAtMost(MAX_MS)

    private fun showText(text: String) {
        removeView()
        val tail = TAIL_DP * density
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
            maxWidth = (MAX_WIDTH_DP * density).toInt()
            gravity = Gravity.CENTER
            val padH = (10 * density).toInt()
            val padV = (6 * density).toInt()
            setPadding(padH, padV, padH, padV + tail.toInt())
            background = BubbleDrawable(density, tail)
        }
        // 창 크기를 정하려고 미리 재 본다
        tv.measure(
            View.MeasureSpec.makeMeasureSpec((MAX_WIDTH_DP * density).toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val lp = WindowManager.LayoutParams(
            tv.measuredWidth,
            tv.measuredHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or      // 말풍선은 터치를 통과시킨다
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        params = lp
        place()
        try {
            windowManager.addView(tv, lp)
            view = tv
        } catch (e: RuntimeException) {
            params = null      // 권한이 꺼진 경우 등: 말풍선만 조용히 건너뛴다
        }
    }

    /** 머리 위 가운데에 오도록 위치를 계산한다. 바뀌었으면 true */
    private fun place(): Boolean {
        val lp = params ?: return false
        val maxX = (screenRight - lp.width).coerceAtLeast(screenLeft)
        val x = (centerX - lp.width / 2f).toInt().coerceIn(screenLeft, maxX)
        val y = (topY - lp.height + OVERLAP_DP * density).toInt().coerceAtLeast(0)
        if (x == lp.x && y == lp.y) return false
        lp.x = x
        lp.y = y
        return true
    }

    private fun relayout() {
        val v = view ?: return
        val lp = params ?: return
        try {
            windowManager.updateViewLayout(v, lp)
        } catch (e: IllegalArgumentException) {
            view = null
        }
    }

    private fun removeView() {
        val v = view
        view = null
        params = null
        v?.let {
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
                // 이미 제거된 경우는 무시한다
            }
        }
    }

    /** 둥근 네모 + 아래 가운데 꼬리 모양 배경 */
    private class BubbleDrawable(density: Float, private val tail: Float) : Drawable() {
        private val radius = 12 * density
        private val stroke = 1.2f * density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STROKE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val s = stroke / 2
            val bottom = b.bottom - tail - s
            val cx = b.exactCenterX()
            path.reset()
            path.addRoundRect(RectF(b.left + s, b.top + s, b.right - s, bottom), radius, radius, Path.Direction.CW)
            val tri = Path().apply {
                moveTo(cx - tail, bottom - s)
                lineTo(cx, b.bottom - s)
                lineTo(cx + tail, bottom - s)
                close()
            }
            path.op(tri, Path.Op.UNION)
            canvas.drawPath(path, fill)
            canvas.drawPath(path, line)
        }

        override fun setAlpha(alpha: Int) {
            fill.alpha = alpha
            line.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fill.colorFilter = colorFilter
            line.colorFilter = colorFilter
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        const val SPLIT = ">>"
        private const val BASE_MS = 1800L          // 대사를 보여 주는 기본 시간
        private const val PER_CHAR_MS = 90L        // 글자 하나당 더 보여 주는 시간
        private const val MAX_MS = 4500L
        private const val PART_GAP_MS = 500L       // 두 부분 대사 사이에 비워 두는 시간
        private const val TEXT_SP = 13f
        private const val MAX_WIDTH_DP = 190
        private const val TAIL_DP = 6
        private const val OVERLAP_DP = 4           // 펫 그림 위쪽의 빈 공간만큼 살짝 겹친다
        private val TEXT_COLOR = Color.rgb(0x4A, 0x3F, 0x35)
        private val STROKE_COLOR = Color.rgb(0xD9, 0xCF, 0xC4)
    }
}
