package com.pocketpet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.pocketpet.service.PetService

class MainActivity : Activity() {

    private lateinit var permissionStatus: TextView
    private lateinit var petStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionStatus = findViewById(R.id.permissionStatus)
        petStatus = findViewById(R.id.petStatus)

        findViewById<Button>(R.id.summonButton).setOnClickListener { summonPet() }
        findViewById<Button>(R.id.dismissButton).setOnClickListener { dismissPet() }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면에서 권한을 바꾸고 돌아왔을 때 상태를 다시 표시한다
        updateStatus()
    }

    private fun summonPet() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        startForegroundService(Intent(this, PetService::class.java))
        petStatus.text = "펫 상태: 소환됨"
    }

    private fun dismissPet() {
        stopService(Intent(this, PetService::class.java))
        petStatus.text = "펫 상태: 쉬는 중"
    }

    private fun openOverlaySettings() {
        Toast.makeText(
            this,
            "목록에서 Pocket Pet을 찾아 '다른 앱 위에 표시'를 허용해 주세요",
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun updateStatus() {
        permissionStatus.text = if (Settings.canDrawOverlays(this)) {
            "다른 앱 위에 표시 권한: 허용됨 ✅"
        } else {
            "다른 앱 위에 표시 권한: 필요함 ⚠️\n소환하기를 누르면 설정 화면으로 이동해요"
        }
        petStatus.text = if (PetService.isRunning) "펫 상태: 소환됨" else "펫 상태: 쉬는 중"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }
}
