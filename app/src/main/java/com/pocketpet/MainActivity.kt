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
import android.widget.SeekBar
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

        setupPetSelector()
        setupSizeSeekBar()
        setupActivitySeekBar()
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
        petPreview.setImageResource(pet.idleFrames.first())
        petName.text = pet.name
    }

    // ---------- 설정 ----------

    private fun setupSizeSeekBar() {
        val label = findViewById<TextView>(R.id.sizeLabel)
        val bar = findViewById<SeekBar>(R.id.sizeSeekBar)

        // 10dp 단위로 조절한다 (80dp ~ 200dp)
        bar.min = PetSettingsRepository.MIN_SIZE_DP / SIZE_STEP_DP
        bar.max = PetSettingsRepository.MAX_SIZE_DP / SIZE_STEP_DP
        bar.progress = settings.sizeDp / SIZE_STEP_DP
        label.text = sizeText(settings.sizeDp)

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val dp = progress * SIZE_STEP_DP
                label.text = sizeText(dp)
                if (fromUser) settings.sizeDp = dp
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupActivitySeekBar() {
        val label = findViewById<TextView>(R.id.activityLabel)
        val bar = findViewById<SeekBar>(R.id.activitySeekBar)

        bar.min = PetSettingsRepository.MIN_ACTIVITY
        bar.max = PetSettingsRepository.MAX_ACTIVITY
        bar.progress = settings.activityLevel
        label.text = activityText(settings.activityLevel)

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                label.text = activityText(progress)
                if (fromUser) settings.activityLevel = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun sizeText(dp: Int): String {
        val percent = dp * 100 / PetSettingsRepository.DEFAULT_SIZE_DP
        return "크기: $percent%"
    }

    private fun activityText(level: Int): String {
        val names = arrayOf("아주 얌전함", "얌전함", "보통", "활발함", "아주 활발함")
        val index = (level - 1).coerceIn(0, names.size - 1)
        return "움직임 빈도: ${names[index]}"
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

    companion object {
        private const val SIZE_STEP_DP = 10
    }
}
