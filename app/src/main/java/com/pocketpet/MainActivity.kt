package com.pocketpet

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply {
            text = "펫 선택 + 설정\n(준비 중)"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        setContentView(label)
    }
}
