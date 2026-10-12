package com.pocketpet

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply {
            text = "Pocket Pet\n빌드 환경 테스트 성공!"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        setContentView(label)
    }
}
