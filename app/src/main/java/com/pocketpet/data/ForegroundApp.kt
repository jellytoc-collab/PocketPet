package com.pocketpet.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

/**
 * ‘바탕화면에서만 보이기’를 위해 지금 화면에 떠 있는 앱이 무엇인지 알아낸다.
 * ‘사용 정보 접근’ 권한이 있어야 하고, 알아낸 정보는 저장하거나 밖으로 보내지 않는다.
 */
class ForegroundApp(private val context: Context) {

    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private val homePackages: Set<String> = findHomePackages()

    private var lastPackage: String? = null   // 마지막으로 화면에 나온 앱
    private var lastQueryTime = 0L

    /**
     * 펫을 보여 줘도 되면 true.
     * 바탕화면 앱이나 이 앱이 떠 있을 때, 또는 아직 알 수 없을 때는 보여 준다.
     */
    fun isPetAllowed(): Boolean {
        val pkg = currentPackage() ?: return true
        return pkg == context.packageName || pkg in homePackages
    }

    /** 지난번 확인 이후의 기록을 읽어서, 가장 최근에 화면에 나온 앱을 돌려준다 */
    private fun currentPackage(): String? {
        val now = System.currentTimeMillis()
        // 처음에는 1분 전부터, 그다음부터는 지난번 확인 직전부터 읽는다
        val from = if (lastQueryTime == 0L) now - FIRST_LOOKBACK_MS else lastQueryTime - OVERLAP_MS
        val events = try {
            usageStats.queryEvents(from, now)
        } catch (e: SecurityException) {
            return null
        } ?: return null

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == FOREGROUND_EVENT) lastPackage = event.packageName
        }
        lastQueryTime = now
        return lastPackage
    }

    /** 폰에 설치된 바탕화면 앱(런처)들. 설정 앱의 임시 홈 화면은 뺀다 */
    private fun findHomePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .filter { it != "com.android.settings" }
            .toSet()
    }

    companion object {
        private const val FIRST_LOOKBACK_MS = 60_000L
        private const val OVERLAP_MS = 1_000L

        // 앱 화면이 맨 앞으로 나왔다는 기록 (Android 10부터 ACTIVITY_RESUMED, 같은 값 1)
        @Suppress("DEPRECATION")
        private const val FOREGROUND_EVENT = UsageEvents.Event.MOVE_TO_FOREGROUND

        /** ‘사용 정보 접근’ 권한이 허용되어 있으면 true */
        fun hasPermission(context: Context): Boolean {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
