package com.smyddevstudio.kanako.camera

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.ui.screens.HomeScreen
import com.smyddevstudio.kanako.ui.screens.SavedCameraImageScreen
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CameraGalleryTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun homeGalleryEntryWorksWithoutReaderAccessOrTakingAPhoto() {
        var opens = 0
        compose.setContent {
            KanakoTheme { HomeScreen({}, {}, {}, onNavigateToGallery = { opens++ }, animationsEnabled = false) }
        }
        compose.onNodeWithText(context.getString(R.string.gallery_title)).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test
    fun missingSavedImageOffersRetryAndDisablesSharing() {
        compose.setContent {
            KanakoTheme {
                SavedCameraImageScreen(Uri.parse("content://com.smyddevstudio.kanako.missing/image"), "Kanako.png", {})
            }
        }
        val message = context.getString(R.string.gallery_image_missing)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.camera_retry)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.camera_share)).assertIsNotEnabled()
    }

    @Test
    fun documentHistorySurvivesRepositoryRecreationAndRetainsMissingEntries() = runBlocking {
        val preferencesName = "gallery-test-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(preferencesName, mode)
        }
        val uri = Uri.parse("content://com.smyddevstudio.kanako.missing/document")
        try {
            SavedCameraImages(isolated).retainDocument(uri, "Kanako-test.png")
            SavedCameraImages(isolated).retainDocument(uri, "Kanako-test.png")
            val matches = SavedCameraImages(isolated).load().filter { it.uri == uri }
            assertEquals(1, matches.size)
            assertEquals("Kanako-test.png", matches.single().name)
        } finally {
            context.deleteSharedPreferences(preferencesName)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun mediaStoreGalleryIncludesPublishedExportsAndExcludesOtherDirectoriesAndPendingImages() = runBlocking {
        val resolver = context.contentResolver
        val created = mutableListOf<Uri>()
        fun create(path: String, pending: Boolean): Uri {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Kanako-gallery-test-${UUID.randomUUID()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, path)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
            created += uri
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            try {
                requireNotNull(resolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
            if (!pending) resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        }
        try {
            val export = create("Pictures/Kanako/", false)
            val other = create("Pictures/Kanako-gallery-test/", false)
            val pending = create("Pictures/Kanako/", true)
            val ids = SavedCameraImages(context).load().map { it.uri.lastPathSegment }
            assertTrue(export.lastPathSegment in ids)
            assertFalse(other.lastPathSegment in ids)
            assertFalse(pending.lastPathSegment in ids)
        } finally {
            created.forEach { resolver.delete(it, null, null) }
        }
    }
}
