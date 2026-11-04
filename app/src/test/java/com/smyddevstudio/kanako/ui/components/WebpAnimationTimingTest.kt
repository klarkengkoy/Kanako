package com.smyddevstudio.kanako.ui.components

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebpAnimationTimingTest {
    private class Fixture(val bytes: ByteArray, val offsets: List<Int>)

    private fun fixture(durations: List<Int>): Fixture {
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray())
        fun chunk(tag: String, payload: ByteArray) {
            body.write(tag.toByteArray())
            repeat(4) { body.write(payload.size ushr (it * 8) and 255) }
            body.write(payload)
            if (payload.size % 2 == 1) body.write(0)
        }
        chunk("EXIF", byteArrayOf(7, 8, 9))
        val offsets = durations.map { duration ->
            val offset = 8 + body.size() + 20
            val frame = ByteArray(21) { 71 }
            repeat(3) { frame[12 + it] = (duration ushr (it * 8)).toByte() }
            chunk("ANMF", frame)
            offset
        }
        val output = ByteArrayOutputStream()
        output.write("RIFF".toByteArray())
        repeat(4) { output.write(body.size() ushr (it * 8) and 255) }
        output.write(body.toByteArray())
        return Fixture(output.toByteArray(), offsets)
    }

    private fun duration(bytes: ByteArray, offset: Int) =
        (0..2).sumOf { (bytes[offset + it].toInt() and 255) shl (it * 8) }

    @Test fun retimesAllFramesToExactly700msWithoutChangingPixelsOrTheInput() {
        val fixture = fixture(List(42) { if (it % 3 == 0) 45 else 110 })
        val original = fixture.bytes.copyOf()
        val result = retimeAnimatedWebp(fixture.bytes, 700)
        assertArrayEquals(original, fixture.bytes)
        assertEquals(original.size, result.size)
        assertEquals(700, fixture.offsets.sumOf { duration(result, it) })
        assertTrue(fixture.offsets.all { duration(result, it) > 0 })
        val timingBytes = fixture.offsets.flatMap { listOf(it, it + 1, it + 2) }.toSet()
        original.indices.filterNot { it in timingBytes }.forEach { index ->
            assertEquals("Changed non-timing byte $index", original[index], result[index])
        }
    }

    @Test fun zeroDurationFramesStillReceivePositiveTimes() {
        val fixture = fixture(listOf(0, 0, 0))
        val result = retimeAnimatedWebp(fixture.bytes, 700)
        assertEquals(700, fixture.offsets.sumOf { duration(result, it) })
        assertTrue(fixture.offsets.all { duration(result, it) > 0 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedContainers() {
        val bytes = fixture(listOf(50)).bytes
        retimeAnimatedWebp(bytes.copyOf(bytes.size - 1), 700)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsContainersWithoutFrames() {
        retimeAnimatedWebp(fixture(emptyList()).bytes, 700)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTargetsTooShortToShowEveryFrame() {
        retimeAnimatedWebp(fixture(listOf(50, 50)).bytes, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonWebpData() {
        retimeAnimatedWebp("GIF89aabcdefghijklmnop".toByteArray(), 700)
    }
}
