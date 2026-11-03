package com.smyddevstudio.kanako.data.tokenizer

import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Run with :app:testDebugUnitTest --tests '*ReadingPerformanceTest' -PreadingPerformance=true
 * -PperformanceLabel=<label>. Host timings exclude OCR, capture, layout and Android rendering.
 */
class ReadingPerformanceTest {
    @Volatile private var consumed = 0

    @Test
    fun measureProjectionAndWarmDictionaryWorkloads() {
        val report = StringBuilder()
        report.appendLine("Host JVM measurements; not Android latency, memory or frame-time evidence.")
        report.appendLine("VM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
        report.appendLine("Projection uses fixed pretokenized input to isolate processing overhead.")
        for (lineCount in listOf(64, 256, 1024)) {
            val lines = List(lineCount) { "日".repeat(16) }
            val tokens = lines.joinToString("").mapIndexed { index, char ->
                JapaneseToken(char.toString(), index, "ニチ", listOf("名詞"))
            }
            val tokenizer = JapaneseTextTokenizer { JapaneseReadingEngine { tokens } }
            val projected = tokenizer.tokenizeLines(lines)
            assertEquals(lines, projected.map { it.joinToString("") { segment -> segment.text } })
            measure(report, "projection lines=$lineCount tokens=${tokens.size}", 40) {
                val result = tokenizer.tokenizeLines(lines)
                consumed = result.size + result.last().size
            }
        }
        val start = System.nanoTime()
        val engine = ReadingEngineFixtures.sudachi
        report.appendLine("Host dictionary fixture lookup ms=${ms(System.nanoTime() - start)} (may already be loaded; no asset install/checksum)")
        val tokenizer = JapaneseTextTokenizer { engine }
        val examples = listOf("日本語を勉強する。", "二人で歩く。", "木の下に猫がいる。", "本を一冊買う。")
        measure(report, "warm Sudachi four-sentence batch", 200) {
            consumed = examples.sumOf { tokenizer.tokenize(it).size }
        }
        report.appendLine("Warmup=10; median/p95 are per call, allocation is current-thread bytes/call when supported.")
        val file = File(requireNotNull(System.getProperty("reading.performance.report")))
        requireNotNull(file.parentFile).mkdirs()
        file.writeText(report.toString())
        println(report)
    }

    private fun measure(report: StringBuilder, label: String, repetitions: Int, action: () -> Unit) {
        repeat(10) { action() }
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        if (bean?.isThreadAllocatedMemorySupported == true) bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        val before = bean?.takeIf { it.isThreadAllocatedMemoryEnabled }?.getThreadAllocatedBytes(thread)
        val times = LongArray(repetitions) {
            val start = System.nanoTime()
            action()
            System.nanoTime() - start
        }
        val after = before?.let { bean.getThreadAllocatedBytes(thread) }
        times.sort()
        report.appendLine("$label median_ms=${ms(times[times.size / 2])} p95_ms=${ms(times[((times.size - 1) * 0.95).toInt()])}" +
            " allocated_bytes_per_call=${if (before != null && after != null) (after - before) / repetitions else "unavailable"}")
    }

    private fun ms(nanos: Long) = String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0)
}
