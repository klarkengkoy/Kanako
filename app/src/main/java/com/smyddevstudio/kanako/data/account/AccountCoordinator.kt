package com.smyddevstudio.kanako.data.account

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.smyddevstudio.kanako.BuildConfig
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.smyddevstudio.kanako.data.billing.PlayBillingStore
import com.smyddevstudio.kanako.data.billing.BillingState
import com.smyddevstudio.kanako.data.billing.PurchasePlan
import com.smyddevstudio.kanako.data.billing.PurchaseEvidence
import com.smyddevstudio.kanako.data.billing.PurchaseVerifier
import com.smyddevstudio.kanako.data.billing.VerifiedPurchase
import com.smyddevstudio.kanako.data.billing.TemporaryPurchaseVerificationException
import java.io.IOException
import com.smyddevstudio.kanako.service.KanakoOverlayService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

internal class AccountCoordinator(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val credentials = CredentialManager.create(app)
    private val auth = if (AccountConfiguration.signInConfigured)
        FirebaseApp.initializeApp(app)?.let { FirebaseAuth.getInstance(it) } else null
    private val server = if (auth != null && AccountConfiguration.accessServiceConfigured)
        AccountServer(AccountConfiguration.serverUrl) else null
    private val mutableState = MutableStateFlow(AccountState(
        configured = auth != null,
        issue = if (auth == null) AccountIssue.SETUP else null,
        accessServiceConfigured = server != null
    ))
    val state = mutableState.asStateFlow()
    private val cache = AccountGrantCache(app)
    private val cacheMutex = Mutex()
    private var refreshJob: Job? = null
    private var revision = 0L
    private var deletionInProgress = false
    private val responseOrder = AccountResponseOrder()
    private var billingStore: PlayBillingStore? = null
    private var billingJob: Job? = null
    private val mutableBillingState = MutableStateFlow(BillingState())
    val billingState = mutableBillingState.asStateFlow()

    private fun closeBillingStore() {
        billingJob?.cancel()
        billingStore?.close()
        billingStore = null
        mutableBillingState.value = BillingState()
    }

    init {
        auth?.addAuthStateListener { firebase ->
            val user = firebase.currentUser
            if (user?.uid != state.value.userId) {
                val previousUserId = state.value.userId
                revision++
                responseOrder.reset()
                val accountRevision = revision
                refreshJob?.cancel()
                closeBillingStore()
                mutableState.value = AccountState(
                    configured = true,
                    userId = user?.uid,
                    email = user?.email,
                    displayName = user?.displayName,
                    profilePhotoUrl = user?.photoUrl?.toString(),
                    elapsedMillis = SystemClock.elapsedRealtime(),
                    accessServiceConfigured = server != null,
                    reviewerSignInPending = state.value.reviewerSignInPending,
                    loginChoicePending = state.value.loginChoicePending,
                    busy = state.value.reviewerSignInPending
                )
                stopReading()
                scope.launch {
                    if (previousUserId != null) clearCachedGrant(previousUserId)
                    if (user != null) {
                        val restored = cacheMutex.withLock {
                            withContext(Dispatchers.IO) { cache.read(user.uid) }
                        }
                        if (revision == accountRevision && auth.currentUser?.uid == user.uid &&
                            state.value.grant == null && restored?.isFresh(SystemClock.elapsedRealtime()) == true) {
                            mutableState.update { it.copy(grant = restored, elapsedMillis = SystemClock.elapsedRealtime()) }
                        }
                        if (revision == accountRevision) refresh()
                    }
                }
            } else if (user != null) {
                mutableState.update {
                    it.copy(
                        email = user.email,
                        displayName = user.displayName,
                        profilePhotoUrl = user.photoUrl?.toString()
                    )
                }
            }
        }
        if (server != null) scope.launch {
            var nextRefresh = SystemClock.elapsedRealtime() + 15 * 60 * 1_000L
            while (isActive) {
                delay(1_000)
                val wasAllowed = state.value.canRead
                val elapsed = SystemClock.elapsedRealtime()
                mutableState.update { it.copy(elapsedMillis = elapsed) }
                if (wasAllowed && !canReadNow()) {
                    stopReading()
                    if (state.value.subscriptionNeedsRefresh || !state.value.hasFreshGrant) refresh()
                    else if (state.value.needsPurchase) requestPaywall()
                }
                if (elapsed >= nextRefresh) {
                    if (state.value.userId != null) refresh()
                    nextRefresh = elapsed + 15 * 60 * 1_000L
                }
            }
        }
    }

    fun canReadNow(): Boolean {
        val uid = auth?.currentUser?.uid ?: return false
        return state.value.grant?.canRead(uid, SystemClock.elapsedRealtime()) == true
    }

    suspend fun testLogin(username: String, password: String): Boolean {
        val firebase = auth ?: return false
        if (!BuildConfig.TEST_LOGIN_ENABLED || server == null || state.value.busy ||
            state.value.reviewerSignInPending || firebase.currentUser != null) return false
        mutableState.update { it.copy(busy = true, reviewerSignInPending = true, issue = null) }
        try {
            val user = firebase.signInWithEmailAndPassword(username.trim(), password).await().user
                ?: error("Missing reviewer identity")
            withTimeout(30_000) {
                state.first { it.userId == user.uid }
                refresh()
                refreshJob?.join()
            }
            if (firebase.currentUser?.uid == user.uid && state.value.testSession && canReadNow()) return true
            signOut()
            return false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            signOut()
            mutableState.update { it.copy(issue = AccountIssue.SIGN_IN) }
            return false
        } finally {
            mutableState.update { it.copy(busy = false, reviewerSignInPending = false) }
        }
    }

    fun serverNowMillis(): Long =
        state.value.grant?.serverNow(SystemClock.elapsedRealtime()) ?: Long.MAX_VALUE

    fun refresh() {
        if (deletionInProgress) return
        val user = auth?.currentUser ?: return
        val service = server ?: return
        if (refreshJob?.isActive == true) return
        val requestRevision = revision
        val request = responseOrder.begin()
        refreshJob = scope.launch {
            mutableState.update { it.copy(busy = !it.hasFreshGrant || it.reviewerSignInPending, issue = null) }
            try {
                val token = user.getIdToken(false).await().token ?: error("Missing account token")
                val grant = service.session(user.uid, token)
                if (revision == requestRevision && auth.currentUser?.uid == user.uid) acceptGrant(grant, request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (revision == requestRevision && responseOrder.isCurrent(request)) {
                    if (error is FirebaseAuthInvalidUserException ||
                        error is AccountServerException && error.status in setOf(401, 403, 410)) {
                        mutableState.update { it.copy(grant = null) }
                        stopReading()
                        clearCachedGrant(user.uid)
                    }
                    if (revision == requestRevision && responseOrder.isCurrent(request)) {
                        mutableState.update { it.copy(issue = AccountIssue.CONNECTION) }
                    }
                }
            } finally {
                if (revision == requestRevision) mutableState.update { it.copy(busy = it.reviewerSignInPending) }
            }
        }
    }

    suspend fun signIn(activity: Activity) {
        val firebase = auth ?: return
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, issue = null) }
        try {
            val googleToken = googleToken(activity)
            mutableState.update { it.copy(loginChoicePending = true) }
            firebase.signInWithCredential(GoogleAuthProvider.getCredential(googleToken, null)).await()
        } catch (_: GetCredentialCancellationException) {
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(issue = AccountIssue.SIGN_IN, loginChoicePending = false) }
        } finally {
            mutableState.update { it.copy(busy = false) }
        }
    }

    suspend fun signOut() {
        scope.launch { finishSignOut() }.join()
    }

    private suspend fun finishSignOut() {
        val previousUserId = auth?.currentUser?.uid
        revision++
        responseOrder.reset()
        refreshJob?.cancel()
        closeBillingStore()
        mutableState.value = AccountState(
            configured = auth != null,
            accessServiceConfigured = server != null
        )
        stopReading()
        auth?.signOut()
        if (previousUserId != null) clearCachedGrant(previousUserId)
        runCatching { credentials.clearCredentialState(ClearCredentialStateRequest()) }
    }

    suspend fun deleteAccount(activity: Activity) {
        val firebase = auth ?: return
        val user = firebase.currentUser ?: return
        val service = server
        if (service == null && AccountConfiguration.enforceReadingAccess) {
            mutableState.update { it.copy(issue = AccountIssue.SETUP) }
            return
        }
        if (state.value.busy) return
        val requestRevision = ++revision
        deletionInProgress = true
        refreshJob?.cancel()
        closeBillingStore()
        mutableState.update { it.copy(busy = true, grant = null, issue = null) }
        try {
            stopReading()
            clearCachedGrant(user.uid)
            val googleToken = googleToken(activity)
            user.reauthenticate(GoogleAuthProvider.getCredential(googleToken, null)).await()
            if (firebase.currentUser?.uid != user.uid || revision != requestRevision) return
            if (service != null) {
                val token = user.getIdToken(true).await().token ?: error("Missing account token")
                service.deleteAccount(token)
                if (revision == requestRevision) signOut()
            } else {
                user.delete().await()
                if (firebase.currentUser == null) signOut()
            }
        } catch (_: GetCredentialCancellationException) {
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (revision == requestRevision) mutableState.update { it.copy(issue = AccountIssue.DELETION) }
        } finally {
            deletionInProgress = false
            if (revision == requestRevision) {
                mutableState.update { it.copy(busy = it.reviewerSignInPending) }
            }
            if (state.value.issue != AccountIssue.DELETION) refresh()
        }
    }

    private fun createBillingStore(): PlayBillingStore? {
        if (state.value.testSession) return null
        val user = auth?.currentUser ?: return null
        if (server == null) return null
        val googleSubject = user.providerData.singleOrNull { it.providerId == "google.com" }?.uid?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
        // Match the server's verified Google identity so purchases survive Firebase account recreation.
        val accountId = MessageDigest.getInstance("SHA-256")
            .digest("com.smyddevstudio.kanako/google/v1/$googleSubject".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return PlayBillingStore(
            app,
            AccountConfiguration.billingCatalog,
            PurchaseVerifier { verify(user.uid, it) },
            purchasesAllowed = {
                val current = state.value.copy(elapsedMillis = SystemClock.elapsedRealtime())
                auth.currentUser?.uid == user.uid && current.userId == user.uid && current.canPurchase
            },
            nowMillis = ::serverNowMillis,
            obfuscatedAccountId = accountId
        )
    }

    private suspend fun verify(userId: String, evidence: PurchaseEvidence): VerifiedPurchase? {
        val firebase = auth ?: return null
        val user = firebase.currentUser?.takeIf { it.uid == userId } ?: return null
        val service = server ?: return null
        val requestRevision = revision
        val request = responseOrder.begin()
        try {
            val token = user.getIdToken(false).await().token ?: error("Missing account token")
            val (grant, verified) = service.verify(userId, token, evidence)
            if (revision != requestRevision || firebase.currentUser?.uid != userId ||
                !acceptGrant(grant, request, purchaseVerification = true)) {
                throw CancellationException("Superseded account verification")
            }
            return verified
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (revision != requestRevision || firebase.currentUser?.uid != userId ||
                !responseOrder.isVerificationCurrent(request)) {
                throw CancellationException("Superseded account verification")
            }
            val temporary = when (error) {
                is AccountServerException -> error.status == 408 || error.status == 429 || error.status in 500..599
                is IOException, is FirebaseNetworkException -> true
                else -> false
            }
            if (temporary) {
                throw TemporaryPurchaseVerificationException(error)
            }
            throw error
        }
    }

    private suspend fun acceptGrant(
        grant: AccountGrant,
        request: Long,
        purchaseVerification: Boolean = false
    ): Boolean {
        if (auth?.currentUser?.uid != grant.userId ||
            !responseOrder.accept(request, purchaseVerification)) return false
        mutableState.update { it.withVerifiedGrant(grant, SystemClock.elapsedRealtime()) }
        if (state.value.testSession) closeBillingStore()
        else if (billingStore == null) {
            billingStore = createBillingStore()
            billingJob = billingStore?.let { store ->
                scope.launch { store.state.collect { mutableBillingState.value = it } }
            }
            billingStore?.refresh()
        }
        if (!canReadNow()) stopReading()
        val grantRevision = revision
        cacheMutex.withLock {
            if (revision == grantRevision && auth.currentUser?.uid == grant.userId && state.value.grant === grant) {
                withContext(Dispatchers.IO) { runCatching { cache.write(grant) } }
            }
        }
        return true
    }

    fun requestPaywall() {
        mutableState.update { if (it.hasFreshGrant && !it.testSession && !it.hasPaidAccess) it.copy(paywallRequested = true) else it }
    }

    fun dismissPaywall() {
        mutableState.update { it.copy(paywallRequested = false) }
    }

    fun resume() {
        refresh()
        billingStore?.refresh()
    }

    fun restorePurchases() {
        billingStore?.refresh(restoring = true, userInitiated = true)
        refresh()
    }

    fun retryPurchases() {
        billingStore?.refresh(userInitiated = true)
        refresh()
    }

    fun purchase(activity: Activity, plan: PurchasePlan) {
        billingStore?.purchase(activity, plan)
    }

    suspend fun startTrial() {
        val user = auth?.currentUser ?: return
        val service = server ?: return
        if (state.value.busy || !state.value.hasFreshGrant ||
            state.value.testSession || state.value.hasPaidAccess || state.value.grant?.trialAvailable != true) return
        val accountRevision = revision
        val request = responseOrder.begin()
        mutableState.update { it.copy(busy = true, issue = null) }
        try {
            val token = user.getIdToken(false).await().token ?: error("Missing account token")
            val grant = service.startTrial(user.uid, token)
            if (revision == accountRevision && auth.currentUser?.uid == user.uid &&
                acceptGrant(grant, request, purchaseVerification = true)) {
                if (state.value.canRead) dismissPaywall()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (revision == accountRevision && responseOrder.isCurrent(request)) {
                if (error is AccountServerException && error.status == 409) refresh()
                mutableState.update { it.copy(issue = AccountIssue.TRIAL) }
            }
        } finally {
            if (revision == accountRevision) mutableState.update { it.copy(busy = false) }
        }
    }

    private suspend fun clearCachedGrant(userId: String) {
        cacheMutex.withLock { withContext(Dispatchers.IO) { cache.clear(userId) } }
    }

    private suspend fun googleToken(activity: Activity): String {
        val option = GetSignInWithGoogleOption.Builder(AccountConfiguration.webClientId).build()
        val response = credentials.getCredential(
            activity,
            GetCredentialRequest.Builder().addCredentialOption(option).build()
        )
        val credential = response.credential as? CustomCredential ?: error("Unexpected credential")
        require(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    private fun stopReading() {
        KanakoOverlayService.stopService(app)
    }
}
