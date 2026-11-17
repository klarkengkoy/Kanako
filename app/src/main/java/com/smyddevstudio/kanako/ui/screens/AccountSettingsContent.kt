package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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

internal fun formatAccessDeadline(epochMillis: Long): String {
    val local = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(local)
    return "$date (" + DateTimeFormatter.ofPattern("z").format(local) + ")"
}

@Composable
internal fun AccountSettingsContent(
    state: AccountState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onManageSubscription: () -> Unit,
    onPurchaseOptions: () -> Unit,
    modifier: Modifier = Modifier,
    billing: BillingState = BillingState(),
    showIntroduction: Boolean = true,
    signInOnly: Boolean = false
) {
    var confirmDelete by rememberSaveable(state.userId) { mutableStateOf(false) }
    val busy = state.busy || billing.purchasing || billing.showFeedback && (billing.refreshing || billing.verifying)
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.testSession && !signInOnly) {
            AccountStatusSummary(state)
            KanakoOutlinedButton(onClick = onSignOut, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.account_sign_out))
            }
        } else if (state.userId == null || signInOnly) {
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
            val accountName = state.displayName?.takeIf { it.isNotBlank() }
                ?: state.email?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.account_title)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        accountName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    state.profilePhotoUrl?.takeIf { it.startsWith("https://") }?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                Text(
                    accountName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            AccountStatusSummary(state)
            if (state.hasFreshGrant && !state.hasPaidAccess && !state.subscriptionNeedsRefresh) {
                KanakoButton(onClick = onPurchaseOptions, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.account_purchase_options))
                }
            }
            if (state.grant?.paidAccess is PaidAccess.Subscription) {
                KanakoOutlinedButton(onClick = onManageSubscription, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.account_manage_subscription))
                }
            }
            KanakoOutlinedButton(onClick = onSignOut, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.account_sign_out))
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            KanakoOutlinedButton(
                onClick = { confirmDelete = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (busy) 0.38f else 1f))
            ) {
                Text(stringResource(R.string.account_delete))
            }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (billing.verifying || state.userId == null || signInOnly) {
                Text(stringResource(if (billing.verifying) R.string.purchase_verifying else R.string.account_checking),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        if (billing.pendingPurchase) Text(stringResource(R.string.purchase_pending))
        billing.issue?.takeUnless { billing.verifying || !billing.showFeedback }?.let { issue ->
            Text(stringResource(when (issue) {
                BillingIssue.UNAVAILABLE, BillingIssue.CONNECTION -> R.string.purchase_unavailable
                BillingIssue.PRODUCTS -> R.string.purchase_products_unavailable
                BillingIssue.PURCHASE -> R.string.purchase_failed
                BillingIssue.VERIFICATION -> R.string.purchase_verification_failed
                BillingIssue.ACKNOWLEDGEMENT -> R.string.purchase_acknowledgement_failed
                BillingIssue.NO_PURCHASES -> R.string.purchase_none_found
            }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        state.issue?.takeUnless { billing.verifying && it == AccountIssue.CONNECTION }?.let { issue ->
            Text(stringResource(when (issue) {
                AccountIssue.SETUP -> R.string.account_setup_needed
                AccountIssue.SIGN_IN -> R.string.account_sign_in_failed
                AccountIssue.CONNECTION -> R.string.account_connection_failed
                AccountIssue.DELETION -> R.string.account_delete_failed
                AccountIssue.TRIAL -> R.string.purchase_trial_failed
            }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (!signInOnly && state.userId != null && state.accessServiceConfigured && state.issue != null) {
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

@Composable
private fun AccountStatusSummary(
    state: AccountState,
    modifier: Modifier = Modifier
) {
    val grant = state.grant?.takeIf { state.hasFreshGrant }
    val subscription = grant?.paidAccess as? PaidAccess.Subscription
    val trialActive = grant?.trialRemaining(state.elapsedMillis)?.let { it > 0 } == true
    val checking = grant == null || state.subscriptionNeedsRefresh
    val expired = !checking && !state.hasPaidAccess && !state.testSession &&
        grant.trialAvailable == false && !trialActive
    val status = when {
        checking -> R.string.account_status_checking
        state.testSession -> R.string.account_test_session
        state.hasPaidAccess -> when (grant?.plan) {
            PurchasePlan.MONTHLY -> R.string.account_monthly_active
            PurchasePlan.YEARLY -> R.string.account_yearly_active
            PurchasePlan.LIFETIME -> R.string.account_lifetime
            null -> if (grant?.paidAccess == PaidAccess.Lifetime) R.string.account_lifetime else R.string.account_paid
        }
        trialActive -> R.string.account_trial_active
        grant?.trialAvailable == true -> R.string.account_trial_not_started
        else -> R.string.account_status_expired
    }
    val deadline = when {
        checking || state.testSession || grant?.paidAccess == PaidAccess.Lifetime -> null
        state.hasPaidAccess -> subscription?.expiresAtMillis
        trialActive -> grant?.trialEndsAtMillis
        subscription != null -> subscription.expiresAtMillis
        else -> grant?.trialEndsAtMillis?.takeIf { it > 0 }
    }
    val deadlineLabel = when {
        state.hasPaidAccess || subscription != null -> R.string.account_valid_until
        trialActive -> R.string.account_trial_end_label
        else -> R.string.account_trial_ended_label
    }
    Column(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when {
                expired -> MaterialTheme.colorScheme.errorContainer
                checking || grant?.trialAvailable == true -> MaterialTheme.colorScheme.surfaceContainerHighest
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = when {
                expired -> MaterialTheme.colorScheme.onErrorContainer
                checking || grant?.trialAvailable == true -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            }
        ) {
            Text(stringResource(status), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge)
        }
        if (deadline != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(deadlineLabel), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatAccessDeadline(deadline), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "Japanese", locale = "ja")
@androidx.compose.ui.tooling.preview.Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Large text", widthDp = 320, fontScale = 2f)
@Composable
private fun SubscribedAccountPreview() {
    com.smyddevstudio.kanako.ui.theme.KanakoTheme {
        androidx.compose.material3.Surface {
            AccountSettingsContent(
                state = AccountState(
                    configured = true, userId = "preview", email = "reader@example.com",
                    displayName = "Kanako Reader",
                    accessServiceConfigured = true,
                    grant = com.smyddevstudio.kanako.data.account.AccountGrant(
                        "preview", 1_800_000_000_000L, 0, 0,
                        PaidAccess.Subscription(1_802_000_000_000L), 86_400_000, 0,
                        plan = PurchasePlan.MONTHLY
                    )
                ),
                onSignIn = {}, onSignOut = {}, onDelete = {}, onRetry = {},
                onManageSubscription = {}, onPurchaseOptions = {}
            )
        }
    }
}
