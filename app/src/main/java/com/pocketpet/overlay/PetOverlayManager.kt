package com.pocketpet.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import com.pocketpet.pet.Anim
import com.pocketpet.pet.Frame
import com.pocketpet.pet.PetController
import com.pocketpet.pet.PetDefinition
import com.pocketpet.pet.PetState

/**
 * 펫 오버레이 창을 만들고, 움직이고, 정리한다.
 * onLost: 권한이 꺼지는 등 펫을 더 이상 표시할 수 없게 됐을 때 한 번 호출된다.
 */
class PetOverlayManager(
    private val context: Context,
    private val definition: PetDefinition,
    private val onLost: () -> Unit
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val controller = PetController(definition, density)
    private val handler = Handler(Looper.getMainLooper())

    // 그림은 처음 소환할 때 한 번만 불러온다
    private var idleDrawable: Drawable? = null
    private var walkFrames: List<Drawable> = emptyList()
    private var flapFrames: List<Drawable> = emptyList()

    private var petView: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private var widthPx = 0
    private var heightPx = 0
    private var sizeDp = definition.heightDp
    private var floorDp = 0
    private var currentFrame: Frame? = null

    private var loopRunning = false      // 매 프레임 갱신 중인지
    private var paused = false           // 화면이 꺼져서 멈춘 상태인지
    private var sleepSec = 0f            // 잠든 시간 (초)
    private var lastFrameTimeNanos = 0L
    private var sinceCheckSec = 0f       // 마지막 권한 확인 후 지난 시간
    private var lostReported = false

    // 누른 지점과 펫 왼쪽 위 모서리 사이의 거리 (순간이동 방지)
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f

    // 손가락을 놓는 순간의 속도를 재서 던지기에 쓴다
    private var velocityTracker: VelocityTracker? = null

    // 던지기는 VIP부터. 일반 등급이면 놓았을 때 그냥 떨어진다
    private var canThrow = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!loopRunning) return
            onFrame(frameTimeNanos)
            if (loopRunning) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val wakeRunnable = Runnable { onWake() }

    /** 펫을 띄운다. 성공하면 true */
    fun show(): Boolean {
        // 이미 펫이 떠 있으면 새로 만들지 않는다 (중복 생성 방지)
        if (petView != null) return true
        if (!Settings.canDrawOverlays(context)) return false

        loadFrames()
        val idle = idleDrawable ?: return false
        computeSize()

        val view = ImageView(context).apply {
            setImageDrawable(idle)
            setOnTouchListener { _, event -> onPetTouch(event) }
        }
        // 시스템이 창을 강제로 없애면 알아챈다
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                if (petView === v) lost()
            }
        })
        currentFrame = Frame(Anim.IDLE, 0)

        val lp = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // 창이 화면 밖으로 나갈 수 있게 한다 (바닥 높이 제한 없음)
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        updateBounds()
        controller.placeInMiddle()
        lp.x = controller.x.toInt()
        lp.y = controller.y.toInt()

        try {
            windowManager.addView(view, lp)
        } catch (e: RuntimeException) {
            // 권한이 막 꺼졌거나 창을 만들 수 없는 경우
            return false
        }
        petView = view
        params = lp
        lostReported = false

        startLoop()
        return true
    }

    /** 펫을 정리한다. 여러 번 불러도 안전하다 */
    fun hide() {
        stopLoop()
        velocityTracker?.recycle()
        velocityTracker = null
        val view = petView
        petView = null          // 먼저 비워서 '강제로 없어짐'으로 오해하지 않게 한다
        params = null
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
                // 이미 제거된 경우는 무시한다
            }
        }
    }

    /** 화면이 꺼졌을 때: 계산을 멈춘다 */
    fun pause() {
        paused = true
        stopLoop()
    }

    /** 화면이 켜졌을 때: 권한을 확인하고 다시 움직인다 */
    fun resume() {
        if (!paused) return
        paused = false
        if (petView == null) return
        if (!checkPermission()) return
        startLoop()
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
        val lp = params ?: return     // 아직 소환 전이면 값만 기억해 둔다
        computeSize()
        lp.width = widthPx
        lp.height = heightPx
        updateBounds()
        lp.x = controller.x.toInt()
        lp.y = controller.y.toInt()
        relayout()
    }

    /** 바닥 높이 설정이 바뀌었을 때 바닥 위치를 다시 계산한다 */
    fun setFloor(newFloorDp: Int) {
        floorDp = newFloorDp
        if (petView == null) return   // 아직 소환 전이면 값만 기억해 둔다
        updateBounds()
        applyPosition()
    }

    /** 걷는 빈도가 바뀌었을 때: 잠들어 있었다면 깨워서 새 빈도로 다시 계산한다 */
    fun setWalkFrequency(frequency: Int) {
        controller.walkFrequency = frequency
        startLoop()
    }

    /** 던질 수 있는지 (등급에 따라 바뀐다) */
    fun setCanThrow(allowed: Boolean) {
        canThrow = allowed
    }

    // ---------- 움직임 반복 ----------

    private fun startLoop() {
        if (petView == null || paused) return
        handler.removeCallbacks(wakeRunnable)
        if (loopRunning) return
        loopRunning = true
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopLoop() {
        loopRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        handler.removeCallbacks(wakeRunnable)
    }

    /** 쉬는 동안은 매 프레임 계산하지 않고 잠든다 (배터리 절약) */
    private fun sleep(sec: Float) {
        loopRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        sleepSec = sec
        handler.postDelayed(wakeRunnable, (sec * 1000).toLong())
    }

    private fun onWake() {
        if (petView == null || paused) return
        controller.passTime(sleepSec)
        if (!checkPermission()) return
        startLoop()
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
        if (!applyPosition()) return

        // 2초마다 권한이 아직 있는지 확인한다
        sinceCheckSec += dt
        if (sinceCheckSec >= PERMISSION_CHECK_SEC) {
            sinceCheckSec = 0f
            if (!checkPermission()) return
        }

        // 쉬는 중이면 잠든다. 권한 확인을 위해 한 번에 최대 2초씩만 잔다
        val rest = controller.restTimeLeft
        if (rest > MIN_SLEEP_SEC) {
            sleep(minOf(rest, PERMISSION_CHECK_SEC))
        }
    }

    // ---------- 권한·오류 처리 ----------

    /** 권한이 있으면 true. 없으면 펫을 정리하고 알린다 */
    private fun checkPermission(): Boolean {
        if (Settings.canDrawOverlays(context)) return true
        lost()
        return false
    }

    private fun lost() {
        if (lostReported) return
        lostReported = true
        hide()
        onLost()
    }

    /** 창 위치·크기를 반영한다. 실패하면(창이 사라짐) 정리하고 false */
    private fun relayout(): Boolean {
        val view = petView ?: return false
        val lp = params ?: return false
        return try {
            windowManager.updateViewLayout(view, lp)
            true
        } catch (e: IllegalArgumentException) {
            lost()
            false
        }
    }

    // ---------- 그림 ----------

    private fun loadFrames() {
        if (idleDrawable != null) return
        idleDrawable = context.getDrawable(definition.idle)
        walkFrames = loadStrip(definition.walkStrip, definition.walkFrameCount)
        flapFrames = loadStrip(definition.flapStrip, definition.flapFrameCount)
    }

    /** 가로로 이어 붙인 그림을 count등분해서 컷 목록으로 만든다 */
    private fun loadStrip(res: Int, count: Int): List<Drawable> {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val sheet = BitmapFactory.decodeResource(context.resources, res, options)
            ?: return emptyList()
        val frameWidth = sheet.width / count
        val frames = (0 until count).map { i ->
            val bitmap = Bitmap.createBitmap(sheet, i * frameWidth, 0, frameWidth, sheet.height)
            BitmapDrawable(context.resources, bitmap)
        }
        sheet.recycle()
        return frames
    }

    /** 그림의 가로세로 비율을 유지해서 창 크기를 정한다 */
    private fun computeSize() {
        val d = idleDrawable ?: context.getDrawable(definition.idle) ?: return
        if (d.intrinsicHeight <= 0) return
        heightPx = (sizeDp * density).toInt()
        widthPx = heightPx * d.intrinsicWidth / d.intrinsicHeight
    }

    private fun applyFrame() {
        val view = petView ?: return
        val frame = controller.currentFrame()
        if (frame != currentFrame) {
            drawableFor(frame)?.let { view.setImageDrawable(it) }
            currentFrame = frame
        }
        // 걷기 그림은 오른쪽을 보고 있으므로, 왼쪽으로 걸을 때만 좌우를 뒤집는다
        view.scaleX = if (controller.state == PetState.WALK && !controller.facingRight) -1f else 1f
    }

    private fun drawableFor(frame: Frame): Drawable? = when (frame.anim) {
        Anim.IDLE -> idleDrawable
        Anim.WALK -> walkFrames.getOrNull(frame.index)
        Anim.FLAP -> flapFrames.getOrNull(frame.index)
    }

    // ---------- 터치 ----------

    private fun onPetTouch(event: MotionEvent): Boolean {
        val lp = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchOffsetX = event.rawX - lp.x
                touchOffsetY = event.rawY - lp.y
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                trackVelocity(event)
                controller.startDrag()
                applyFrame()
                startLoop()      // 잠들어 있었다면 깨운다
            }
            MotionEvent.ACTION_MOVE -> {
                trackVelocity(event)
                controller.dragTo(event.rawX - touchOffsetX, event.rawY - touchOffsetY)
                applyPosition()
            }
            MotionEvent.ACTION_UP -> {
                trackVelocity(event)
                // 놓는 순간 손가락 속도(초당 px)로 던진다. 천천히 놓으면 그냥 떨어진다
                val tracker = velocityTracker
                tracker?.computeCurrentVelocity(1000)
                if (canThrow) {
                    controller.release(tracker?.xVelocity ?: 0f, tracker?.yVelocity ?: 0f)
                } else {
                    controller.release()
                }
                tracker?.recycle()
                velocityTracker = null
                startLoop()
            }
            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                controller.release()
                startLoop()
            }
        }
        return true
    }

    /**
     * 펫 창이 손가락을 따라 움직이므로, 창 안 좌표 대신 화면 전체 좌표로 속도를 잰다.
     */
    private fun trackVelocity(event: MotionEvent) {
        val tracker = velocityTracker ?: return
        val copy = MotionEvent.obtain(event)
        copy.setLocation(event.rawX, event.rawY)
        tracker.addMovement(copy)
        copy.recycle()
    }

    // ---------- 위치 ----------

    /** 위치를 창에 반영한다. 창이 사라졌으면 false */
    private fun applyPosition(): Boolean {
        val lp = params ?: return false
        val newX = controller.x.toInt()
        val newY = controller.y.toInt()
        // 위치가 그대로면 창을 다시 배치하지 않는다
        if (newX == lp.x && newY == lp.y) return true
        lp.x = newX
        lp.y = newY
        return relayout()
    }

    private fun updateBounds() {
        val area = usableArea()
        val bottomMargin = ((BOTTOM_MARGIN_DP + floorDp) * density).toInt()
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

    companion object {
        private const val BOTTOM_MARGIN_DP = 8
        private const val PERMISSION_CHECK_SEC = 2f
        private const val MIN_SLEEP_SEC = 0.3f
    }
}
