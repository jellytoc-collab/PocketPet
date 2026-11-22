package com.pocketpet.ads

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

/**
 * 구글 광고(AdMob) 관련 코드를 모아 둔 곳.
 * 지금은 구글이 주는 테스트 광고 ID를 쓴다 — 출시 전에 내 AdMob 광고 ID로 바꾼다.
 * 🚨 실제 광고 ID로 바꾼 뒤에는 내 광고를 절대 누르지 않는다 (계정 정지 위험)
 */
object Ads {

    private var initStarted = false

    /** 광고 준비. 앱이 느려지지 않도록 다른 스레드에서 한 번만 한다 */
    fun init(context: Context) {
        if (initStarted) return
        initStarted = true
        val app = context.applicationContext
        Thread { MobileAds.initialize(app) {} }.start()
    }
}

/**
 * 화면 아래 배너 광고 하나를 관리한다.
 * show/hide는 등급이 바뀔 수 있으니 화면이 다시 보일 때마다 부른다.
 */
class BannerSlot(private val activity: Activity, private val container: FrameLayout) {

    private var adView: AdView? = null

    fun show() {
        container.visibility = View.VISIBLE
        if (adView != null) return
        val metrics = activity.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        val view = AdView(activity).apply {
            adUnitId = BANNER_UNIT_ID
            // 화면 너비에 맞춘 배너 크기
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        }
        container.addView(view)
        view.loadAd(AdRequest.Builder().build())
        adView = view
    }

    fun hide() {
        adView?.destroy()
        adView = null
        container.removeAllViews()
        container.visibility = View.GONE
    }

    fun pause() { adView?.pause() }
    fun resume() { adView?.resume() }

    companion object {
        /** 배너 광고 ID (지금은 테스트용) */
        private const val BANNER_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
    }
}
