package com.smyddevstudio.kanako.data.account

import android.os.SystemClock
import com.smyddevstudio.kanako.data.billing.PaidAccess
import com.smyddevstudio.kanako.data.billing.PurchasePlan
import com.smyddevstudio.kanako.data.billing.PurchaseEvidence
import com.smyddevstudio.kanako.data.billing.VerifiedPurchase
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AccountServerException(val status: Int) : IOException("Account request failed")

internal class AccountServer(private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    init {
        require(AccountConfiguration.isSecureUrl(baseUrl))
    }

    suspend fun session(userId: String, token: String): AccountGrant {
        val requestedAt = SystemClock.elapsedRealtime()
        val response = request("session/choice", token, JSONObject())
        return response.toGrant(userId, requestedAt)
    }

    suspend fun startTrial(userId: String, token: String): AccountGrant {
        val requestedAt = SystemClock.elapsedRealtime()
        return request("trial/start", token, JSONObject()).toGrant(userId, requestedAt)
    }

    suspend fun verify(
        userId: String,
        token: String,
        evidence: PurchaseEvidence
    ): Pair<AccountGrant, VerifiedPurchase?> {
        val requestedAt = SystemClock.elapsedRealtime()
        val response = request("purchases/verify", token, JSONObject()
            .put("purchaseToken", evidence.token)
            .put("productIds", org.json.JSONArray(evidence.productIds)))
        val grant = response.getJSONObject("session").toGrant(userId, requestedAt)
        val verified = response.optJSONObject("verifiedPurchase")?.let {
            VerifiedPurchase(it.getString("productId"), it.toPaidAccess())
        }
        return grant to verified
    }

    suspend fun deleteAccount(token: String) {
        val response = request("account/delete", token, JSONObject())
        if (!response.optBoolean("deleted")) throw IOException("Account deletion not confirmed")
    }

    private fun JSONObject.toPaidAccess(): PaidAccess = when (getString("kind")) {
        "none" -> PaidAccess.None
        "lifetime" -> PaidAccess.Lifetime
        "subscription" -> PaidAccess.Subscription(getLong("expiresAtMillis"))
        else -> throw IOException("Invalid access response")
    }

    private fun JSONObject.toGrant(userId: String, requestedAt: Long): AccountGrant {
        if (getString("userId") != userId) throw IOException("Account response mismatch")
        return AccountGrant(
            userId = userId,
            serverNowMillis = getLong("serverNowMillis"),
            trialStartedAtMillis = getLong("trialStartedAtMillis"),
            trialEndsAtMillis = getLong("trialEndsAtMillis"),
            paidAccess = getJSONObject("paidAccess").toPaidAccess(),
            leaseDurationMillis = getLong("leaseDurationMillis"),
            receivedAtElapsedMillis = requestedAt,
            plan = getJSONObject("paidAccess").optString("plan").takeIf { it.isNotBlank() }?.let {
                when (it) {
                    "monthly" -> PurchasePlan.MONTHLY
                    "yearly" -> PurchasePlan.YEARLY
                    "lifetime" -> PurchasePlan.LIFETIME
                    else -> throw IOException("Invalid account plan")
                }
            },
            testCheckoutAllowed = optBoolean("testCheckoutAllowed", false),
            reviewerAccess = optBoolean("reviewerAccess", false),
            checkoutAllowed = optBoolean("checkoutAllowed", false)
        )
    }

    private suspend fun request(path: String, token: String, body: JSONObject): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder()
                .url("$baseUrl/$path")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("X-Kanako-Trial-Choice", "explicit")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!continuation.isActive) return
                        try {
                            if (!it.isSuccessful) throw AccountServerException(it.code)
                            val content = it.body ?: throw IOException("Empty account response")
                            val limit = 64 * 1_024L
                            if (content.contentLength() > limit) throw IOException("Account response too large")
                            val source = content.source()
                            source.request(limit + 1)
                            if (source.buffer.size > limit) throw IOException("Account response too large")
                            val text = source.readUtf8()
                            continuation.resume(if (text.isBlank()) JSONObject() else JSONObject(text))
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
            })
        }
}
