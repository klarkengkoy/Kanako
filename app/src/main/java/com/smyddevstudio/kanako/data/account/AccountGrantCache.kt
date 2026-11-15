package com.smyddevstudio.kanako.data.account

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.smyddevstudio.kanako.data.billing.PaidAccess
import com.smyddevstudio.kanako.data.billing.PurchasePlan
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AccountGrantCache(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.noBackupFilesDir, "account_access")
    private val alias = "kanako.account.access.v1"
    private val authority = AccountConfiguration.serverUrl + "\n" + AccountConfiguration.webClientId

    fun read(userId: String): AccountGrant? = runCatching {
        val boot = bootCount()
        if (boot < 0) return null
        val file = file(userId)
        if (!file.baseFile.isFile || file.baseFile.length() > 16_384) return null
        val envelope = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        require(iv.size == 12)
        val encrypted = Base64.decode(envelope.getString("data"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(128, iv))
        cipher.updateAAD((authority + "\n" + userId).toByteArray(Charsets.UTF_8))
        val data = JSONObject(cipher.doFinal(encrypted).toString(Charsets.UTF_8))
        require(data.getInt("version") == 2 && data.getInt("boot") == boot)
        require(data.getString("authority") == authority)
        require(data.getString("userId") == userId)
        val access = when (data.getString("kind")) {
            "none" -> PaidAccess.None
            "lifetime" -> PaidAccess.Lifetime
            "subscription" -> PaidAccess.Subscription(data.getLong("expiresAtMillis"))
            else -> error("Invalid cached access")
        }
        AccountGrant(
            userId, data.getLong("serverNowMillis"), data.getLong("trialStartedAtMillis"),
            data.getLong("trialEndsAtMillis"), access, data.getLong("leaseDurationMillis"),
            data.getLong("receivedAtElapsedMillis"),
            data.optString("plan").takeIf { it.isNotBlank() }?.let { PurchasePlan.valueOf(it) }
        )
    }.getOrNull()

    fun write(grant: AccountGrant) {
        // Test checkout permission is deliberately not cached; opening it requires a server refresh.
        val boot = bootCount()
        if (boot < 0) return
        val data = JSONObject()
            .put("version", 2).put("boot", boot).put("userId", grant.userId).put("authority", authority)
            .put("serverNowMillis", grant.serverNowMillis)
            .put("trialStartedAtMillis", grant.trialStartedAtMillis)
            .put("trialEndsAtMillis", grant.trialEndsAtMillis)
            .put("leaseDurationMillis", grant.leaseDurationMillis)
            .put("receivedAtElapsedMillis", grant.receivedAtElapsedMillis)
            .put("plan", grant.plan?.name ?: "")
        when (val access = grant.paidAccess) {
            PaidAccess.None -> data.put("kind", "none")
            PaidAccess.Lifetime -> data.put("kind", "lifetime")
            is PaidAccess.Subscription -> data.put("kind", "subscription").put("expiresAtMillis", access.expiresAtMillis)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
        cipher.updateAAD((authority + "\n" + grant.userId).toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
        val envelope = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        check(directory.isDirectory || directory.mkdirs())
        val target = file(grant.userId)
        val output = target.startWrite()
        try {
            output.write(envelope.toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(output)
        } catch (error: Exception) {
            target.failWrite(output)
            throw error
        }
    }

    fun clear(userId: String) {
        file(userId).delete()
    }

    private fun bootCount(): Int =
        Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun file(userId: String): AtomicFile {
        val name = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, name))
    }

    private fun existingKey(): SecretKey? =
        (KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as? SecretKey)

    private fun createKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
}
