package com.pocketpet.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.pocketpet.R
import com.pocketpet.data.PetSettingsRepository
import com.pocketpet.data.Tier

/**
 * 구글 Play 구독 결제.
 *
 * Play Console에 만들어 둔 상품:
 *  - 구독 상품 "vip", "vip_plus"
 *  - 각 상품의 기본 요금제 "monthly"(1개월), "yearly"(1년)
 *  - 연간 요금제의 무료 체험 혜택 (VIP 3일, VIP+ 7일, 처음 가입하는 사람만)
 *
 * 구독 상태를 확인해서 이용 등급(Tier)을 정한다.
 * 테스트 빌드(debug)에서는 설정의 시험용 등급 스위치를 쓰기 때문에 등급을 덮어쓰지 않는다.
 */
object Billing {

    const val VIP = "vip"
    const val VIP_PLUS = "vip_plus"
    const val PLAN_MONTHLY = "monthly"
    const val PLAN_YEARLY = "yearly"

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var app: Application
    private var client: BillingClient? = null
    private var connecting = false

    /** 불러온 상품 정보 (가격 등) */
    private var products: Map<String, ProductDetails> = emptyMap()

    /** 지금 구독 중인 상품 (업그레이드할 때 옛 구독을 바꿔치기하려고 기억한다) */
    private var activePurchase: Purchase? = null

    /** 상품 정보나 구독 상태가 바뀌면 화면을 다시 그리라고 알려 줄 곳 */
    private val listeners = mutableSetOf<() -> Unit>()

    private val debuggable: Boolean
        get() = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** 앱이 켜질 때 한 번 부른다 */
    fun init(application: Application) {
        if (client != null) return
        app = application
        client = BillingClient.newBuilder(application)
            .setListener { result, purchases -> post { onPurchasesUpdated(result, purchases) } }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        connect()
    }

    /** 구독 상태를 다시 확인한다 (앱 화면이 다시 보일 때마다) */
    fun refresh() {
        if (client?.isReady == true) {
            queryPurchases()
            if (products.isEmpty()) queryProducts()
        } else {
            connect()
        }
    }

    fun addListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }

    // ---------- 화면에 보여 줄 정보 ----------

    /** 요금제의 정가 (예: "₩24,000"). 아직 못 불러왔으면 null */
    fun priceOf(productId: String, planId: String): String? =
        offersOf(productId, planId)
            .firstOrNull { it.offerId == null }
            ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    /** 이 사람이 받을 수 있는 무료 체험 일수. 체험이 없거나 이미 받았으면 null */
    fun trialDaysOf(productId: String, planId: String): Int? =
        offersOf(productId, planId)
            .flatMap { it.pricingPhases.pricingPhaseList }
            .firstOrNull { it.priceAmountMicros == 0L }
            ?.let { daysOf(it.billingPeriod) }

    private fun offersOf(productId: String, planId: String): List<ProductDetails.SubscriptionOfferDetails> =
        products[productId]?.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == planId }

    /** "P3D", "P1W" 같은 기간을 일수로 바꾼다 */
    private fun daysOf(period: String): Int? {
        val match = Regex("""P(\d+)([DW])""").matchEntire(period) ?: return null
        val n = match.groupValues[1].toInt()
        return if (match.groupValues[2] == "W") n * 7 else n
    }

    // ---------- 결제 ----------

    /** 구독 결제 창을 연다. 받을 수 있는 무료 체험이 있으면 체험으로 시작한다 */
    fun subscribe(activity: Activity, productId: String, planId: String) {
        val c = client
        val details = products[productId]
        val offers = offersOf(productId, planId)
        if (c == null || !c.isReady || details == null || offers.isEmpty()) {
            Toast.makeText(activity, R.string.billing_not_ready, Toast.LENGTH_SHORT).show()
            refresh()
            return
        }
        // 체험 같은 혜택(offerId가 있는 것)이 있으면 그것을, 없으면 기본 요금제를 고른다
        val offer = offers.firstOrNull { it.offerId != null } ?: offers.first()
        val builder = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offer.offerToken)
                    .build()
            )
        )
        // 이미 다른 구독(예: VIP)이 있으면 바꿔치기한다. 남은 기간만큼 구글이 계산해 준다
        activePurchase?.let { old ->
            builder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(old.purchaseToken)
                    .setSubscriptionReplacementMode(
                        BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
                    )
                    .build()
            )
        }
        val result = c.launchBillingFlow(activity, builder.build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            debugToast("🧪 결제 창 실패 (${result.responseCode}): ${result.debugMessage}")
            Toast.makeText(activity, R.string.billing_error, Toast.LENGTH_SHORT).show()
        }
    }

    /** Google Play의 구독 관리(해지·변경) 화면 주소 */
    fun manageUrl(context: Context): String {
        val sku = activePurchase?.products?.firstOrNull()
        val base = "https://play.google.com/store/account/subscriptions?package=${context.packageName}"
        return if (sku != null) "$base&sku=$sku" else base
    }

    // ---------- 내부 처리 ----------

    private fun connect() {
        val c = client ?: return
        if (c.isReady || connecting) return
        connecting = true
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                post {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryProducts()
                        queryPurchases()
                    } else {
                        debugToast("🧪 결제 연결 실패 (${result.responseCode}): ${result.debugMessage}")
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                post { connecting = false }
            }
        })
    }

    private fun queryProducts() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(VIP, VIP_PLUS).map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
        ).build()
        c.queryProductDetailsAsync(params) { result, details ->
            post {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    products = details.productDetailsList.associateBy { it.productId }
                    if (products.isEmpty()) debugToast("🧪 구독 상품을 찾지 못했어요 (Play Console 확인)")
                    notifyListeners()
                } else {
                    debugToast("🧪 상품 정보 실패 (${result.responseCode}): ${result.debugMessage}")
                }
            }
        }
    }

    private fun queryPurchases() {
        val c = client ?: return
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            post {
                // 확인에 실패하면(인터넷 없음 등) 등급을 그대로 둔다
                if (result.responseCode == BillingClient.BillingResponseCode.OK) applyPurchases(purchases)
            }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().forEach { purchase ->
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED ->
                            Toast.makeText(app, R.string.billing_thanks, Toast.LENGTH_SHORT).show()
                        Purchase.PurchaseState.PENDING ->
                            Toast.makeText(app, R.string.billing_pending, Toast.LENGTH_LONG).show()
                        else -> Unit
                    }
                }
                // 새 구독은 확인 처리하고, 전체 구독 목록으로 등급을 다시 정한다
                purchases.orEmpty().forEach { acknowledge(it) }
                queryPurchases()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> {
                debugToast("🧪 결제 실패 (${result.responseCode}): ${result.debugMessage}")
                Toast.makeText(app, R.string.billing_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 구독 목록을 보고 등급을 정한다 */
    private fun applyPurchases(purchases: List<Purchase>) {
        val active = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        active.forEach { acknowledge(it) }
        val owned = active.flatMap { it.products }
        val tier = when {
            VIP_PLUS in owned -> Tier.VIP_PLUS
            VIP in owned -> Tier.VIP
            else -> Tier.FREE
        }
        activePurchase = active.firstOrNull { VIP_PLUS in it.products } ?: active.firstOrNull()

        val settings = PetSettingsRepository(app)
        if (debuggable) {
            if (owned.isNotEmpty()) debugToast("🧪 구독 확인: $tier (테스트 빌드라 등급 스위치는 그대로)")
        } else if (settings.tier != tier) {
            settings.tier = tier
        }
        notifyListeners()
    }

    /** 결제 후 3일 안에 확인 처리를 하지 않으면 구글이 자동 환불한다 */
    private fun acknowledge(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client?.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                post { debugToast("🧪 구독 확인 처리 실패 (${result.responseCode})") }
            }
        }
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it() }
    }

    /** 구글 결제 쪽 응답은 다른 스레드에서 올 수 있어서 화면 스레드로 옮긴다 */
    private fun post(block: () -> Unit) {
        handler.post(block)
    }

    private fun debugToast(message: String) {
        if (!debuggable) return
        Toast.makeText(app, message, Toast.LENGTH_LONG).show()
    }
}
