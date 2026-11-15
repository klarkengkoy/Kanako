package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.data.billing.BillingIssue
import com.smyddevstudio.kanako.data.billing.BillingState
import com.smyddevstudio.kanako.data.billing.PaidAccess
import com.smyddevstudio.kanako.data.billing.PurchasePlan
import com.smyddevstudio.kanako.ui.components.KanakoButton
import com.smyddevstudio.kanako.ui.components.KanakoOutlinedButton
import com.smyddevstudio.kanako.ui.components.KanakoTextButton

@Composable
internal fun PurchaseScreen(
    billing: BillingState,
    accessGranted: Boolean,
    onPurchase: (PurchasePlan) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onManageSubscription: () -> Unit,
    onHelp: () -> Unit,
    onPrivacy: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onAccount: (() -> Unit)? = null,
    testCheckout: Boolean = false
) {
    Column(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                KanakoTextButton(onClick = onClose) { Text(stringResource(R.string.purchase_close)) }
            }
            Text(
                stringResource(when {
                    accessGranted -> R.string.purchase_access_ready
                    testCheckout -> R.string.purchase_test_title
                    else -> R.string.purchase_title
                }),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            if (accessGranted) {
                KanakoButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.purchase_continue))
                }
            } else {
                Text(stringResource(if (testCheckout) R.string.purchase_test_intro else R.string.purchase_intro),
                    style = MaterialTheme.typography.bodyLarge)
            }
            if (billing.refreshing || billing.purchasing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.purchase_loading),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (billing.pendingPurchase) {
                Text(stringResource(R.string.purchase_pending),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            billing.issue?.let { issue ->
                Text(
                    stringResource(when (issue) {
                        BillingIssue.UNAVAILABLE, BillingIssue.CONNECTION -> R.string.purchase_unavailable
                        BillingIssue.PRODUCTS -> R.string.purchase_products_unavailable
                        BillingIssue.PURCHASE -> R.string.purchase_failed
                        BillingIssue.VERIFICATION -> R.string.purchase_verification_failed
                        BillingIssue.ACKNOWLEDGEMENT -> R.string.purchase_acknowledgement_failed
                        BillingIssue.NO_PURCHASES -> R.string.purchase_none_found
                    }),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                KanakoTextButton(onClick = onRetry, enabled = !billing.refreshing && !billing.purchasing) {
                    Text(stringResource(R.string.purchase_retry))
                }
            }
            if (!accessGranted) {
                billing.offers.forEach { offer ->
                    KanakoOutlinedButton(
                        onClick = { onPurchase(offer.plan) },
                        enabled = !billing.refreshing && !billing.purchasing && !billing.pendingPurchase,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    ) {
                        Column(
                            Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(stringResource(when (offer.plan) {
                                PurchasePlan.MONTHLY -> R.string.purchase_monthly
                                PurchasePlan.YEARLY -> R.string.purchase_yearly
                                PurchasePlan.LIFETIME -> R.string.purchase_lifetime
                            }), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(when (offer.plan) {
                                PurchasePlan.MONTHLY -> R.string.purchase_monthly_price
                                PurchasePlan.YEARLY -> R.string.purchase_yearly_price
                                PurchasePlan.LIFETIME -> R.string.purchase_lifetime_price
                            }, offer.formattedPrice))
                        }
                    }
                }
                Text(stringResource(R.string.purchase_subscriptions_terms), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.purchase_lifetime_terms), style = MaterialTheme.typography.bodyMedium)
            }
            KanakoTextButton(onClick = onRestore, enabled = !billing.refreshing && !billing.purchasing) {
                Text(stringResource(R.string.purchase_restore))
            }
            if (billing.access is PaidAccess.Subscription) {
                KanakoTextButton(onClick = onManageSubscription) {
                    Text(stringResource(R.string.purchase_manage))
                }
            }
            onAccount?.let { openAccount ->
                KanakoTextButton(onClick = openAccount) { Text(stringResource(R.string.account_title)) }
            }
            KanakoTextButton(onClick = onHelp) { Text(stringResource(R.string.purchase_help)) }
            KanakoTextButton(onClick = onPrivacy) { Text(stringResource(R.string.purchase_privacy)) }
        }
    }
}
