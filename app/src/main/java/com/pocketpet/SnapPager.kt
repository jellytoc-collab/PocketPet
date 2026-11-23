package com.pocketpet

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.HorizontalScrollView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 카드를 한 장씩 딱딱 넘기는 가로 스크롤.
 * 손가락이 버튼 위에서 시작해도 모든 터치를 직접 지켜보기 때문에 넘김이 씹히지 않는다.
 * - 휙 밀면(빠르면) 조금만 움직여도 다음 카드로
 * - 천천히 밀면 카드 너비의 [DISTANCE_RATIO] 이상 움직였을 때만 다음 카드로
 */
class SnapPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    /** 카드 한 장의 너비(px). 코드에서 정한다 */
    var pageWidth = 0

    /** 카드 수 */
    var pageCount = 1

    private var startPage = 0
    private var tracker: VelocityTracker? = null
    private val flingVelocity = FLING_DP * resources.displayMetrics.density

    /** 지금 가장 가까운 카드 번호 */
    val currentPage: Int
        get() = if (pageWidth == 0) 0
        else (scrollX.toFloat() / pageWidth).roundToInt().coerceIn(0, pageCount - 1)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            startPage = currentPage
            tracker?.recycle()
            tracker = VelocityTracker.obtain()
        }
        tracker?.addMovement(ev)

        val handled = super.dispatchTouchEvent(ev)

        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            val t = tracker
            t?.computeCurrentVelocity(1000)
            val velocityX = t?.xVelocity ?: 0f
            t?.recycle()
            tracker = null
            snap(velocityX)
        }
        return handled
    }

    /** 기본 관성 스크롤은 끈다 — 대신 손을 뗄 때 카드에 맞춰 넘긴다 */
    override fun fling(velocityX: Int) {}

    private fun snap(velocityX: Float) {
        if (pageWidth == 0) return
        val moved = scrollX - startPage * pageWidth
        val target = when {
            // 손가락이 왼쪽으로 빠르게 → 다음 카드, 오른쪽으로 빠르게 → 이전 카드
            velocityX < -flingVelocity && moved > 0 -> startPage + 1
            velocityX > flingVelocity && moved < 0 -> startPage - 1
            abs(moved) > pageWidth * DISTANCE_RATIO -> if (moved > 0) startPage + 1 else startPage - 1
            else -> startPage
        }.coerceIn(0, pageCount - 1)
        smoothScrollTo(target * pageWidth, 0)
    }

    companion object {
        private const val FLING_DP = 250f        // 이 속도(초당 dp)보다 빠르면 ‘휙’으로 본다
        private const val DISTANCE_RATIO = 0.3f  // 천천히 밀 때 넘어가는 거리 비율
    }
}
