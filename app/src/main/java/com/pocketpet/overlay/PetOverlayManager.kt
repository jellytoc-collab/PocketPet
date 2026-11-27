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
    private var sleepFrames: List<Drawable> = emptyList()

    private var petView: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private var widthPx = 0              // 기본 창 너비 (가만히 있기 그림 기준)
    private var heightPx = 0
    // 잠잘 때는 옆으로 넓은 그림이라 창을 넓힌다. 넓어진 만큼의 절반을 왼쪽으로 옮겨 펫 위치는 그대로 둔다
    private var extraLeftPx = 0
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

    // 말풍선 대사 (VIP+)
    private val bubble = SpeechBubble(context, windowManager)
    private var canTalk = false
    private var lastState: PetState? = null    // 행동이 바뀌는 순간을 알아채려고 기억해 둔다
    private var screenLeft = 0
    private var screenRight = 0
    /** 상황별 대사 목록 (summon, idle, grab, fly, sleep, wake) */
    private val lines: Map<String, List<String>> by lazy { loadLines() }
    private val idleTalkRunnable = Runnable { onIdleTalk() }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!loopRunning) return
            onFrame(frameTimeNanos)
            if (loopRunning) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val wakeRunnable = Runnable { onWake() }

    /** 펫을 띄운다. 성공하면 true. spread: 다른 펫과 겹치지 않게 아무 곳에 둔다 (여러 마리 소환) */
    fun show(spread: Boolean = false): Boolean {
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
        if (spread) controller.placeRandomly() else controller.placeInMiddle()
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
        lastState = controller.state

        startLoop()
        say(TALK_SUMMON, force = true)
        scheduleIdleTalk()
        return true
    }

    /** 펫을 정리한다. 여러 번 불러도 안전하다 */
    fun hide() {
        stopLoop()
        handler.removeCallbacks(idleTalkRunnable)
        bubble.clear()
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
        handler.removeCallbacks(idleTalkRunnable)
        bubble.clear()
    }

    /** 화면이 켜졌을 때: 권한을 확인하고 다시 움직인다 */
    fun resume() {
        if (!paused) return
        paused = false
        if (petView == null) return
        if (!checkPermission()) return
        startLoop()
        scheduleIdleTalk()
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
        lp.height = heightPx
        updateBounds()
        fitWindowTo(petView?.drawable)
        lp.x = controller.x.toInt() - extraLeftPx
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

    /** 잠잘 수 있는지 (VIP+). 바뀌면 쉬는 시간을 다시 계산하도록 깨운다 */
    fun setCanSleep(allowed: Boolean) {
        controller.canSleep = allowed
        startLoop()
    }

    /** 말풍선 대사를 쓸 수 있는지 (VIP+) */
    fun setCanTalk(allowed: Boolean) {
        canTalk = allowed
        if (allowed) {
            scheduleIdleTalk()
        } else {
            handler.removeCallbacks(idleTalkRunnable)
            bubble.clear()
        }
    }

    // ---------- 말풍선 ----------

    /**
     * 상황에 맞는 대사를 하나 골라 말한다.
     * force가 아니면 이미 말하는 중일 때는 건너뛴다 (잡았다 놓았다 할 때 말이 쏟아지지 않게).
     */
    private fun say(situation: String, force: Boolean = false) {
        if (!canTalk || petView == null || paused) return
        if (!force && bubble.busy) return
        val line = lines[situation]?.randomOrNull() ?: return
        bubble.say(line)
        moveBubble()
    }

    private fun moveBubble() {
        if (!bubble.busy) return
        // 펫 그림의 가운데는 창이 넓어져도 controller.x + 기본 너비의 절반이다
        bubble.moveTo(controller.x + widthPx / 2f, controller.y, screenLeft, screenRight)
    }

    /** 행동이 바뀌는 순간에 맞는 대사를 한다 */
    private fun onStateMaybeChanged() {
        val state = controller.state
        if (state == lastState) return
        lastState = state
        when (state) {
            PetState.DRAGGED -> say(TALK_GRAB)
            PetState.THROWN -> say(TALK_FLY)
            PetState.SLEEP -> say(TALK_SLEEP, force = true)
            PetState.WAKE -> say(TALK_WAKE, force = true)
            else -> Unit
        }
    }

    /** 가만히 있거나 걷는 중이면 가끔 혼잣말을 한다 */
    private fun onIdleTalk() {
        val state = controller.state
        if (state == PetState.IDLE || state == PetState.WALK) say(TALK_IDLE)
        scheduleIdleTalk()
    }

    private fun scheduleIdleTalk() {
        handler.removeCallbacks(idleTalkRunnable)
        if (!canTalk || petView == null || paused) return
        val delayMs = IDLE_TALK_MIN_MS + (Math.random() * (IDLE_TALK_MAX_MS - IDLE_TALK_MIN_MS)).toLong()
        handler.postDelayed(idleTalkRunnable, delayMs)
    }

    /** talk.xml 의 "상황: 대사" 줄들을 상황별로 묶는다 */
    private fun loadLines(): Map<String, List<String>> {
        val res = definition.talk ?: return emptyMap()
        return context.resources.getStringArray(res)
            .mapNotNull { item ->
                val colon = item.indexOf(':')
                if (colon <= 0) null
                else item.substring(0, colon).trim() to item.substring(colon + 1).trim()
            }
            .filter { it.second.isNotEmpty() }
            .groupBy({ it.first }, { it.second })
    }

    /** 시험용 잠들기 버튼: 땅에 있으면 바로 잠들기 시작한다 */
    fun sleepNow() {
        if (petView == null) return
        if (controller.sleepNow()) startLoop()
    }

    /** 폰을 흔들었을 때: 펫이 휙 날아오른다 */
    fun fling() {
        if (petView == null || paused) return
        controller.fling()
        startLoop()
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
        onStateMaybeChanged()
        applyFrame()
        if (!applyPosition()) return

        // 2초마다 권한이 아직 있는지 확인한다
        sinceCheckSec += dt
        if (sinceCheckSec >= PERMISSION_CHECK_SEC) {
            sinceCheckSec = 0f
            if (!checkPermission()) return
        }

        // 쉬거나 자는 중이라 화면이 안 바뀌면 계산을 멈춘다. 권한 확인을 위해 한 번에 최대 2초씩만 멈춘다
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
        sleepFrames = definition.sleepStrip?.let { loadStrip(it, definition.sleepFrameCount) }
            ?: emptyList()
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
            drawableFor(frame)?.let {
                view.setImageDrawable(it)
                if (fitWindowTo(it)) {
                    params?.x = controller.x.toInt() - extraLeftPx
                    relayout()
                }
            }
            currentFrame = frame
        }
        // 걷기 그림은 오른쪽을 보고 있으므로, 왼쪽으로 걸을 때만 좌우를 뒤집는다
        view.scaleX = if (controller.state == PetState.WALK && !controller.facingRight) -1f else 1f
    }

    /**
     * 그림 비율에 맞게 창 너비를 정한다 (잠잘 때만 기본보다 넓다).
     * 너비가 바뀌었으면 true. 창 위치 반영은 부른 쪽에서 한다.
     */
    private fun fitWindowTo(d: Drawable?): Boolean {
        val lp = params ?: return false
        val w = if (d != null && d.intrinsicHeight > 0) {
            maxOf(widthPx, heightPx * d.intrinsicWidth / d.intrinsicHeight)
        } else {
            widthPx
        }
        extraLeftPx = (w - widthPx) / 2
        if (lp.width == w) return false
        lp.width = w
        return true
    }

    private fun drawableFor(frame: Frame): Drawable? = when (frame.anim) {
        Anim.IDLE -> idleDrawable
        Anim.WALK -> walkFrames.getOrNull(frame.index)
        Anim.FLAP -> flapFrames.getOrNull(frame.index)
        Anim.SLEEP -> sleepFrames.getOrNull(frame.index) ?: idleDrawable
    }

    // ---------- 터치 ----------

    private fun onPetTouch(event: MotionEvent): Boolean {
        val lp = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 창 대신 펫 위치 기준으로 잰다 (잠자다 깨며 창이 좁아져도 펫이 튀지 않게)
                touchOffsetX = event.rawX - controller.x
                touchOffsetY = event.rawY - controller.y
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                trackVelocity(event)
                controller.startDrag()
                onStateMaybeChanged()
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
        val newX = controller.x.toInt() - extraLeftPx
        val newY = controller.y.toInt()
        // 위치가 그대로면 창을 다시 배치하지 않는다
        if (newX == lp.x && newY == lp.y) return true
        lp.x = newX
        lp.y = newY
        moveBubble()
        return relayout()
    }

    private fun updateBounds() {
        val area = usableArea()
        screenLeft = area.left
        screenRight = area.right
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
        private const val IDLE_TALK_MIN_MS = 20_000L   // 혼잣말 간격 (20 ~ 45초 사이에서 무작위)
        private const val IDLE_TALK_MAX_MS = 45_000L
        private const val TALK_SUMMON = "summon"
        private const val TALK_IDLE = "idle"
        private const val TALK_GRAB = "grab"
        private const val TALK_FLY = "fly"
        private const val TALK_SLEEP = "sleep"
        private const val TALK_WAKE = "wake"
    }
}
