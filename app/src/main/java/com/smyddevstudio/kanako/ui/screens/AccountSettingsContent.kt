package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.data.account.AccountIssue
import com.smyddevstudio.kanako.data.account.AccountState
import com.smyddevstudio.kanako.data.billing.PaidAccess
import com.smyddevstudio.kanako.data.billing.PurchasePlan
import com.smyddevstudio.kanako.data.billing.BillingState
import com.smyddevstudio.kanako.data.billing.BillingIssue
import com.smyddevstudio.kanako.ui.components.KanakoButton
import com.smyddevstudio.kanako.ui.components.KanakoOutlinedButton
import com.smyddevstudio.kanako.ui.components.KanakoTextButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun AccountSettingsContent(
    state: AccountState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
    onManageSubscription: () -> Unit,
    onPurchaseOptions: () -> Unit,
    modifier: Modifier = Modifier,
    billing: BillingState = BillingState(),
    showIntroduction: Boolean = true
) {
    var confirmDelete by rememberSaveable(state.userId) { mutableStateOf(false) }
    val busy = state.busy || billing.refreshing || billing.purchasing
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.testSession) {
            Text(stringResource(R.string.account_test_session))
            KanakoTextButton(onClick = onSignOut) {
                Text(stringResource(R.string.account_sign_out))
            }
        } else if (state.userId == null) {
            if (showIntroduction) {
                Text(stringResource(if (state.accessServiceConfigured)
                    R.string.account_intro else R.string.account_sign_in_intro),
                    style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(
                onClick = onSignIn,
                enabled = state.configured && !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F1F1F)
                )
            ) {
                Image(painterResource(R.drawable.ic_google_sign_in), contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.account_google),
                    fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            Text(stringResource(R.string.account_signed_in, state.email ?: state.userId))
            state.grant?.takeIf { it.isFresh(state.elapsedMillis) }?.let { grant ->
                if (grant.hasPaidAccess(state.elapsedMillis)) {
                    Text(stringResource(when (grant.plan) {
                        PurchasePlan.MONTHLY -> R.string.purchase_monthly
                        PurchasePlan.YEARLY -> R.string.purchase_yearly
                        PurchasePlan.LIFETIME -> R.string.purchase_lifetime
                        null -> R.string.account_paid
                    }))
                } else {
                    val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                        .format(Instant.ofEpochMilli(grant.trialEndsAtMillis).atZone(ZoneId.systemDefault()))
                    Text(stringResource(R.string.account_trial_ends, date))
                    Text(stringResource(R.string.account_no_auto_charge))
                }
            }
            if (state.needsPurchase) {
                KanakoButton(onClick = onPurchaseOptions, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.account_purchase_options))
                }
            }
            if (state.accessServiceConfigured) {
                KanakoOutlinedButton(onClick = onRestore, enabled = !busy) {
                    Text(stringResource(R.string.account_restore))
                }
            }
            if (state.grant?.paidAccess is PaidAccess.Subscription) {
                KanakoTextButton(onClick = onManageSubscription) {
                    Text(stringResource(R.string.account_manage_subscription))
                }
            }
            KanakoTextButton(onClick = onSignOut, enabled = !busy) {
                Text(stringResource(R.string.account_sign_out))
            }
            KanakoTextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                Text(stringResource(R.string.account_delete))
            }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.account_checking),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (billing.pendingPurchase) Text(stringResource(R.string.purchase_pending))
        billing.issue?.let { issue ->
            Text(stringResource(when (issue) {
                BillingIssue.UNAVAILABLE, BillingIssue.CONNECTION -> R.string.purchase_unavailable
                BillingIssue.PRODUCTS -> R.string.purchase_products_unavailable
                BillingIssue.PURCHASE -> R.string.purchase_failed
                BillingIssue.VERIFICATION -> R.string.purchase_verification_failed
                BillingIssue.ACKNOWLEDGEMENT -> R.string.purchase_acknowledgement_failed
                BillingIssue.NO_PURCHASES -> R.string.purchase_none_found
            }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        state.issue?.let { issue ->
            Text(stringResource(when (issue) {
                AccountIssue.SETUP -> R.string.account_setup_needed
                AccountIssue.SIGN_IN -> R.string.account_sign_in_failed
                AccountIssue.CONNECTION -> R.string.account_connection_failed
                AccountIssue.DELETION -> R.string.account_delete_failed
            }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (state.userId != null && state.accessServiceConfigured) {
            KanakoTextButton(onClick = onRetry, enabled = !busy) {
                Text(stringResource(R.string.account_retry))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = {
                Text(stringResource(if (state.accessServiceConfigured)
                    R.string.account_delete_body_with_trial_history else R.string.account_delete_body),
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = {
                KanakoTextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.account_delete_confirm))
                }
            },
            dismissButton = {
                KanakoTextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.account_cancel))
                }
            }
        )
    }
}
