package com.smyddevstudio.kanako.data.tokenizer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text-only evaluation: --tests '*ReadingEvaluationTest' -PreadingEvaluation=true
 * -PevaluationLabel=<label>. Mismatches are reported; only corpus/source invariants are test gates.
 * Reserved fixture families are not an independently annotated or blind real-world benchmark.
 */
class ReadingEvaluationTest {
    private data class Case(
        val id: String, val partition: String, val family: String, val category: String,
        val input: String, val accepted: Set<String>, val reference: String?
    )
    private data class Result(
        val engine: String, val case: Case, val reading: String, val kind: String,
        val totalKanji: Long, val missingKanji: Long, val personTagged: Boolean,
        val rawReading: String, val referenceReading: String, val tokens: String
    )

    @Test
    fun reportReadingsWithoutGuessingAmbiguousGroundTruth() {
        val cases = loadCases()
        assertEquals(cases.size, cases.map { it.id }.distinct().size)
        cases.groupBy { it.family }.forEach { (family, group) ->
            assertTrue("Family crosses scored partitions: $family",
                group.filter { it.partition != "diagnostic" }.map { it.partition }.distinct().size <= 1)
        }
        val results = listOf("sudachi" to ReadingEngineFixtures.sudachi).flatMap { (name, engine) ->
            val tokenizer = JapaneseTextTokenizer { engine }
            cases.map { case ->
                val lines = case.input.split('\n')
                val projected = tokenizer.tokenizeLines(lines)
                assertEquals("Source/line boundaries changed: ${case.id}", lines,
                    projected.map { it.joinToString("") { segment -> segment.text } })
                val segments = projected.flatten()
                val reading = segments.joinToString("") { it.reading ?: it.text }
                val missing = segments.filter { it.reading == null }.sumOf { kanjiCount(it.text) }
                val total = kanjiCount(case.input)
                val raw = engine.tokenize(lines.joinToString(""))
                val expected = case.accepted.map(tokenizer::katakanaToHiragana)
                val kind = when {
                    expected.isEmpty() -> "DIAGNOSTIC"
                    tokenizer.katakanaToHiragana(reading) in expected -> "MATCH"
                    missing > 0 -> "MISSING_READING"
                    else -> "WRONG_READING"
                }
                Result(name, case, reading, kind, total, missing, raw.any { it.isPersonName },
                    raw.joinToString("") { it.reading ?: it.surface },
                    case.reference?.let { reference ->
                        tokenizer.tokenizeLines(reference.split('\n')).flatten().joinToString("") { it.reading ?: it.text }
                    }.orEmpty(),
                    raw.joinToString(" | ") { "${it.surface}=${it.reading}[${it.partOfSpeech.joinToString("/")}]" })
            }
        }
        val report = File(requireNotNull(System.getProperty("reading.evaluation.report")))
        report.mkdirs()
        val summary = buildString {
            appendLine("Developer-authored text fixtures; not general Japanese reading accuracy.")
            appendLine("No image OCR, confidence calibration, real-person pronunciation, or rendered association measured.")
            appendLine("NAME_AMBIGUOUS and OCR_INJECTED are unscored diagnostics; reference text is supplied test evidence.")
            appendLine("A MATCH accepts any listed reading; it does not establish a unique pronunciation.")
            results.groupBy { it.engine to it.case.partition }.forEach { (group, rows) ->
                val scored = rows.filter { it.kind != "DIAGNOSTIC" }
                appendLine("${group.first}/${group.second}: matched=${scored.count { it.kind == "MATCH" }}/${scored.size}" +
                    " wrong=${scored.count { it.kind == "WRONG_READING" }} missing=${scored.count { it.kind == "MISSING_READING" }}" +
                    " annotated_kanji=${scored.sumOf { it.totalKanji - it.missingKanji }}/${scored.sumOf { it.totalKanji }}")
                scored.filter { it.kind != "MATCH" }.forEach { row ->
                    appendLine("  ${row.case.id} ${row.case.category} ${row.kind}: ${escape(row.reading)}; expected=${row.case.accepted.joinToString("|")}")
                }
            }
            appendLine("Exact source/line preservation passed for all ${results.size} engine/case pairs.")
        }
        File(report, "summary.txt").writeText(summary)
        File(report, "readings.tsv").bufferedWriter().use { writer ->
            writer.appendLine("engine\tid\tpartition\tfamily\tcategory\tinput\taccepted\treading\tkind\tmissing_kanji\tperson_tag\traw_reading\treference_text\treference_reading\ttokens")
            results.forEach { row ->
                writer.appendLine(listOf(
                    row.engine, row.case.id, row.case.partition, row.case.family, row.case.category,
                    row.case.input, row.case.accepted.joinToString("|"), row.reading, row.kind,
                    row.missingKanji.toString(), row.personTagged.toString(), row.rawReading,
                    row.case.reference.orEmpty(), row.referenceReading, row.tokens
                ).joinToString("\t", transform = ::escape))
            }
        }
        println(summary)
    }

    private fun loadCases(): List<Case> {
        val stream = requireNotNull(javaClass.getResourceAsStream("/reading-cases.tsv"))
        return stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { row ->
                val columns = row.split('\t')
                require(columns.size == 7) { "Malformed evaluation row" }
                val (id, partition, family, category) = columns
                require(partition in setOf("development", "evaluation", "diagnostic"))
                val accepted = if (columns[5] == "-") emptySet() else columns[5].split('|').toSet()
                require((partition == "diagnostic") == accepted.isEmpty()) { "Diagnostic/scored mismatch: $id" }
                val input = columns[4].replace("\\n", "\n")
                val reference = columns[6].takeUnless { it == "-" }?.replace("\\n", "\n")
                if (category == "OCR_INJECTED") {
                    requireNotNull(reference)
                    assertNotEquals(input, reference)
                }
                Case(id, partition, family, category, input, accepted, reference)
            }.toList()
        }
    }

    private fun kanjiCount(text: String): Long = text.codePoints().filter {
        Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN || it == 0x3005
    }.count()

    private fun escape(value: String) = value.replace("\n", "\\n").replace("\t", "\\t")
}
