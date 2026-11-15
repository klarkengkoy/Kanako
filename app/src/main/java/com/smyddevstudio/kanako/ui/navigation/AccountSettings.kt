package com.smyddevstudio.kanako.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smyddevstudio.kanako.KanakoApplication
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.data.account.AccountConfiguration
import com.smyddevstudio.kanako.data.billing.BillingState
import com.smyddevstudio.kanako.ui.components.KanakoTextButton
import com.smyddevstudio.kanako.ui.screens.AccountSettingsContent
import com.smyddevstudio.kanako.ui.screens.PurchaseScreen
import com.smyddevstudio.kanako.ui.screens.PrivacyPolicyDialog
import com.smyddevstudio.kanako.ui.screens.cameraActivity
import kotlinx.coroutines.launch

@Composable
internal fun AccountSettings(
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    showPurchaseInitially: Boolean = false
) {
    val context = LocalContext.current
    val activity = context.cameraActivity()
    val accounts = (context.applicationContext as KanakoApplication).accounts
    val state by accounts.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showPurchase by rememberSaveable(state.userId) { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<Int?>(null) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    val store = remember(accounts, state.userId) { accounts.createBillingStore() }
    val billingFlow = remember(store) { store?.state ?: kotlinx.coroutines.flow.MutableStateFlow(BillingState()) }
    val billing by billingFlow.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun manageSubscription() {
        val uri = Uri.Builder().scheme("https").authority("play.google.com")
            .path("store/account/subscriptions")
            .appendQueryParameter("sku", AccountConfiguration.billingCatalog.subscriptionId)
            .appendQueryParameter("package", context.packageName).build()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            pageError = R.string.account_open_failed
        } catch (_: SecurityException) {
            pageError = R.string.account_open_failed
        }
    }

    DisposableEffect(lifecycle, store) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accounts.refresh()
                store?.refresh()
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            accounts.refresh()
            store?.refresh()
        }
        onDispose { lifecycle.removeObserver(observer); store?.close() }
    }
    LaunchedEffect(state.needsPurchase, showPurchaseInitially) {
        if (!state.needsPurchase) showPurchase = false
        else if (showPurchaseInitially) showPurchase = true
    }
    AccountSettingsContent(
        state = state,
        billing = billing,
        modifier = modifier,
        onSignIn = { activity?.let { scope.launch { accounts.signIn(it) } } },
        onSignOut = { scope.launch { accounts.signOut(); showPurchase = false } },
        onDelete = { activity?.let { scope.launch { accounts.deleteAccount(it) } } },
        onRetry = { accounts.refresh() },
        onRestore = { store?.refresh(restoring = true); accounts.refresh() },
        onManageSubscription = ::manageSubscription,
        onPurchaseOptions = { showPurchase = true }
    )
    if (showPurchase && state.needsPurchase) {
        Dialog(
            onDismissRequest = { showPurchase = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                PurchaseScreen(
                    billing = billing,
                    accessGranted = state.grant?.hasPaidAccess(state.elapsedMillis) == true,
                    testCheckout = state.grant?.testCheckoutAllowed == true,
                    onPurchase = { plan -> activity?.let { store?.purchase(it, plan) } },
                    onRestore = { store?.refresh(restoring = true); accounts.refresh() },
                    onRetry = { store?.refresh(); accounts.refresh() },
                    onManageSubscription = ::manageSubscription,
                    onHelp = { showPurchase = false; onHelp() },
                    onPrivacy = { showPrivacy = true },
                    onClose = { showPurchase = false },
                    onAccount = { showPurchase = false }
                )
            }
        }
    }
    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    pageError?.let { message ->
        AlertDialog(
            onDismissRequest = { pageError = null },
            text = { Text(stringResource(message)) },
            confirmButton = {
                KanakoTextButton(onClick = { pageError = null }) {
                    Text(stringResource(R.string.account_close))
                }
            }
        )
    }
}
