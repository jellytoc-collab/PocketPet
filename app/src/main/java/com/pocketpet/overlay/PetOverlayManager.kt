package com.pocketpet.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
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
    private var sizeDp = definition.heightDp
    private var currentFrameRes = 0
    private var lastFrameTimeNanos = 0L
    private var running = false

    // 누른 지점과 펫 왼쪽 위 모서리 사이의 거리 (순간이동 방지)
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f

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
        computeSize()

        val view = ImageView(context).apply {
            setImageDrawable(drawable)
            setOnTouchListener { _, event -> onPetTouch(event) }
        }
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
        lp.y = controller.y.toInt()

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

    /** 크기 설정이 바뀌었을 때 창 크기와 이동 범위를 다시 계산한다 */
    fun setSize(newSizeDp: Int) {
        sizeDp = newSizeDp
        val view = petView ?: return   // 아직 소환 전이면 값만 기억해 둔다
        val lp = params ?: return
        computeSize()
        lp.width = widthPx
        lp.height = heightPx
        updateBounds()
        lp.x = controller.x.toInt()
        lp.y = controller.y.toInt()
        windowManager.updateViewLayout(view, lp)
    }

    fun setActivityLevel(level: Int) {
        controller.activityLevel = level
    }

    /** 그림의 가로세로 비율을 유지해서 창 크기를 정한다 */
    private fun computeSize() {
        val d = drawableFor(definition.idleFrames.first()) ?: return
        heightPx = (sizeDp * density).toInt()
        widthPx = heightPx * d.intrinsicWidth / d.intrinsicHeight
    }

    private fun onPetTouch(event: MotionEvent): Boolean {
        val lp = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchOffsetX = event.rawX - lp.x
                touchOffsetY = event.rawY - lp.y
                controller.startDrag()
                applyFrame()
            }
            MotionEvent.ACTION_MOVE -> {
                controller.dragTo(event.rawX - touchOffsetX, event.rawY - touchOffsetY)
                applyPosition()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                controller.release()
            }
        }
        return true
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
        val newY = controller.y.toInt()
        // 위치가 그대로면 창을 다시 배치하지 않는다
        if (newX == lp.x && newY == lp.y) return
        lp.x = newX
        lp.y = newY
        windowManager.updateViewLayout(view, lp)
    }

    private fun updateBounds() {
        val area = usableArea()
        val bottomMargin = (BOTTOM_MARGIN_DP * density).toInt()
        controller.setBounds(
            minX = area.left.toFloat(),
            maxX = (area.right - widthPx).toFloat(),
            minY = area.top.toFloat(),
            groundY = (area.bottom - heightPx - bottomMargin).toFloat()
        )
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
