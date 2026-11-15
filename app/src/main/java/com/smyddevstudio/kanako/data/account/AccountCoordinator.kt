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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.smyddevstudio.kanako.data.billing.PlayBillingStore
import com.smyddevstudio.kanako.data.billing.PurchaseEvidence
import com.smyddevstudio.kanako.data.billing.PurchaseVerifier
import com.smyddevstudio.kanako.data.billing.VerifiedPurchase
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

    init {
        auth?.addAuthStateListener { firebase ->
            val user = firebase.currentUser
            if (user?.uid != state.value.userId) {
                val previousUserId = state.value.userId
                revision++
                val accountRevision = revision
                refreshJob?.cancel()
                mutableState.value = AccountState(
                    configured = true,
                    userId = user?.uid,
                    email = user?.email,
                    elapsedMillis = SystemClock.elapsedRealtime(),
                    accessServiceConfigured = server != null
                )
                stopReading()
                scope.launch {
                    if (previousUserId != null) clearCachedGrant(previousUserId)
                    if (user != null) {
                        val restored = cacheMutex.withLock {
                            withContext(Dispatchers.IO) { cache.read(user.uid) }
                        }
                        if (revision == accountRevision && auth.currentUser?.uid == user.uid &&
                            restored?.isFresh(SystemClock.elapsedRealtime()) == true) {
                            mutableState.update { it.copy(grant = restored, elapsedMillis = SystemClock.elapsedRealtime()) }
                        }
                        if (revision == accountRevision) refresh()
                    }
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
                if (wasAllowed && !canReadNow()) stopReading()
                if (elapsed >= nextRefresh) {
                    if (state.value.userId != null) refresh()
                    nextRefresh = elapsed + 15 * 60 * 1_000L
                }
            }
        }
    }

    fun canReadNow(): Boolean {
        if (BuildConfig.TEST_LOGIN_ENABLED && state.value.testSession) return true
        val uid = auth?.currentUser?.uid ?: return false
        return !AccountConfiguration.enforceReadingAccess ||
            state.value.grant?.canRead(uid, SystemClock.elapsedRealtime()) == true
    }

    fun testLogin(username: String, password: String): Boolean {
        if (!BuildConfig.TEST_LOGIN_ENABLED || state.value.busy || auth?.currentUser != null ||
            username != "tester" || password != "tester") return false
        revision++
        refreshJob?.cancel()
        mutableState.update { it.copy(testSession = true, grant = null, issue = null) }
        return true
    }

    fun serverNowMillis(): Long =
        state.value.grant?.serverNow(SystemClock.elapsedRealtime()) ?: Long.MAX_VALUE

    fun refresh() {
        val user = auth?.currentUser ?: return
        val service = server ?: return
        if (refreshJob?.isActive == true) return
        val requestRevision = revision
        refreshJob = scope.launch {
            mutableState.update { it.copy(busy = true, issue = null) }
            try {
                val token = user.getIdToken(false).await().token ?: error("Missing account token")
                val grant = service.session(user.uid, token)
                if (revision == requestRevision && auth.currentUser?.uid == user.uid) acceptGrant(grant)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (revision == requestRevision) {
                    if (error is FirebaseAuthInvalidUserException ||
                        error is AccountServerException && error.status in setOf(401, 403, 410)) {
                        mutableState.update { it.copy(grant = null) }
                        clearCachedGrant(user.uid)
                        stopReading()
                    }
                    mutableState.update { it.copy(issue = AccountIssue.CONNECTION) }
                }
            } finally {
                if (revision == requestRevision) mutableState.update { it.copy(busy = false) }
            }
        }
    }

    suspend fun signIn(activity: Activity) {
        val firebase = auth ?: return
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, issue = null) }
        try {
            val googleToken = googleToken(activity)
            firebase.signInWithCredential(GoogleAuthProvider.getCredential(googleToken, null)).await()
        } catch (_: GetCredentialCancellationException) {
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(issue = AccountIssue.SIGN_IN) }
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
        refreshJob?.cancel()
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
        refreshJob?.cancel()
        mutableState.update { it.copy(busy = true, grant = null, issue = null) }
        clearCachedGrant(user.uid)
        stopReading()
        try {
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
            if (revision == requestRevision) mutableState.update { it.copy(busy = false) }
        }
    }

    fun createBillingStore(): PlayBillingStore? {
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
                auth.currentUser?.uid == user.uid && current.userId == user.uid && current.needsPurchase
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
        val token = user.getIdToken(false).await().token ?: error("Missing account token")
        val (grant, verified) = service.verify(userId, token, evidence)
        if (revision != requestRevision || firebase.currentUser?.uid != userId) return null
        acceptGrant(grant)
        return verified
    }

    private suspend fun acceptGrant(grant: AccountGrant) {
        if (auth?.currentUser?.uid != grant.userId) return
        mutableState.update { it.copy(grant = grant, elapsedMillis = SystemClock.elapsedRealtime(), issue = null) }
        if (!canReadNow()) stopReading()
        val grantRevision = revision
        cacheMutex.withLock {
            if (revision == grantRevision && auth.currentUser?.uid == grant.userId) {
                withContext(Dispatchers.IO) { runCatching { cache.write(grant) } }
            }
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
