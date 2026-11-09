package com.example.harikathamruthasara.data

import android.content.Context
import org.json.JSONArray

/** One word of a verse, with its position. [global] is the index across the whole verse. */
data class WordRef(val line: Int, val index: Int, val global: Int, val text: String)

/**
 * One line as printed in the Tadipatri script. [words] are the rhythmic units between the script's
 * marks; [seps] holds, per word, the mark that follows it:
 *  ' ' word space · '-' gana break · '_' gana break + word space · '|' "--" silent pause · '.' line end
 */
data class Line(
    val words: List<String>, val sentenceEnd: Boolean, val seps: String = " ".repeat(words.size),
    /** per word: '1' = a real word ends after it, '0' = the word runs on (across a dash or into the next line) */
    val wordEnds: String = "1".repeat(words.size),
) {
    /** The mark printed after word [i] ("", " ", "-", "- ", " -- "). */
    fun sepAfter(i: Int): String = when (seps.getOrElse(i) { ' ' }) {
        '-' -> "-"; '_' -> "- "; '|' -> " -- "; '.' -> ""; else -> if (i == words.lastIndex) "" else " "
    }
    /** The line as the script prints it, dashes included. */
    val text: String get() = buildString { words.forEachIndexed { i, w -> append(w).append(sepAfter(i)) } }
}

data class Verse(
    val sandhiId: Int,
    /** 0 = the refrain (ಪಲ್ಲವಿ) sung at the start of the sandhi. */
    val number: Int,
    val lines: List<Line>,
) {
    val key: String = "$sandhiId:$number"
    val isRefrain: Boolean get() = number == 0
    val words: List<WordRef> by lazy {
        var g = 0
        lines.flatMapIndexed { li, line -> line.words.mapIndexed { wi, w -> WordRef(li, wi, g++, w) } }
    }
    val label: String get() = if (isRefrain) "ಪಲ್ಲವಿ" else "ಪದ್ಯ $number"
    val firstLine: String get() = lines.firstOrNull()?.text.orEmpty()
}

data class Sandhi(val id: Int, val title: String, val alt: String, val verses: List<Verse>) {
    val padyaCount: Int get() = verses.count { !it.isRefrain }
}

object Library {
    @Volatile private var cache: List<Sandhi>? = null

    /** The text downloaded from the source-of-truth server (data/Source.kt); used instead of the bundled copy. */
    fun localText(context: Context) = java.io.File(context.filesDir, "text/hks.json")

    /** Forget the loaded text so the next [load] picks up a newly downloaded one. */
    fun invalidate() { cache = null }

    fun load(context: Context): List<Sandhi> {
        cache?.let { return it }
        val local = localText(context)
        val list = local.takeIf { it.exists() }?.let { f -> runCatching { parse(f.readText()) }.getOrNull() }
            ?: parse(context.assets.open("hks.json").bufferedReader().use { it.readText() })
        cache = list
        return list
    }

    /** Parses (and so validates) the text JSON. */
    fun parse(json: String): List<Sandhi> {
        val arr = JSONArray(json)
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getInt("id")
            val verses = mutableListOf<Verse>()
            val intro = o.getJSONArray("intro")
            if (intro.length() > 0) verses += Verse(id, 0, parseLines(intro))
            val vs = o.getJSONArray("verses")
            for (k in 0 until vs.length()) {
                val v = vs.getJSONObject(k)
                verses += Verse(id, v.getInt("n"), parseLines(v.getJSONArray("lines")))
            }
            Sandhi(id, o.getString("title"), o.optString("alt"), verses)
        }
        require(list.isNotEmpty() && list.all { it.verses.isNotEmpty() }) { "text has no padyas" }
        return list
    }

    private fun parseLines(a: JSONArray): List<Line> = (0 until a.length()).map { j ->
        val l = a.getJSONObject(j)
        val words = l.getString("t").split(Regex("\\s+")).filter { it.isNotBlank() }
        Line(
            words, l.getBoolean("e"), l.optString("s").takeIf { it.length == words.size } ?: " ".repeat(words.size),
            l.optString("w").takeIf { it.length == words.size } ?: "1".repeat(words.size),
        )
    }
}

/**
 * Kannada → Tamil script, in the style of Tamil-printed stotra books:
 * ಖ=க², ಗ=க³, ಘ=க⁴ … and Grantha letters ஜ ஶ ஷ ஸ ஹ.
 */
object Translit {
    private val vowels = mapOf(
        'ಅ' to "அ", 'ಆ' to "ஆ", 'ಇ' to "இ", 'ಈ' to "ஈ", 'ಉ' to "உ", 'ಊ' to "ஊ",
        'ಋ' to "ரு", 'ೠ' to "ரூ", 'ಎ' to "எ", 'ಏ' to "ஏ", 'ಐ' to "ஐ", 'ಒ' to "ஒ",
        'ಓ' to "ஓ", 'ಔ' to "ஔ",
    )
    private val signs = mapOf(
        'ಾ' to "ா", 'ಿ' to "ி", 'ೀ' to "ீ", 'ು' to "ு", 'ೂ' to "ூ", 'ೆ' to "ெ",
        'ೇ' to "ே", 'ೈ' to "ை", 'ೊ' to "ொ", 'ೋ' to "ோ", 'ೌ' to "ௌ", '್' to "்",
    )
    private val vocalicR = mapOf('ೃ' to "ரு", 'ೄ' to "ரூ")

    /** consonant → (tamil base, superscript number, nasal class for anusvara) */
    private data class C(val ta: String, val sup: Int, val nasal: String)
    private val cons: Map<Char, C> = buildMap {
        fun g(chars: String, ta: String, nasal: String, sups: IntArray) =
            chars.forEachIndexed { i, ch -> put(ch, C(ta, sups[i], nasal)) }
        g("ಕಖಗಘ", "க", "ங", intArrayOf(1, 2, 3, 4)); put('ಙ', C("ங", 1, "ங"))
        g("ಚಛ", "ச", "ஞ", intArrayOf(1, 2)); g("ಜಝ", "ஜ", "ஞ", intArrayOf(1, 2)); put('ಞ', C("ஞ", 1, "ஞ"))
        g("ಟಠಡಢ", "ட", "ண", intArrayOf(1, 2, 3, 4)); put('ಣ', C("ண", 1, "ண"))
        g("ತಥದಧ", "த", "ந", intArrayOf(1, 2, 3, 4)); put('ನ', C("ந", 1, "ந"))
        g("ಪಫಬಭ", "ப", "ம", intArrayOf(1, 2, 3, 4)); put('ಮ', C("ம", 1, "ம"))
        put('ಯ', C("ய", 1, "ம")); put('ರ', C("ர", 1, "ம")); put('ಱ', C("ற", 1, "ம"))
        put('ಲ', C("ல", 1, "ம")); put('ವ', C("வ", 1, "ம")); put('ಶ', C("ஶ", 1, "ம"))
        put('ಷ', C("ஷ", 1, "ம")); put('ಸ', C("ஸ", 1, "ம")); put('ಹ', C("ஹ", 1, "ம"))
        put('ಳ', C("ள", 1, "ம")); put('ೞ', C("ழ", 1, "ம"))
    }
    private val supChars = arrayOf("", "", "²", "³", "⁴")
    private const val NUKTA = '಼'
    private const val ZWJ = '‍'
    private const val ZWNJ = '‌'

    fun toTamil(kn: String, superscripts: Boolean = true): String {
        val sb = StringBuilder(kn.length + 8)
        var i = 0
        val n = kn.length
        fun peek(k: Int): Char? {
            var j = k
            while (j < n && (kn[j] == NUKTA || kn[j] == ZWJ || kn[j] == ZWNJ)) j++
            return if (j < n) kn[j] else null
        }
        fun skipTo(k: Int): Int {
            var j = k
            while (j < n && (kn[j] == NUKTA || kn[j] == ZWJ || kn[j] == ZWNJ)) j++
            return j
        }
        while (i < n) {
            val ch = kn[i]
            val c = cons[ch]
            when {
                c != null -> {
                    val sup = if (superscripts) supChars[c.sup] else ""
                    sb.append(c.ta)
                    val j = skipTo(i + 1)
                    val next = if (j < n) kn[j] else null
                    when {
                        next != null && vocalicR.containsKey(next) -> {
                            sb.append("்").append(sup).append(vocalicR[next]); i = j + 1
                        }
                        next != null && signs.containsKey(next) -> {
                            sb.append(signs[next]).append(sup); i = j + 1
                        }
                        else -> { sb.append(sup); i = j }
                    }
                }
                vowels.containsKey(ch) -> { sb.append(vowels[ch]); i++ }
                ch == 'ಂ' -> {
                    val next = peek(i + 1)
                    val nc = next?.let { cons[it] }
                    sb.append(nc?.nasal ?: "ம").append("்"); i++
                }
                ch == 'ಃ' -> { sb.append("ஃ"); i++ }
                ch in '೦'..'೯' -> { sb.append('0' + (ch - '೦')); i++ }
                ch == NUKTA || ch == ZWJ || ch == ZWNJ -> i++
                ch == 'ಽ' -> { sb.append('\''); i++ }
                else -> { sb.append(ch); i++ }
            }
        }
        return sb.toString()
    }
}

/** The reader's own script, shown beside (or instead of) Kannada. */
enum class Script { LATIN, TAMIL, DEVANAGARI, TELUGU, KANNADA }

object Scripts {
    fun render(kn: String, script: Script, tamilSuperscripts: Boolean = true): String = when (script) {
        Script.KANNADA -> kn
        Script.TAMIL -> Translit.toTamil(kn, tamilSuperscripts)
        Script.LATIN -> toLatin(kn)
        Script.DEVANAGARI -> shift(kn, -0x380, devaFix)
        Script.TELUGU -> shift(kn, -0x80, emptyMap())
    }

    /** Everyday words whose natural spelling in Tamil and Hindi ends long, unlike the Kannada letters. */
    private val LONG_ENDING = setOf("ಅಮ್ಮ", "ಅಪ್ಪ")

    /**
     * A whole everyday word (Say it practice) spelled the way a reader of that script expects, not letter for letter.
     * Verses keep [render], which follows the printed stotra style exactly.
     */
    fun renderWord(kn: String, script: Script, tamilSuperscripts: Boolean = true): String {
        val text = render(kn, script, tamilSuperscripts)
        return when (script) {
            Script.TAMIL -> tamilNa(text) + if (kn in LONG_ENDING) "ா" else ""
            Script.DEVANAGARI -> text + if (kn in LONG_ENDING) "ा" else ""
            else -> text
        }
    }

    /** Tamil writes ந only at the start of a word or before த; everywhere else the same sound is ன. */
    private fun tamilNa(word: String): String = word.mapIndexed { i, ch ->
        if (ch == 'ந' && i > 0 && !(word.getOrNull(i + 1) == '்' && word.getOrNull(i + 2) == 'த')) 'ன' else ch
    }.joinToString("")

    private val devaFix = mapOf('ೞ' to "ळ", 'ಱ' to "ऱ", 'ೱ' to "", 'ೲ' to "")

    /** Kannada and Telugu/Devanagari blocks are laid out in parallel, so most letters map by offset. */
    private fun shift(kn: String, offset: Int, fix: Map<Char, String>): String {
        val sb = StringBuilder(kn.length)
        for (ch in kn) {
            when {
                fix.containsKey(ch) -> sb.append(fix[ch])
                ch == '಼' -> {}
                ch in '\u0C80'..'\u0CFF' -> sb.append((ch.code + offset).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private val lv = mapOf(
        'ಅ' to "a", 'ಆ' to "ā", 'ಇ' to "i", 'ಈ' to "ī", 'ಉ' to "u", 'ಊ' to "ū", 'ಋ' to "ṛ", 'ೠ' to "ṝ",
        'ಎ' to "e", 'ಏ' to "ē", 'ಐ' to "ai", 'ಒ' to "o", 'ಓ' to "ō", 'ಔ' to "au",
    )
    private val ls = mapOf(
        'ಾ' to "ā", 'ಿ' to "i", 'ೀ' to "ī", 'ು' to "u", 'ೂ' to "ū", 'ೃ' to "ṛ", 'ೄ' to "ṝ",
        'ೆ' to "e", 'ೇ' to "ē", 'ೈ' to "ai", 'ೊ' to "o", 'ೋ' to "ō", 'ೌ' to "au",
    )
    private val lc = mapOf(
        'ಕ' to "k", 'ಖ' to "kh", 'ಗ' to "g", 'ಘ' to "gh", 'ಙ' to "ṅ", 'ಚ' to "c", 'ಛ' to "ch", 'ಜ' to "j", 'ಝ' to "jh", 'ಞ' to "ñ",
        'ಟ' to "ṭ", 'ಠ' to "ṭh", 'ಡ' to "ḍ", 'ಢ' to "ḍh", 'ಣ' to "ṇ", 'ತ' to "t", 'ಥ' to "th", 'ದ' to "d", 'ಧ' to "dh", 'ನ' to "n",
        'ಪ' to "p", 'ಫ' to "ph", 'ಬ' to "b", 'ಭ' to "bh", 'ಮ' to "m", 'ಯ' to "y", 'ರ' to "r", 'ಱ' to "ṟ", 'ಲ' to "l", 'ವ' to "v",
        'ಶ' to "ś", 'ಷ' to "ṣ", 'ಸ' to "s", 'ಹ' to "h", 'ಳ' to "ḷ", 'ೞ' to "ḻ",
    )

    /** ISO 15919-style romanization: ಹರಿಕಥಾಮೃತಸಾರ → harikathāmṛtasāra */
    fun toLatin(kn: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < kn.length) {
            val ch = kn[i]
            val c = lc[ch]
            if (c != null) {
                sb.append(c)
                var j = i + 1
                if (j < kn.length && kn[j] == '಼') j++
                val nx = kn.getOrNull(j)
                when {
                    nx == '್' -> i = j + 1
                    nx != null && ls.containsKey(nx) -> { sb.append(ls[nx]); i = j + 1 }
                    else -> { sb.append('a'); i = j }
                }
                continue
            }
            when {
                lv.containsKey(ch) -> sb.append(lv[ch])
                ch == 'ಂ' -> sb.append('ṁ')
                ch == 'ಃ' -> sb.append('ḥ')
                ch in '೦'..'೯' -> sb.append('0' + (ch - '೦'))
                ch == '\u200C' || ch == '\u200D' || ch == '಼' -> {}
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }
}
