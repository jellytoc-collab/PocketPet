package com.pocketpet.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds

/**
 * 구글 광고(AdMob) 관련 코드를 모아 둔 곳.
 * 지금은 구글이 주는 테스트 광고 ID를 쓴다 — 출시 전에 내 AdMob 광고 ID로 바꾼다.
 * 🚨 실제 광고 ID로 바꾼 뒤에는 내 광고를 절대 누르지 않는다 (계정 정지 위험)
 */
object Ads {

    private var initStarted = false
    private var appOpen: AppOpenAdManager? = null

    /** 광고 준비. 앱이 켜질 때 한 번만 한다 (무거운 준비는 다른 스레드에서) */
    fun init(app: Application) {
        if (initStarted) return
        initStarted = true
        Thread { MobileAds.initialize(app) {} }.start()
        appOpen = AppOpenAdManager(app).also { it.start() }
    }

    /** 앱이 직접 바깥 화면(권한 설정·메일·스토어 등)을 열기 전에 부른다 → 돌아올 때 복귀 광고를 건너뛴다 */
    fun skipNextAppOpenAd() {
        appOpen?.skipNext()
    }

    /**
     * 테스트 빌드에서만: 광고 상태를 화면에 짧게 알려 준다 (광고가 안 뜰 때 원인 찾기용).
     * 플레이스토어 버전에서는 아무것도 보이지 않는다.
     */
    fun debugToast(context: Context, message: String) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * 화면 아래 배너 광고 하나를 관리한다.
 * show/hide는 등급이 바뀔 수 있으니 화면이 다시 보일 때마다 부른다.
 */
class BannerSlot(private val activity: Activity, private val container: FrameLayout) {

    private var adView: AdView? = null
    private var retryCount = 0

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
        view.adListener = object : AdListener() {
            override fun onAdLoaded() {
                retryCount = 0
                Ads.debugToast(activity, "🧪 배너 광고 받음")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Ads.debugToast(activity, "🧪 배너 광고 실패 (코드 ${error.code}): ${error.message}")
                // 잠깐 뒤에 다시 시도한다 (30초 → 1분 → 2분, 최대 3번)
                if (retryCount < MAX_RETRY) {
                    val delay = RETRY_BASE_MS shl retryCount
                    retryCount++
                    container.postDelayed({
                        if (adView === view) view.loadAd(AdRequest.Builder().build())
                    }, delay)
                }
            }
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

        private const val RETRY_BASE_MS = 30 * 1000L
        private const val MAX_RETRY = 3
    }
}
