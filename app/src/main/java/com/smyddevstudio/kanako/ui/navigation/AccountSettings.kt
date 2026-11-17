package com.smyddevstudio.kanako.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smyddevstudio.kanako.KanakoApplication
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.data.account.AccountConfiguration
import com.smyddevstudio.kanako.ui.components.KanakoTextButton
import com.smyddevstudio.kanako.ui.screens.AccountSettingsContent
import com.smyddevstudio.kanako.ui.screens.PurchaseScreen
import com.smyddevstudio.kanako.ui.screens.PrivacyPolicyDialog
import com.smyddevstudio.kanako.ui.screens.cameraActivity
import kotlinx.coroutines.launch

private fun manageSubscription(context: Context, onUnavailable: () -> Unit) {
    val uri = Uri.Builder().scheme("https").authority("play.google.com")
        .path("store/account/subscriptions")
        .appendQueryParameter("sku", AccountConfiguration.billingCatalog.subscriptionId)
        .appendQueryParameter("package", context.packageName).build()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        onUnavailable()
    } catch (_: SecurityException) {
        onUnavailable()
    }
}

@Composable
internal fun AccountSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context.cameraActivity()
    val accounts = (context.applicationContext as KanakoApplication).accounts
    val state by accounts.state.collectAsStateWithLifecycle()
    val billing by accounts.billingState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pageUnavailable by remember { mutableStateOf(false) }

    AccountSettingsContent(
        state = state,
        billing = billing,
        modifier = modifier,
        onSignIn = { activity?.let { scope.launch { accounts.signIn(it) } } },
        onSignOut = { scope.launch { accounts.signOut() } },
        onDelete = { activity?.let { scope.launch { accounts.deleteAccount(it) } } },
        onRetry = accounts::refresh,
        onManageSubscription = { manageSubscription(context) { pageUnavailable = true } },
        onPurchaseOptions = accounts::requestPaywall
    )
    if (pageUnavailable) {
        AlertDialog(
            onDismissRequest = { pageUnavailable = false },
            text = { Text(stringResource(R.string.account_open_failed)) },
            confirmButton = {
                KanakoTextButton(onClick = { pageUnavailable = false }) {
                    Text(stringResource(R.string.account_close))
                }
            }
        )
    }
}

@Composable
internal fun AccountAccessDialog(onHelp: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context.cameraActivity()
    val accounts = (context.applicationContext as KanakoApplication).accounts
    val state by accounts.state.collectAsStateWithLifecycle()
    val billing by accounts.billingState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showPrivacy by remember { mutableStateOf(false) }
    if (state.hasFreshGrant && state.accessResolved && billing.purchasesResolved &&
        !state.hasPaidAccess && !state.testSession && !state.subscriptionNeedsRefresh) {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                PurchaseScreen(
                    billing = billing,
                    account = state,
                    accessGranted = false,
                    onStartTrial = { scope.launch { accounts.startTrial() } },
                    onContinueTrial = onClose,
                    onPurchase = { plan -> activity?.let { accounts.purchase(it, plan) } },
                    onRestore = accounts::restorePurchases,
                    onRetry = accounts::retryPurchases,
                    onHelp = onHelp,
                    onPrivacy = { showPrivacy = true },
                    onClose = onClose
                )
            }
        }
    } else if (!state.hasPaidAccess && !state.testSession) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.account_title)) },
            text = {
                Text(stringResource(when {
                    billing.verifying -> R.string.purchase_verifying
                    state.issue != null -> R.string.account_connection_failed
                    billing.issue == com.smyddevstudio.kanako.data.billing.BillingIssue.VERIFICATION -> R.string.purchase_verification_failed
                    billing.issue != null -> R.string.purchase_unavailable
                    else -> R.string.account_checking
                }))
            },
            confirmButton = {
                if (state.issue != null || billing.issue != null) {
                    KanakoTextButton(
                        onClick = accounts::restorePurchases,
                        enabled = !state.busy && !billing.refreshing && !billing.verifying
                    ) { Text(stringResource(R.string.account_restore)) }
                }
            },
            dismissButton = {
                KanakoTextButton(onClick = onClose) { Text(stringResource(R.string.account_close)) }
            }
        )
    }
    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
}
