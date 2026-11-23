package com.pocketpet.ads

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.SystemClock
import com.google.android.gms.ads.AdActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.pocketpet.data.PetSettingsRepository

/**
 * 앱 복귀 광고: 다른 앱에 갔다가 Pocket Pet으로 돌아오면 전체 화면 광고를 보여 준다.
 *
 * 사용자가 싫어하지 않도록 이럴 때는 안 보여 준다:
 * - 일반 등급이 아닐 때 (VIP·VIP+는 복귀 광고 없음)
 * - 앱을 처음 켰을 때
 * - 권한 설정·언어 설정·메일·스토어처럼 앱이 직접 연 화면에서 돌아올 때
 * - 마지막 광고 후 [MIN_INTERVAL_MS]가 지나지 않았을 때
 */
class AppOpenAdManager(private val app: Application) : Application.ActivityLifecycleCallbacks {

    private val settings = PetSettingsRepository(app)

    // 테스트 빌드는 확인하기 쉽게 30초, 실제 앱은 2시간에 한 번까지만
    private val minIntervalMs =
        if ((app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) TEST_INTERVAL_MS
        else MIN_INTERVAL_MS

    private var ad: AppOpenAd? = null
    private var adLoadedAt = 0L
    private var loading = false
    private var showing = false
    private var lastShownAt = 0L

    private var startedCount = 0          // 지금 보이는 우리 화면 수
    private var wentToBackground = false  // 화면이 모두 사라졌다가(다른 앱으로 감) 돌아오는 중인지
    private var returnPending = false     // 돌아왔으니 광고를 보여 줄 차례인지

    fun start() {
        app.registerActivityLifecycleCallbacks(this)
        preload()
    }

    /** 앱이 직접 바깥 화면을 열 때 부른다 → 거기서 돌아올 때는 광고를 건너뛴다 */
    fun skipNext() {
        skipNextReturn = true
    }

    private var skipNextReturn = false

    // ---------- 광고 미리 받아 두기 ----------

    private fun preload() {
        if (loading || isAdFresh()) return
        if (!settings.tier.showAppOpenAd) return
        loading = true
        AppOpenAd.load(
            app,
            APP_OPEN_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    ad = loaded
                    adLoadedAt = SystemClock.elapsedRealtime()
                    Ads.debugToast(app, "🧪 복귀 광고 준비됨")
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false   // 다음에 다시 시도한다
                    Ads.debugToast(app, "🧪 복귀 광고 실패 (코드 ${error.code}): ${error.message}")
                }
            }
        )
    }

    /** 받아 둔 광고는 4시간이 지나면 못 쓴다 */
    private fun isAdFresh(): Boolean =
        ad != null && SystemClock.elapsedRealtime() - adLoadedAt < AD_EXPIRE_MS

    // ---------- 보여 주기 ----------

    private fun showIfAllowed(activity: Activity) {
        if (showing || activity is AdActivity) return
        if (!settings.tier.showAppOpenAd) return
        val now = SystemClock.elapsedRealtime()
        if (lastShownAt != 0L && now - lastShownAt < minIntervalMs) return
        if (!isAdFresh()) {
            preload()   // 이번엔 준비가 안 됐으니 다음 복귀 때 보여 준다
            return
        }
        val current = ad ?: return
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                showing = false
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                showing = false
                preload()
            }
        }
        showing = true
        lastShownAt = now
        current.show(activity)
    }

    // ---------- 화면이 켜지고 꺼지는 것 지켜보기 ----------

    override fun onActivityStarted(activity: Activity) {
        if (activity is AdActivity) return
        if (startedCount == 0 && wentToBackground) {
            // 다른 앱에 있다가 돌아왔다
            returnPending = !skipNextReturn
            skipNextReturn = false
            wentToBackground = false
        }
        startedCount++
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is AdActivity) return
        if (returnPending) {
            returnPending = false
            showIfAllowed(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is AdActivity) return
        startedCount = (startedCount - 1).coerceAtLeast(0)
        // 화면 회전처럼 잠깐 다시 만드는 경우는 다른 앱으로 간 게 아니다
        if (startedCount == 0 && !activity.isChangingConfigurations) {
            wentToBackground = true
            preload()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        /** 앱 복귀 광고 ID (지금은 테스트용) */
        private const val APP_OPEN_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"

        /** 복귀 광고 사이 최소 간격: 2시간 */
        private const val MIN_INTERVAL_MS = 2 * 60 * 60 * 1000L

        /** 테스트 빌드에서의 간격: 30초 */
        private const val TEST_INTERVAL_MS = 30 * 1000L

        /** 받아 둔 광고를 쓸 수 있는 시간: 4시간 (구글 규칙) */
        private const val AD_EXPIRE_MS = 4 * 60 * 60 * 1000L
    }
}
