package com.pocketpet

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.pocketpet.data.PetSettingsRepository
import com.pocketpet.data.Tier

/**
 * VIP / VIP+ 소개 화면.
 * 앱을 새로 켤 때(VIP+가 아니면), 그리고 잠긴 펫을 소환하려 할 때 나온다.
 * 구독 버튼은 구글 결제를 연결하면 실제로 동작한다.
 */
class VipActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vip)

        findViewById<Button>(R.id.closeButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.laterButton).setOnClickListener { finish() }

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
    }

    /** 아직 구글 결제를 연결하기 전이라 안내만 한다 */
    private fun subscribe() {
        Toast.makeText(this, R.string.vip_not_ready, Toast.LENGTH_SHORT).show()
    }
}
