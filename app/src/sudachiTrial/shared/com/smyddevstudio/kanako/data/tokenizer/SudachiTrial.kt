package com.smyddevstudio.kanako.data.tokenizer

import com.atilika.kuromoji.ipadic.Tokenizer as KuromojiTokenizer
import com.worksap.nlp.sudachi.Config
import com.worksap.nlp.sudachi.Dictionary
import com.worksap.nlp.sudachi.Tokenizer
import java.io.File
import java.util.Locale

/**
 * Evaluation harness, deliberately outside production/test sources unless -PsudachiTrial=true.
 * Expected readings are a small, hand-authored regression set, not an accuracy benchmark.
 * Empty expectations denote ambiguous names or deliberately corrupted OCR diagnostic inputs.
 */
internal object SudachiTrial {
    private data class Case(val text: String, val expected: String? = null)
    private data class Piece(val surface: String, val reading: String, val start: Int, val end: Int, val pos: String)
    private val cases = listOf(
        Case("二人で歩く。", "フタリデアルク。"),
        Case("一人で待つ。", "ヒトリデマツ。"),
        Case("三人で話す。", "サンニンデハナス。"),
        Case("二人組が来た。", "フタリグミガキタ。"),
        Case("二人称を使う。", "ニニンショウヲツカウ。"),
        Case("一人前の料理。", "イチニンマエノリョウリ。"),
        Case("木の下で休む。", "キノシタデヤスム。"),
        Case("木の下に猫がいる。", "キノシタニネコガイル。"),
        Case("机の下で眠る。", "ツクエノシタデネムル。"),
        Case("木下さんが来た。", "キノシタサンガキタ。"),
        Case("山田先生に聞く。", "ヤマダセンセイニキク。"),
        Case("田中さんと話す。", "タナカサントハナス。"),
        Case("そう言いました。", "ソウイイマシタ。"),
        Case("彼は「ありがとう」と言いました。", "カレハ「アリガトウ」トイイマシタ。"),
        Case("入口で待つ。", "イリグチデマツ。"),
        Case("出口へ向かう。", "デグチヘムカウ。"),
        Case("工場で働く。", "コウジョウデハタラク。"),
        Case("力を合わせる。", "チカラヲアワセル。"),
        Case("夕方に帰る。", "ユウガタニカエル。"),
        Case("八人が集まる。", "ハチニンガアツマル。"),
        Case("ニコニコ笑う。", "ニコニコワラウ。"),
        Case("ロボットが動く。", "ロボットガウゴク。"),
        Case("エアコンを使う。", "エアコンヲツカウ。"),
        Case("カメラで撮る。", "カメラデトル。"),
        Case("タクシーに乗る。", "タクシーニノル。"),
        Case("ハンカチを持つ。", "ハンカチヲモツ。"),
        Case("東京都に住む。", "トウキョウトニスム。"),
        Case("図書館で本を読む。", "トショカンデホンヲヨム。"),
        Case("日本語を勉強する。", "ニホンゴヲベンキョウスル。"),
        Case("橋を渡る。", "ハシヲワタル。"),
        Case("箸で食べる。", "ハシデタベル。"),
        Case("今日も学校に行く。", "キョウモガッコウニイク。"),
        Case("本を一冊買う。", "ホンヲイッサツカウ。"),
        // Boundary preservation probes: spaces, line wrapping, emoji, full-width text.
        Case("二人で\n木の下へ行きました。"),
        Case("彼は言い\nました。"),
        Case("  二人で  歩く。\n"),
        Case("猫🐈とＡＢＣを見る。"),
        // Diagnostic only: no engine is expected to recover missing OCR evidence.
        Case("ニ人で歩く。"),
        Case("入ロで待つ。"),
        Case("エ場で働く。"),
        Case("カを合わせる。"),
        // These spellings admit multiple valid personal-name readings.
        Case("一さんが来た。"),
        Case("東さんと話す。"),
        Case("生田さんに聞く。")
    )

    fun run(dictionaryFile: File, onSudachiLoaded: () -> Unit = {}): String {
        require(dictionaryFile.isFile) { "Missing official full dictionary: ${dictionaryFile.absolutePath}" }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        dictionaryFile.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        require(hash == "7c797531dea76f06b629fad39cb3a5bdb3a9e54c67ef3d9a869e61418426a7d1") {
            "This trial requires the official full dictionary 20261015; SHA-256 was $hash"
        }
        val report = StringBuilder()
        report.appendLine("Sudachi 0.8.2 / SudachiDict full 20261015 / split mode C")
        report.appendLine("Dictionary bytes: ${dictionaryFile.length()}")
        report.appendLine("Runtime: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
        report.appendLine("Hand-authored regression examples; NOT a general Japanese accuracy benchmark.")
        val started = System.nanoTime()
        Dictionary.load(Config.defaultConfig().systemDictionary(dictionaryFile.toPath())).use { dictionary ->
            report.appendLine("Sudachi dictionary load ms: ${ms(System.nanoTime() - started)}")
            val sudachi = dictionary.tokenizer()
            val firstStart = System.nanoTime()
            sudachi.tokenize(Tokenizer.SplitMode.C, cases.first().text)
            report.appendLine("Sudachi first sentence ms: ${ms(System.nanoTime() - firstStart)}")
            onSudachiLoaded()
            val kuromojiStart = System.nanoTime()
            val kuromoji = KuromojiTokenizer()
            report.appendLine("Kuromoji initialization ms: ${ms(System.nanoTime() - kuromojiStart)}")
            val current = JapaneseTextTokenizer { SudachiReadingEngine(dictionary) }
            fun kana(text: String) = current.hiraganaToKatakana(text)
            fun sudachiPieces(text: String) = sudachi.tokenize(Tokenizer.SplitMode.C, text).map {
                Piece(it.surface(), it.readingForm(), it.begin(), it.end(), it.partOfSpeech().joinToString("/"))
            }
            fun kuromojiPieces(text: String) = kuromoji.tokenize(text).map {
                Piece(it.surface, it.reading?.takeUnless { r -> r == "*" }.orEmpty().ifEmpty { it.surface },
                    it.position, it.position + it.surface.length, it.partOfSpeechLevel1 + "/" + it.partOfSpeechLevel2)
            }
            fun reading(text: String, pieces: List<Piece>): String = buildString {
                var cursor = 0
                pieces.forEach {
                    check(it.start >= cursor && it.end <= text.length && it.end >= it.start) {
                        "Invalid span: ${it.surface} [${it.start},${it.end})"
                    }
                    check(text.substring(it.start, it.end) == it.surface) { "Surface changed: $text -> ${it.surface}" }
                    append(text.substring(cursor, it.start))
                    append(it.reading)
                    cursor = it.end
                }
                append(text.substring(cursor))
            }.let(::kana)
            fun currentReading(text: String): String {
                val segments = current.tokenize(text)
                check(segments.joinToString("") { it.text } == text)
                return kana(segments.joinToString("") { it.reading ?: it.text })
            }
            var sudachiMatches = 0
            var rawMatches = 0
            var currentMatches = 0
            var scored = 0
            report.appendLine("input\texpected\tSudachi\traw Kuromoji\tcurrent Kanako")
            cases.forEach { case ->
                val sp = sudachiPieces(case.text)
                val kp = kuromojiPieces(case.text)
                val sr = reading(case.text, sp)
                val kr = reading(case.text, kp)
                val cr = currentReading(case.text)
                if (case.expected != null) {
                    scored++
                    if (sr == case.expected) sudachiMatches++
                    if (kr == case.expected) rawMatches++
                    if (cr == case.expected) currentMatches++
                }
                fun escaped(value: String) = value.replace("\n", "\\n").replace("\t", "\\t")
                report.appendLine(listOf(case.text, case.expected ?: "(diagnostic)", sr, kr, cr).joinToString("\t", transform = ::escaped))
                report.appendLine("  S: " + sp.joinToString(" | ") { "${it.surface}=${it.reading} [${it.pos}]" })
            }
            report.appendLine("Exact expected readings: Sudachi $sudachiMatches/$scored; raw Kuromoji $rawMatches/$scored; current Kanako $currentMatches/$scored")
            report.appendLine("Original surfaces/spans preserved on all ${cases.size} inputs.")
            // A small smoke timing, not AndroidX Benchmark: excludes initialization, includes tokenization.
            fun timing(label: String, action: (String) -> Unit) {
                repeat(5) { cases.forEach { action(it.text) } }
                val times = ArrayList<Long>()
                repeat(20) {
                    cases.forEach {
                        val start = System.nanoTime()
                        action(it.text)
                        times += System.nanoTime() - start
                    }
                }
                times.sort()
                report.appendLine("$label warm per-sentence median/p95 ms: ${ms(times[times.size / 2])}/${ms(times[(times.size * .95).toInt()])} (n=${times.size})")
            }
            timing("Sudachi") { sudachi.tokenize(Tokenizer.SplitMode.C, it) }
            timing("Current Kanako") { current.tokenize(it) }
            report.appendLine("Timings are single-process smoke measurements, not a statistically controlled engine benchmark.")
        }
        return report.toString()
    }

    private fun ms(nanos: Long) = String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0)
}
