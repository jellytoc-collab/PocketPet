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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.pocketpet.data.PetSettingsRepository
import com.pocketpet.pet.Pets
import com.pocketpet.service.PetService

class MainActivity : Activity() {

    private lateinit var permissionStatus: TextView
    private lateinit var petStatus: TextView
    private lateinit var petPreview: ImageView
    private lateinit var petName: TextView
    private lateinit var settings: PetSettingsRepository

    private var petIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = PetSettingsRepository(this)
        permissionStatus = findViewById(R.id.permissionStatus)
        petStatus = findViewById(R.id.petStatus)
        petPreview = findViewById(R.id.petPreview)
        petName = findViewById(R.id.petName)

        findViewById<Button>(R.id.summonButton).setOnClickListener { summonPet() }
        findViewById<Button>(R.id.dismissButton).setOnClickListener { dismissPet() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupPetSelector()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면에서 권한을 바꾸고 돌아왔을 때 상태를 다시 표시한다
        updateStatus()
    }

    // ---------- 펫 선택 ----------

    private fun setupPetSelector() {
        petIndex = Pets.ALL.indexOfFirst { it.id == settings.selectedPetId }.coerceAtLeast(0)
        findViewById<Button>(R.id.prevPetButton).setOnClickListener { selectPet(petIndex - 1) }
        findViewById<Button>(R.id.nextPetButton).setOnClickListener { selectPet(petIndex + 1) }
        showSelectedPet()
    }

    private fun selectPet(index: Int) {
        // 양 끝에서 넘기면 반대쪽 끝으로 돌아간다
        petIndex = (index + Pets.ALL.size) % Pets.ALL.size
        settings.selectedPetId = Pets.ALL[petIndex].id
        showSelectedPet()
    }

    private fun showSelectedPet() {
        val pet = Pets.ALL[petIndex]
        petPreview.setImageResource(pet.idle)
        petName.text = pet.name
    }

    // ---------- 소환 ----------

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
