package com.pocketpet

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.pocketpet.ads.Ads
import com.pocketpet.ads.BannerSlot
import com.pocketpet.data.PetSettingsRepository
import com.pocketpet.data.ScreenInfo
import com.pocketpet.data.Tier
import com.pocketpet.pet.Pets
import com.pocketpet.service.PetService

class MainActivity : Activity() {

    private lateinit var permissionStatus: TextView
    private lateinit var petStatus: TextView
    private lateinit var hiddenWarning: TextView
    private lateinit var petPreview: ImageView
    private lateinit var petName: TextView
    private lateinit var drawer: View
    private lateinit var scrim: View
    private lateinit var settings: PetSettingsRepository
    private lateinit var banner: BannerSlot
    private var showVipOnResume = false
    private var notificationAsked = false

    private var petIndex = 0
    private var drawerOpen = false
    private var backCallback: Any? = null   // Android 13 이상에서 쓰는 뒤로가기 처리
    private val refreshStatus = Runnable { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = PetSettingsRepository(this)
        permissionStatus = findViewById(R.id.permissionStatus)
        petStatus = findViewById(R.id.petStatus)
        hiddenWarning = findViewById(R.id.hiddenWarning)
        petPreview = findViewById(R.id.petPreview)
        petName = findViewById(R.id.petName)
        drawer = findViewById(R.id.drawer)
        scrim = findViewById(R.id.drawerScrim)

        findViewById<Button>(R.id.summonButton).setOnClickListener { summonPet() }
        findViewById<Button>(R.id.dismissButton).setOnClickListener { dismissPet() }

        setupDrawer()
        setupPetSelector()

        banner = BannerSlot(this, findViewById(R.id.bannerContainer))

        // 앱을 새로 켰을 때(화면 회전 등이 아닐 때) VIP 소개 화면을 띄운다. VIP+는 띄우지 않는다
        showVipOnResume = savedInstanceState == null && settings.tier != Tier.VIP_PLUS
    }

    override fun onResume() {
        super.onResume()
        if (showVipOnResume) {
            showVipOnResume = false
            openVipPage()
        } else if (!notificationAsked) {
            // 알림 권한은 VIP 화면이 닫힌 뒤에 물어본다 (두 창이 겹치지 않게)
            notificationAsked = true
            requestNotificationPermissionIfNeeded()
        }
        // 등급이 바뀌었을 수 있으니 돌아올 때마다 배너를 다시 정한다 (VIP+는 배너 없음)
        if (settings.tier.showBanner) banner.show() else banner.hide()
        banner.resume()
        // 설정 화면에서 권한·바닥 높이를 바꾸고 돌아왔을 때 상태를 다시 표시한다
        updateStatus()
        // 펫 목록에서 다른 펫을 고르고 돌아왔으면 그 펫을 보여 준다
        petIndex = Pets.ALL.indexOfFirst { it.id == settings.selectedPetId }.coerceAtLeast(0)
        showSelectedPet()
    }

    override fun onPause() {
        banner.pause()
        super.onPause()
    }

    override fun onDestroy() {
        banner.hide()
        petStatus.removeCallbacks(refreshStatus)
        unregisterBackCallback()
        super.onDestroy()
    }

    // Android 12 이하에서의 뒤로가기: 메뉴가 열려 있으면 메뉴만 닫는다
    @Deprecated("Android 13 이상은 OnBackInvokedCallback을 쓴다")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer() else super.onBackPressed()
    }

    // ---------- 메뉴 ----------

    private fun setupDrawer() {
        findViewById<Button>(R.id.menuButton).setOnClickListener { openDrawer() }
        scrim.setOnClickListener { closeDrawer() }

        menuItem(R.id.menuPetList) {
            startActivity(Intent(this, PetListActivity::class.java))
        }
        menuItem(R.id.menuSettings) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        menuItem(R.id.menuLanguage) { openLanguageSettings() }
        menuItem(R.id.menuHelp) {
            startActivity(InfoActivity.intent(this, InfoActivity.TYPE_HELP))
        }
        menuItem(R.id.menuAbout) {
            startActivity(InfoActivity.intent(this, InfoActivity.TYPE_ABOUT))
        }
        menuItem(R.id.menuContact) { openContactMail() }
        menuItem(R.id.menuReview) { openStorePage() }
        menuItem(R.id.menuPrivacy) {
            startActivity(InfoActivity.intent(this, InfoActivity.TYPE_PRIVACY))
        }
    }

    /** 메뉴 항목을 누르면 메뉴를 닫은 뒤 action을 실행한다 */
    private fun menuItem(id: Int, action: () -> Unit) {
        findViewById<View>(id).setOnClickListener { closeDrawer(action) }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        val width = DRAWER_WIDTH_DP * resources.displayMetrics.density

        drawer.visibility = View.VISIBLE
        drawer.translationX = -width
        drawer.animate().translationX(0f).setDuration(OPEN_MS).start()

        scrim.visibility = View.VISIBLE
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(OPEN_MS).start()

        registerBackCallback()
    }

    private fun closeDrawer(after: (() -> Unit)? = null) {
        if (!drawerOpen) {
            after?.invoke()
            return
        }
        drawerOpen = false
        val width = DRAWER_WIDTH_DP * resources.displayMetrics.density

        drawer.animate().translationX(-width).setDuration(CLOSE_MS).withEndAction {
            drawer.visibility = View.GONE
            after?.invoke()
        }.start()
        scrim.animate().alpha(0f).setDuration(CLOSE_MS).withEndAction {
            scrim.visibility = View.GONE
        }.start()

        unregisterBackCallback()
    }

    private fun registerBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { closeDrawer() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback
            )
            backCallback = callback
        }
    }

    private fun unregisterBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backCallback as? OnBackInvokedCallback)?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
        }
        backCallback = null
    }

    /** Android 13 이상은 이 앱만의 언어 설정, 그 아래는 폰 전체 언어 설정을 연다 */
    private fun openLanguageSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(
                Settings.ACTION_APP_LOCALE_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        } else {
            Intent(Settings.ACTION_LOCALE_SETTINGS)
        }
        safeStart(intent)
    }

    private fun openContactMail() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.contact_subject))
        }
        safeStart(intent)
    }

    /** 플레이스토어 앱 페이지. 스토어 앱이 없으면 웹으로 연다 */
    private fun openStorePage() {
        try {
            Ads.skipNextAppOpenAd()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: ActivityNotFoundException) {
            safeStart(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }

    private fun safeStart(intent: Intent) {
        Ads.skipNextAppOpenAd()   // 바깥 화면에서 돌아올 때는 복귀 광고를 띄우지 않는다
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_SHORT).show()
        }
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
        // 잠긴 펫은 흐리게 보여 주고 이름 앞에 자물쇠를 붙인다 (구경은 할 수 있다)
        val unlocked = Pets.isUnlocked(pet, settings.tier)
        petPreview.alpha = if (unlocked) 1f else LOCKED_ALPHA
        petName.text = if (unlocked) getString(pet.nameRes)
        else getString(R.string.locked_pet_name, getString(pet.nameRes))
    }

    // ---------- 소환 ----------

    private fun summonPet() {
        // 잠긴 펫을 누르면 VIP 소개 화면을 보여 준다
        if (!Pets.isUnlocked(Pets.ALL[petIndex], settings.tier)) {
            openVipPage()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        startForegroundService(Intent(this, PetService::class.java))
        petStatus.setText(R.string.pet_status_starting)
        refreshStatusSoon()
    }

    private fun openVipPage() {
        startActivity(Intent(this, VipActivity::class.java))
    }

    private fun dismissPet() {
        stopService(Intent(this, PetService::class.java))
        refreshStatusSoon()
    }

    /** 서비스가 실제로 시작·종료될 시간을 조금 기다린 뒤 상태를 다시 표시한다 */
    private fun refreshStatusSoon() {
        petStatus.removeCallbacks(refreshStatus)
        petStatus.postDelayed(refreshStatus, STATUS_DELAY_MS)
    }

    private fun openOverlaySettings() {
        Toast.makeText(this, R.string.permission_guide, Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        safeStart(intent)
    }

    private fun updateStatus() {
        permissionStatus.setText(
            if (Settings.canDrawOverlays(this)) R.string.permission_granted
            else R.string.permission_needed
        )
        petStatus.setText(
            if (PetService.isRunning) R.string.pet_status_running
            else R.string.pet_status_idle
        )
        // 바닥이 너무 높아서 펫이 화면 밖에 있으면 빨간 경고를 보여 준다
        val hidden = ScreenInfo.isPetHidden(this, settings.floorDp)
        hiddenWarning.visibility = if (hidden) View.VISIBLE else View.GONE
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
        /** 문의 메일을 받을 주소 — 본인 이메일로 바꿔 주세요 */
        const val CONTACT_EMAIL = "your-email@example.com"

        private const val STATUS_DELAY_MS = 700L
        private const val DRAWER_WIDTH_DP = 280
        private const val OPEN_MS = 220L
        private const val CLOSE_MS = 180L
        private const val LOCKED_ALPHA = 0.45f   // 잠긴 펫 미리보기의 투명도
    }
}
