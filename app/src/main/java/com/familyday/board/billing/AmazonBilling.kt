package com.familyday.board.billing

import android.app.Activity
import com.amazon.device.iap.*
import com.amazon.device.iap.model.*
import com.familyday.board.security.ParentGate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

data class ProductOffer(val sku: String, val title: String, val marketplacePrice: String)
data class Entitlement(val premium: Boolean, val validUntil: Long, val verifiedAt: Long)
/** Only a trusted server may produce an entitlement from an Amazon receipt. */
fun interface ReceiptVerifier { suspend fun verify(userId: String, receiptId: String): Entitlement }

/** Compiled native Appstore SDK adapter. Not activated until marketplace SKUs, public key and RVS are configured. */
class AmazonBilling(private val gate: ParentGate, private val verifier: ReceiptVerifier, private val scope: CoroutineScope,
    private val skus: Set<String>) : PurchasingListener {
    val offers = MutableStateFlow<List<ProductOffer>>(emptyList())
    val entitlement = MutableStateFlow(Entitlement(false, 0, 0))
    val unavailable = MutableStateFlow(false)
    private var amazonUserId: String? = null
    fun start(activity: Activity) {
        gate.requireAdult()
        require(skus.isNotEmpty())
        PurchasingService.registerListener(activity.applicationContext, this)
        PurchasingService.getUserData()
        PurchasingService.getProductData(skus)
        PurchasingService.getPurchaseUpdates(true)
    }
    fun purchase(sku: String) {
        gate.requireAdult()
        require(offers.value.any { it.sku == sku })
        PurchasingService.purchase(sku)
    }
    fun restore() { gate.requireAdult(); PurchasingService.getPurchaseUpdates(true) }
    override fun onUserDataResponse(response: UserDataResponse) {
        if (response.requestStatus == UserDataResponse.RequestStatus.SUCCESSFUL) amazonUserId = response.userData.userId else unavailable.value = true
    }
    override fun onProductDataResponse(response: ProductDataResponse) {
        unavailable.value = response.requestStatus != ProductDataResponse.RequestStatus.SUCCESSFUL
        if (!unavailable.value) offers.value = response.productData.values.filter { it.productType == ProductType.SUBSCRIPTION }.map { ProductOffer(it.sku, it.title, it.price) }
    }
    override fun onPurchaseResponse(response: PurchaseResponse) {
        if (response.requestStatus == PurchaseResponse.RequestStatus.SUCCESSFUL) verify(response.userData.userId, response.receipt) else unavailable.value = true
    }
    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        if (response.requestStatus == PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) {
            response.receipts.forEach { verify(response.userData.userId, it) }
            if (response.hasMore()) PurchasingService.getPurchaseUpdates(false)
        } else unavailable.value = true
    }
    private fun verify(userId: String, receipt: Receipt) {
        if (receipt.productType != ProductType.SUBSCRIPTION) return
        scope.launch {
            try {
                val verified = verifier.verify(userId, receipt.receiptId)
                require(verified.verifiedAt <= System.currentTimeMillis() && verified.validUntil >= verified.verifiedAt)
                entitlement.value = verified
                PurchasingService.notifyFulfillment(receipt.receiptId, FulfillmentResult.FULFILLED)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { unavailable.value = true }
        }
    }
}
