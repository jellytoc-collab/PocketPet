package com.pocketpet

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

/** 사용법, 앱 정보, 개인정보처리방침을 보여 주는 화면 */
class InfoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        val (titleRes, body) = when (intent.getStringExtra(EXTRA_TYPE)) {
            TYPE_ABOUT -> R.string.title_about to getString(R.string.about_body, versionName())
            TYPE_PRIVACY -> R.string.title_privacy to
                getString(R.string.privacy_body, MainActivity.CONTACT_EMAIL)
            else -> R.string.title_help to getString(R.string.help_body)
        }
        findViewById<TextView>(R.id.infoTitle).setText(titleRes)
        findViewById<TextView>(R.id.infoBody).text = body
    }

    /** 앱 버전 이름 (예: 0.1.85) */
    @Suppress("DEPRECATION")
    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        const val TYPE_HELP = "help"
        const val TYPE_ABOUT = "about"
        const val TYPE_PRIVACY = "privacy"

        fun intent(context: Context, type: String): Intent =
            Intent(context, InfoActivity::class.java).putExtra(EXTRA_TYPE, type)
    }
}
