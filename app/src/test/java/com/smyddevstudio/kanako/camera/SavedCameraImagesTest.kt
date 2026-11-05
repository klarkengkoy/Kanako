package com.smyddevstudio.kanako.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedCameraImagesTest {
    @Test
    fun keepsSmallImagesAtOriginalResolution() {
        assertEquals(1, galleryDecodeSampleSize(320, 240, 384))
        assertEquals(1, galleryDecodeSampleSize(2048, 2048, 2048))
    }

    @Test
    fun boundsLargePortraitLandscapeAndPanoramaDecodes() {
        for ((width, height) in listOf(8000 to 6000, 6000 to 8000, 32000 to 2000)) {
            for (limit in listOf(384, 2048)) {
                val sample = galleryDecodeSampleSize(width, height, limit)
                assertTrue(sample > 0 && sample.countOneBits() == 1)
                assertTrue((maxOf(width, height) + sample - 1) / sample <= limit)
                assertTrue(maxOf(width, height) / (sample / 2).coerceAtLeast(1) > limit)
            }
        }
    }

    @Test
    fun handlesExtremeImageBoundsWithoutOverflow() {
        assertEquals(1 shl 23, galleryDecodeSampleSize(Int.MAX_VALUE, 1, 384))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnInvalidDecodeLimit() {
        galleryDecodeSampleSize(100, 100, 0)
    }
}
