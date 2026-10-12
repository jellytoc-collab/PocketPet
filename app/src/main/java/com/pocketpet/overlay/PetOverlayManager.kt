package com.pocketpet.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import com.pocketpet.pet.PetController
import com.pocketpet.pet.PetDefinition
import com.pocketpet.pet.PetState

class PetOverlayManager(
    private val context: Context,
    private val definition: PetDefinition
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val controller = PetController(definition, density)
    private val drawables = HashMap<Int, Drawable>()

    private var petView: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private var widthPx = 0
    private var heightPx = 0
    private var groundY = 0
    private var currentFrameRes = 0
    private var lastFrameTimeNanos = 0L
    private var running = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            onFrame(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun show() {
        // 이미 펫이 떠 있으면 새로 만들지 않는다 (중복 생성 방지)
        if (petView != null) return

        val firstFrame = definition.idleFrames.first()
        val drawable = drawableFor(firstFrame) ?: return

        // 그림의 가로세로 비율을 유지해서 창 크기를 정한다
        heightPx = (definition.heightDp * density).toInt()
        widthPx = heightPx * drawable.intrinsicWidth / drawable.intrinsicHeight

        val view = ImageView(context).apply { setImageDrawable(drawable) }
        currentFrameRes = firstFrame

        val lp = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        updateBounds()
        controller.placeInMiddle()
        lp.x = controller.x.toInt()
        lp.y = groundY

        windowManager.addView(view, lp)
        petView = view
        params = lp

        running = true
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun hide() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        petView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: IllegalArgumentException) {
                // 이미 제거된 경우는 무시한다
            }
        }
        petView = null
        params = null
    }

    /** 화면 크기가 바뀌었을 때(회전 등) 이동 범위를 다시 계산한다 */
    fun refreshBounds() {
        if (petView == null) return
        updateBounds()
        applyPosition()
    }

    private fun onFrame(frameTimeNanos: Long) {
        if (lastFrameTimeNanos == 0L) {
            lastFrameTimeNanos = frameTimeNanos
            return
        }
        // 프레임 사이 시간(초). 잠시 멈췄다 돌아와도 순간이동하지 않도록 최대 0.1초로 제한
        val dt = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceAtMost(0.1f)
        lastFrameTimeNanos = frameTimeNanos

        controller.update(dt)
        applyFrame()
        applyPosition()
    }

    private fun applyFrame() {
        val view = petView ?: return
        val frame = controller.currentFrame()
        if (frame != currentFrameRes) {
            drawableFor(frame)?.let { view.setImageDrawable(it) }
            currentFrameRes = frame
        }
        // 걷기 그림은 오른쪽을 보고 있으므로, 왼쪽으로 걸을 때만 좌우를 뒤집는다
        view.scaleX = if (controller.state == PetState.WALK && !controller.facingRight) -1f else 1f
    }

    private fun applyPosition() {
        val view = petView ?: return
        val lp = params ?: return
        val newX = controller.x.toInt()
        // 위치가 그대로면 창을 다시 배치하지 않는다
        if (newX == lp.x && groundY == lp.y) return
        lp.x = newX
        lp.y = groundY
        windowManager.updateViewLayout(view, lp)
    }

    private fun updateBounds() {
        val area = usableArea()
        val bottomMargin = (BOTTOM_MARGIN_DP * density).toInt()
        controller.setBounds(area.left.toFloat(), (area.right - widthPx).toFloat())
        groundY = area.bottom - heightPx - bottomMargin
    }

    /** 상태 표시줄, 내비게이션 바, 카메라 구멍을 뺀 화면 영역 */
    private fun usableArea(): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            val b = metrics.bounds
            Rect(
                b.left + insets.left,
                b.top + insets.top,
                b.right - insets.right,
                b.bottom - insets.bottom
            )
        } else {
            // Android 10 이하: 시스템 바 크기를 대략적으로 뺀다
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
            Rect(0, (24 * density).toInt(), dm.widthPixels, dm.heightPixels - (48 * density).toInt())
        }
    }

    private fun drawableFor(res: Int): Drawable? {
        drawables[res]?.let { return it }
        val d = context.getDrawable(res) ?: return null
        drawables[res] = d
        return d
    }

    companion object {
        private const val BOTTOM_MARGIN_DP = 8
    }
}
