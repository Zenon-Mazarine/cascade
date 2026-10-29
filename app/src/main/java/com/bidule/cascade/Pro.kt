package com.bidule.cascade

import android.app.Activity
import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cascade Pro: a one-time purchase ("cascade_pro" in Play Console) that unlocks the premium
 * styles. Ownership is cached so Pro styles keep working offline, and re-checked with Play
 * every time the app starts.
 */
object Pro {
    const val PRODUCT_ID = "cascade_pro"

    enum class Status { Loading, Unavailable, Ready, Pending, Owned }

    private val _owned = MutableStateFlow(false)
    val owned: StateFlow<Boolean> = _owned.asStateFlow()

    private val _status = MutableStateFlow(Status.Loading)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Localized price from Play, e.g. "$3.99" or "79,00 MXN". */
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private var client: BillingClient? = null
    private var details: ProductDetails? = null
    private var prefs: Prefs? = null

    fun init(context: Context) {
        if (client != null) return
        val p = Prefs(context)
        prefs = p
        if (BuildConfig.ALL_UNLOCKED) {
            _owned.value = true
            _status.value = Status.Owned
            return
        }
        _owned.value = p.proOwned

        client = BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> onPurchases(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        connect()
    }

    private fun connect() {
        val c = client ?: return
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProduct()
                    refreshPurchases()
                } else {
                    _status.value = if (_owned.value) Status.Owned else Status.Unavailable
                }
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() reconnects on the next call.
            }
        })
    }

    private fun loadProduct() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        c.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = queryResult.productDetailsList.firstOrNull()
                _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
                if (!_owned.value) _status.value = if (details != null) Status.Ready else Status.Unavailable
            }
        }
    }

    /** Re-checks what the account owns: also how "Restore purchase" works. */
    fun refreshPurchases() {
        val c = client ?: return
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val mine = purchases.filter { PRODUCT_ID in it.products }
                if (mine.isEmpty()) {
                    // Only revoke if Play positively says nothing is owned (e.g. a refund).
                    setOwned(false)
                    if (details != null) _status.value = Status.Ready
                } else {
                    handle(mine)
                }
            }
        }
    }

    /** Opens Google Play's purchase sheet. Returns false if the store isn't reachable. */
    fun buy(activity: Activity): Boolean {
        val c = client ?: return false
        val d = details ?: run {
            loadProduct()
            return false
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())
            )
            .build()
        return c.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun onPurchases(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handle(purchases.filter { PRODUCT_ID in it.products })
        } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            refreshPurchases()
        }
    }

    private fun handle(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    setOwned(true)
                    if (!purchase.isAcknowledged) {
                        // Unacknowledged purchases are refunded by Play after 3 days.
                        client?.acknowledgePurchase(
                            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                        ) { }
                    }
                }
                Purchase.PurchaseState.PENDING -> if (!_owned.value) _status.value = Status.Pending
                else -> Unit
            }
        }
    }

    private fun setOwned(value: Boolean) {
        _owned.value = value
        prefs?.proOwned = value
        if (value) _status.value = Status.Owned
    }
}
