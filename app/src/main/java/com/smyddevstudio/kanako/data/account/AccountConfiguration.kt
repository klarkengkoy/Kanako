package com.smyddevstudio.kanako.data.account

import com.smyddevstudio.kanako.BuildConfig
import com.smyddevstudio.kanako.data.billing.BillingCatalog
import java.net.URI

internal object AccountConfiguration {
    val serverUrl: String = BuildConfig.ACCOUNT_SERVER_URL.trimEnd('/')
    val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID
    val signInConfigured: Boolean = BuildConfig.ACCOUNT_CONFIG_PRESENT && webClientId.isNotBlank()
    val accessServiceConfigured: Boolean = signInConfigured && isSecureUrl(serverUrl)
    val enforceReadingAccess: Boolean = BuildConfig.ACCOUNT_ACCESS_ENFORCED

    val billingCatalog = BillingCatalog(
        BuildConfig.SUBSCRIPTION_PRODUCT_ID,
        BuildConfig.MONTHLY_BASE_PLAN_ID,
        BuildConfig.YEARLY_BASE_PLAN_ID,
        BuildConfig.LIFETIME_PRODUCT_ID,
        BuildConfig.LIFETIME_PURCHASE_OPTION_ID
    )

    fun isSecureUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null && uri.query == null
    }.getOrDefault(false)
}
