package com.smyddevstudio.kanako.data.tokenizer

import android.os.Build
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Install both trial APKs with adb install -r, then push system_full.dic into the target
 * app's external files/sudachi-trial directory and invoke this class with am instrument.
 * Avoid Gradle connectedDebugAndroidTest: its install/cleanup removes the staged file.
 * This test opens no activity and performs no OCR, camera, or screenshot capture.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class SudachiDeviceTrialTest {
    @Test
    fun evaluateCandidateOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "sudachi-trial")
        val memoryBefore = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        var sudachiPss = 0
        val report = SudachiTrial.run(File(directory, "system_full.dic")) {
            sudachiPss = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
        }
        val memoryAfter = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        File(directory, "device.txt").writeText(
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}\n" +
                report +
                "Process total PSS before/after Sudachi load and first sentence/after BOTH engines (KiB): " +
                "${memoryBefore.totalPss}/$sudachiPss/${memoryAfter.totalPss}\n" +
                "PSS is a rough process snapshot, includes instrumentation, and depends on mapped pages touched.\n"
        )
    }
}
