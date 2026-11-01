package com.smyddevstudio.kanako.data.tokenizer

import java.io.File
import org.junit.Test

class SudachiTrialTest {
    @Test
    fun evaluateCandidateWithoutChangingActiveReader() {
        val report = SudachiTrial.run(File(requireNotNull(System.getProperty("sudachi.dictionary"))))
        val destination = File(requireNotNull(System.getProperty("sudachi.report")))
        requireNotNull(destination.parentFile).mkdirs()
        destination.writeText(report)
        println(report)
        // Linguistic mismatches belong in the report; passing means the evaluation completed.
    }
}
