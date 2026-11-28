package com.pocketpet

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import com.pocketpet.billing.Billing
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
    private lateinit var sleepNowButton: Button
    private lateinit var dismissButton: Button
    private var showVipOnResume = false
    private var notificationAsked = false
    private var consentDone = false   // 광고 동의 확인이 끝났는지
    private var resumedNow = false    // 지금 화면이 보이는 중인지

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
        dismissButton = findViewById(R.id.dismissButton)
        dismissButton.setOnClickListener { dismissPet() }
        sleepNowButton = findViewById(R.id.sleepNowButton)
        sleepNowButton.setOnClickListener { sleepNow() }

        setupDrawer()
        setupPetSelector()

        banner = BannerSlot(this, findViewById(R.id.bannerContainer))

        // 앱을 새로 켰을 때(화면 회전 등이 아닐 때) VIP 소개 화면을 띄운다. VIP+는 띄우지 않는다
        showVipOnResume = savedInstanceState == null && settings.tier != Tier.VIP_PLUS

        // 광고 동의부터 확인한다 (필요한 지역이면 구글 동의 창). 끝나야 VIP 화면·배너를 띄운다
        Ads.gatherConsent(this) { onConsentDone() }
    }

    /** 광고 동의 확인이 끝났을 때 */
    private fun onConsentDone() {
        if (isDestroyed) return
        consentDone = true
        findViewById<View>(R.id.menuAdPrivacy).visibility =
            if (Ads.privacyOptionsRequired(this)) View.VISIBLE else View.GONE
        // 화면이 보이는 중이면 미뤄 둔 일(VIP 화면, 배너)을 지금 한다
        if (resumedNow) refreshScreen()
    }

    override fun onResume() {
        super.onResume()
        resumedNow = true
        // 구독이 끝났거나 새로 생겼을 수 있으니 돌아올 때마다 확인한다 (등급이 바뀌면 화면도 바뀐다)
        Billing.refresh()
        refreshScreen()
    }

    /** 화면이 보일 때마다(그리고 광고 동의가 끝났을 때) 화면 상태를 다시 맞춘다 */
    private fun refreshScreen() {
        // 광고 동의 확인이 끝난 뒤에만 VIP 화면·알림 권한을 띄운다 (동의 창이 뒤에 가려지지 않게)
        if (consentDone) {
            if (showVipOnResume) {
                showVipOnResume = false
                openVipPage()
            } else if (!notificationAsked) {
                // 알림 권한은 VIP 화면이 닫힌 뒤에 물어본다 (두 창이 겹치지 않게)
                notificationAsked = true
                requestNotificationPermissionIfNeeded()
            }
        }
        // 등급이 바뀌었을 수 있으니 돌아올 때마다 배너를 다시 정한다 (VIP+는 배너 없음)
        if (settings.tier.showBanner) banner.show() else banner.hide()
        banner.resume()
        // 시험용 ‘지금 잠들기’ 버튼: 테스트 빌드에서 설정의 스위치를 켰을 때만 보인다
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        sleepNowButton.visibility =
            if (debuggable && settings.testSleepButton) View.VISIBLE else View.GONE
        // 설정 화면에서 권한·바닥 높이를 바꾸고 돌아왔을 때 상태를 다시 표시한다
        updateStatus()
        // 펫 목록에서 다른 펫을 고르고 돌아왔으면 그 펫을 보여 준다
        petIndex = Pets.ALL.indexOfFirst { it.id == settings.selectedPetId }.coerceAtLeast(0)
        showSelectedPet()
    }

    override fun onPause() {
        resumedNow = false
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
        menuItem(R.id.menuAdPrivacy) { Ads.showPrivacyOptions(this) }
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
        if (::dismissButton.isInitialized) updateDismissLabel()
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
        // VIP+: 소환할 때마다 고른 펫을 한 마리씩 더한다
        val tier = settings.tier
        if (tier.maxPets > 1) {
            val pet = Pets.ALL[petIndex]
            val out = summonedNow()
            if (pet.id in out) {
                Toast.makeText(this, R.string.toast_already_out, Toast.LENGTH_SHORT).show()
                return
            }
            if (out.size >= tier.maxPets) {
                Toast.makeText(this, getString(R.string.toast_max_pets, tier.maxPets), Toast.LENGTH_SHORT).show()
                return
            }
            settings.summonedPets = out + pet.id
        }
        startForegroundService(Intent(this, PetService::class.java))
        petStatus.setText(R.string.pet_status_starting)
        refreshStatusSoon()
    }

    /** 시험용: 나와 있는 펫을 바로 잠들게 한다 */
    private fun sleepNow() {
        if (!PetService.isRunning) {
            Toast.makeText(this, R.string.test_sleep_need_pet, Toast.LENGTH_SHORT).show()
            return
        }
        startService(Intent(this, PetService::class.java).setAction(PetService.ACTION_SLEEP_NOW))
    }

    private fun openVipPage() {
        startActivity(Intent(this, VipActivity::class.java))
    }

    private fun dismissPet() {
        // 한 마리면 바로 돌려보낸다. 여러 마리면 누구를 돌려보낼지 고르는 팝업을 띄운다
        val out = summonedNow()
        if (out.size <= 1) {
            stopAllPets()
            return
        }
        DismissDialog(
            this,
            out,
            onDismissOne = { id ->
                val left = summonedNow() - id
                if (left.isEmpty()) stopAllPets()
                else {
                    settings.summonedPets = left
                    refreshStatusSoon()
                }
            },
            onDismissAll = { stopAllPets() }
        ).show()
    }

    private fun stopAllPets() {
        stopService(Intent(this, PetService::class.java))
        refreshStatusSoon()
    }

    /** VIP+ 여러 마리 모드에서 지금 화면에 나와 있는 펫 id들 (아니면 빈 목록) */
    private fun summonedNow(): List<String> =
        if (PetService.isRunning && settings.tier.maxPets > 1) settings.summonedPets else emptyList()

    /** 소환 해제 버튼 글자: 여러 마리가 나와 있으면 ‘소환 해제 (3)’처럼 마릿수를 붙인다 */
    private fun updateDismissLabel() {
        val count = summonedNow().size
        dismissButton.text = if (count > 1) getString(R.string.dismiss_count, count)
        else getString(R.string.dismiss)
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
        val count = summonedNow().size
        when {
            count > 1 -> petStatus.text = getString(R.string.pet_status_running_count, count)
            PetService.isRunning -> petStatus.setText(R.string.pet_status_running)
            else -> petStatus.setText(R.string.pet_status_idle)
        }
        updateDismissLabel()
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
        /** 문의 메일을 받을 주소 (개인정보처리방침의 문의 칸에도 쓰인다) */
        const val CONTACT_EMAIL = "qed3ideal@gmail.com"

        private const val STATUS_DELAY_MS = 700L
        private const val DRAWER_WIDTH_DP = 280
        private const val OPEN_MS = 220L
        private const val CLOSE_MS = 180L
        private const val LOCKED_ALPHA = 0.45f   // 잠긴 펫 미리보기의 투명도
    }
}
