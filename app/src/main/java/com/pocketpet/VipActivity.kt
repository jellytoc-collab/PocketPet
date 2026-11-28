package com.pocketpet

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.pocketpet.billing.Billing
import com.pocketpet.data.PetSettingsRepository
import com.pocketpet.data.Tier
import kotlin.math.roundToInt

/**
 * VIP / VIP+ 소개 화면.
 * 앱을 새로 켤 때(VIP+가 아니면), 그리고 잠긴 펫을 소환하려 할 때 나온다.
 * 카드를 옆으로 밀어 넘기고(VIP+ → VIP), 나가기 버튼은 1초 뒤에 나타난다.
 * 등급마다 연간(무료 체험이 있으면 체험으로 시작)·월간 구독 버튼이 있다.
 */
class VipActivity : Activity() {

    private lateinit var pager: SnapPager
    private lateinit var dots: LinearLayout
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vip)

        pager = findViewById(R.id.pager)
        dots = findViewById(R.id.dots)

        setupButtons()
        setupDots()
        // 화면 너비를 알아야 카드 크기를 정할 수 있어서, 화면이 그려진 뒤에 맞춘다
        pager.post { setupPages() }
        // 넘길 때마다 아래 점을 바꾼다
        pager.setOnScrollChangeListener { _, _, _, _, _ ->
            val page = pager.currentPage
            if (page != currentPage) {
                currentPage = page
                updateDots()
            }
        }
        showExitButtonLater()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.vipYearButton).setOnClickListener {
            Billing.subscribe(this, Billing.VIP, Billing.PLAN_YEARLY)
        }
        findViewById<Button>(R.id.vipButton).setOnClickListener {
            Billing.subscribe(this, Billing.VIP, Billing.PLAN_MONTHLY)
        }
        findViewById<Button>(R.id.vipPlusYearButton).setOnClickListener {
            Billing.subscribe(this, Billing.VIP_PLUS, Billing.PLAN_YEARLY)
        }
        findViewById<Button>(R.id.vipPlusButton).setOnClickListener {
            Billing.subscribe(this, Billing.VIP_PLUS, Billing.PLAN_MONTHLY)
        }
        findViewById<Button>(R.id.manageButton).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Billing.manageUrl(this))))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.laterButton).setOnClickListener { finish() }
        updateButtons()
    }

    override fun onStart() {
        super.onStart()
        Billing.addListener(billingListener)
        Billing.refresh()
        updateButtons()
    }

    override fun onStop() {
        Billing.removeListener(billingListener)
        super.onStop()
    }

    private val billingListener: () -> Unit = { updateButtons() }

    /** 가격·체험 여부·지금 등급에 맞게 버튼 글자를 다시 쓴다 */
    private fun updateButtons() {
        val tier = PetSettingsRepository(this).tier
        bindCard(
            Billing.VIP, R.id.vipPrice, R.id.vipYearButton, R.id.vipButton, R.string.vip_price,
            owned = tier != Tier.FREE,
            ownedText = if (tier == Tier.VIP) R.string.vip_current else R.string.vip_included
        )
        bindCard(
            Billing.VIP_PLUS, R.id.vipPlusPrice, R.id.vipPlusYearButton, R.id.vipPlusButton, R.string.vip_plus_price,
            owned = tier == Tier.VIP_PLUS,
            ownedText = R.string.vip_current
        )
        findViewById<View>(R.id.manageButton).visibility =
            if (tier != Tier.FREE) View.VISIBLE else View.GONE
    }

    private fun bindCard(
        productId: String, priceId: Int, yearId: Int, monthId: Int, fallbackPrice: Int,
        owned: Boolean, ownedText: Int
    ) {
        val yearButton = findViewById<Button>(yearId)
        val monthButton = findViewById<Button>(monthId)
        val yearPrice = Billing.priceOf(productId, Billing.PLAN_YEARLY)
        val monthPrice = Billing.priceOf(productId, Billing.PLAN_MONTHLY)

        // 가격 줄: 구글에서 불러온 가격(나라별 통화), 아직이면 기본 문구
        findViewById<TextView>(priceId).text =
            if (yearPrice != null && monthPrice != null) "$monthPrice · $yearPrice"
            else getString(fallbackPrice)

        if (owned) {
            // 이미 쓰고 있는 등급(또는 그보다 낮은 등급)은 ‘이용 중’으로 막는다
            yearButton.isEnabled = false
            yearButton.setText(ownedText)
            monthButton.visibility = View.GONE
            return
        }
        yearButton.isEnabled = true
        monthButton.visibility = View.VISIBLE
        val trial = Billing.trialDaysOf(productId, Billing.PLAN_YEARLY)
        val yearText = yearPrice ?: "…"
        yearButton.text = if (trial != null) getString(R.string.vip_year_trial_button, trial, yearText)
        else getString(R.string.vip_year_button, yearText)
        monthButton.text = getString(R.string.vip_month_button, monthPrice ?: "…")
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
        val pageWidth = (screenWidth * PAGE_RATIO).roundToInt()
        pager.pageWidth = pageWidth
        pager.pageCount = PAGE_COUNT
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PAGE_COUNT = 2            // VIP+, VIP
        private const val PAGE_RATIO = 0.84f         // 카드 한 장이 차지하는 화면 너비 비율
        private const val EXIT_DELAY_MS = 1000L      // 나가기 버튼이 나타나기까지 시간
        private const val FADE_MS = 250L
    }
}
