package com.smyddevstudio.kanako

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.data.account.AccountGrant
import com.smyddevstudio.kanako.data.account.AccountGrantCache
import com.smyddevstudio.kanako.data.billing.PaidAccess
import com.smyddevstudio.kanako.data.billing.TRIAL_DURATION_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountGrantCacheTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun grant(userId: String) = AccountGrant(
        userId, 1_000, 1_000, 1_000 + TRIAL_DURATION_MILLIS,
        PaidAccess.None, 60_000, SystemClock.elapsedRealtime()
    )

    private fun file(userId: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(context.noBackupFilesDir, "account_access/" + name)
    }

    @Test fun recreatedCacheKeepsTheOriginalTrialDeadlineAndClockAnchor() {
        val userId = "cache-test-" + UUID.randomUUID()
        val original = grant(userId)
        val cache = AccountGrantCache(context)
        try {
            cache.write(original)
            assertEquals(original, AccountGrantCache(context).read(userId))
            cache.clear(userId)
            assertNull(cache.read(userId))
        } finally {
            cache.clear(userId)
        }
    }

    @Test fun copyingAnEncryptedRecordToAnotherAccountDoesNotGrantAccess() {
        val first = "cache-test-" + UUID.randomUUID()
        val second = "cache-test-" + UUID.randomUUID()
        val cache = AccountGrantCache(context)
        try {
            cache.write(grant(first))
            file(first).copyTo(file(second), overwrite = true)
            assertNull(cache.read(second))
        } finally {
            cache.clear(first)
            cache.clear(second)
        }
    }

    @Test fun damagedCachedDataRequiresANewServerCheck() {
        val userId = "cache-test-" + UUID.randomUUID()
        val cache = AccountGrantCache(context)
        try {
            cache.write(grant(userId))
            file(userId).writeText("invalid encrypted access", Charsets.UTF_8)
            assertNull(cache.read(userId))
        } finally {
            cache.clear(userId)
        }
    }
}
