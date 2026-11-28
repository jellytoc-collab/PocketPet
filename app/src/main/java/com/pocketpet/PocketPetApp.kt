package com.pocketpet

import android.app.Application
import com.pocketpet.ads.Ads
import com.pocketpet.billing.Billing

/** 앱 전체가 켜질 때 한 번 실행된다: 광고·결제 준비 */
class PocketPetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Ads.init(this)
        Billing.init(this)
    }
}
