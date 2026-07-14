package com.rnd.remoto.premium

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "BillingManager"

/**
 * One-time (non-consumable) "premium" unlock via Google Play Billing.
 *
 * IMPORTANT: PREMIUM_PRODUCT_ID must exactly match the in-app product ID you create in
 * Play Console (Monetize > Products > In-app products), set as a "Managed product", one-time
 * purchase. This can only be tested once that product exists and the app is uploaded to at
 * least an internal testing track with your account added as a license tester.
 */
const val PREMIUM_PRODUCT_ID = "premium_unlock"

class BillingManager(
    private val context: Context,
    private val premiumRepository: PremiumRepository,
    private val scope: CoroutineScope
) {
    private var billingClient: BillingClient? = null
    private var premiumProductDetails: ProductDetails? = null

    private val _priceText = MutableStateFlow<String?>(null)
    val priceTextFlow: StateFlow<String?> = _priceText.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { purchase -> scope.launch { handlePurchase(purchase) } }
        }
    }

    fun startConnection() {
        val client = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        billingClient = client
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryProductDetails()
                        restorePurchases()
                    }
                } else {
                    Log.w(TAG, "Billing setup falló: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service desconectado")
            }
        })
    }

    fun launchPurchase(activity: Activity) {
        val client = billingClient ?: return
        val details = premiumProductDetails ?: run {
            Log.w(TAG, "Todavía no se cargó el producto de Play Billing")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    private suspend fun queryProductDetails() {
        val client = billingClient ?: return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PREMIUM_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        val result = client.queryProductDetails(params)
        premiumProductDetails = result.productDetailsList?.firstOrNull()
        val details = premiumProductDetails
        if (details == null) {
            Log.w(TAG, "No se encontró el producto '$PREMIUM_PRODUCT_ID' en Play Console todavía")
        } else {
            _priceText.value = details.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    private suspend fun restorePurchases() {
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        val result = client.queryPurchasesAsync(params)
        result.purchasesList?.forEach { handlePurchase(it) }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PREMIUM_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        if (!purchase.isAcknowledged) {
            val client = billingClient ?: return
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(ackParams)
        }
        premiumRepository.setPremium(true)
    }

    fun endConnection() {
        billingClient?.endConnection()
        billingClient = null
    }
}
