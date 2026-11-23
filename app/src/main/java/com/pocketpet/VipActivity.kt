package com.pocketpet

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import com.pocketpet.data.PetSettingsRepository
import com.pocketpet.data.Tier
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * VIP / VIP+ 소개 화면.
 * 앱을 새로 켤 때(VIP+가 아니면), 그리고 잠긴 펫을 소환하려 할 때 나온다.
 * 카드를 옆으로 밀어 넘기고(VIP+ → VIP), 나가기 버튼은 1초 뒤에 나타난다.
 * 구독 버튼은 구글 결제를 연결하면 실제로 동작한다.
 */
class VipActivity : Activity() {

    private lateinit var pager: HorizontalScrollView
    private lateinit var dots: LinearLayout
    private var pageWidth = 0
    private var currentPage = 0
    private var downScrollX = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vip)

        pager = findViewById(R.id.pager)
        dots = findViewById(R.id.dots)

        setupButtons()
        setupDots()
        // 화면 너비를 알아야 카드 크기를 정할 수 있어서, 화면이 그려진 뒤에 맞춘다
        pager.post { setupPages() }
        setupSwipe()
        showExitButtonLater()
    }

    private fun setupButtons() {
        val tier = PetSettingsRepository(this).tier
        val vipButton = findViewById<Button>(R.id.vipButton)
        val plusButton = findViewById<Button>(R.id.vipPlusButton)

        // 이미 쓰고 있는 등급(또는 그보다 낮은 등급) 버튼은 ‘이용 중’으로 막는다
        if (tier != Tier.FREE) {
            vipButton.isEnabled = false
            vipButton.setText(if (tier == Tier.VIP) R.string.vip_current else R.string.vip_included)
        }
        if (tier == Tier.VIP_PLUS) {
            plusButton.isEnabled = false
            plusButton.setText(R.string.vip_current)
        }

        vipButton.setOnClickListener { subscribe() }
        plusButton.setOnClickListener { subscribe() }
        findViewById<Button>(R.id.laterButton).setOnClickListener { finish() }
    }

    /** 나가기 버튼: 1초 뒤에 스르륵 나타난다 */
    private fun showExitButtonLater() {
        val exit = findViewById<Button>(R.id.laterButton)
        exit.postDelayed({
            exit.alpha = 0f
            exit.visibility = View.VISIBLE
            exit.animate().alpha(1f).setDuration(FADE_MS).start()
        }, EXIT_DELAY_MS)
    }

    // ---------- 옆으로 넘기는 카드 ----------

    /** 카드 하나가 화면의 대부분을 차지하고, 옆 카드가 살짝 보이게 크기를 맞춘다 */
    private fun setupPages() {
        val screenWidth = pager.width
        val gap = dp(8)
        pageWidth = (screenWidth * PAGE_RATIO).roundToInt()
        val side = (screenWidth - pageWidth) / 2

        val pages = findViewById<LinearLayout>(R.id.pages)
        pages.setPadding(side, dp(4), side, dp(4))
        for (i in 0 until pages.childCount) {
            val card = pages.getChildAt(i)
            val params = card.layoutParams as LinearLayout.LayoutParams
            params.width = pageWidth - gap * 2
            params.marginStart = gap
            params.marginEnd = gap
            card.layoutParams = params
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipe() {
        pager.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> downScrollX = pager.scrollX
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 손을 떼면 가까운 카드로 딱 맞춰 넘긴다. 조금만 밀어도 다음 카드로 간다
                    pager.post { snap() }
                }
            }
            false
        }
        pager.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            if (pageWidth > 0) {
                val page = (scrollX.toFloat() / pageWidth).roundToInt().coerceIn(0, PAGE_COUNT - 1)
                if (page != currentPage) {
                    currentPage = page
                    updateDots()
                }
            }
        }
    }

    private fun snap() {
        if (pageWidth == 0) return
        val moved = pager.scrollX - downScrollX
        val startPage = (downScrollX.toFloat() / pageWidth).roundToInt()
        val target = when {
            abs(moved) < pageWidth * SWIPE_THRESHOLD -> startPage
            moved > 0 -> startPage + 1
            else -> startPage - 1
        }.coerceIn(0, PAGE_COUNT - 1)
        pager.smoothScrollTo(target * pageWidth, 0)
    }

    // ---------- 아래 점 표시 ----------

    private fun setupDots() {
        repeat(PAGE_COUNT) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(dp(8), dp(8))
            params.marginStart = dp(5)
            params.marginEnd = dp(5)
            dots.addView(dot, params)
        }
        updateDots()
    }

    private fun updateDots() {
        for (i in 0 until dots.childCount) {
            dots.getChildAt(i).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (i == currentPage) 0xFFF28C38.toInt() else 0xFFE3D3C2.toInt())
            }
        }
    }

    /** 아직 구글 결제를 연결하기 전이라 안내만 한다 */
    private fun subscribe() {
        Toast.makeText(this, R.string.vip_not_ready, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PAGE_COUNT = 2            // VIP+, VIP
        private const val PAGE_RATIO = 0.84f         // 카드 한 장이 차지하는 화면 너비 비율
        private const val SWIPE_THRESHOLD = 0.15f    // 이만큼 밀면 다음 카드로 넘어간다
        private const val EXIT_DELAY_MS = 1000L      // 나가기 버튼이 나타나기까지 시간
        private const val FADE_MS = 250L
    }
}
