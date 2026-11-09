package com.example.harikathamruthasara.speech

/**
 * Pronunciation scoring from the "ear" (a CTC model over Kannada letters, see [EarStore]). Pure Kotlin so it can be
 * unit-tested; the server's tools/ear/gop.py is the reference version and must give the same numbers.
 *
 * For every letter that has a usual mistake (ಭ→ಬ, ೀ→ಿ, ಣ→ನ, …) the *margin* is
 *     log P(voice | verse)  −  max over mistakes  log P(voice | verse with that letter swapped)
 * > 0: the right sound fits the voice better than the mistake; < 0: the mistake fits better.
 */
object Pronunciation {
    enum class Kind { LENGTH, CONSONANT }

    /** One judged letter: [index] into the scored text, the [best] mistake (its replacement, "" = dropped). */
    data class Mark(val index: Int, val letter: Char, val margin: Float, val kind: Kind, val best: String)

    /** The model's letters, index = model output (must match the server's work/ear/ear_vocab.json). */
    val VOCAB: List<String> = listOf("<s>", "<pad>", "</s>", "<unk>", "|") +
        "ಂ ಃ ಅ ಆ ಇ ಈ ಉ ಊ ಋ ಎ ಏ ಐ ಒ ಓ ಔ ಕ ಖ ಗ ಘ ಚ ಛ ಜ ಝ ಞ ಟ ಠ ಡ ಢ ಣ ತ ಥ ದ ಧ ನ ಪ ಫ ಬ ಭ ಮ ಯ ರ ಲ ಳ ವ ಶ ಷ ಸ ಹ ಾ ಿ ೀ ು ೂ ೃ ೆ ೇ ೈ ೊ ೋ ೌ ್".split(' ')
    private val ID: Map<Char, Int> = VOCAB.withIndex().filter { it.value.length == 1 }.associate { it.value[0] to it.index }
    const val BLANK = 1
    private const val SPACE = 4

    private val ALT: Map<Char, Map<String, Kind>> = buildMap<Char, MutableMap<String, Kind>> {
        fun pair(a: Char, b: Char, k: Kind) { getOrPut(a) { mutableMapOf() }[b.toString()] = k; getOrPut(b) { mutableMapOf() }[a.toString()] = k }
        listOf("ಆಅ", "ಈಇ", "ಊಉ", "ಏಎ", "ಓಒ", "ೀಿ", "ೂು", "ೇೆ", "ೋೊ").forEach { pair(it[0], it[1], Kind.LENGTH) }
        listOf("ಖಕ", "ಘಗ", "ಛಚ", "ಝಜ", "ಠಟ", "ಢಡ", "ಥತ", "ಧದ", "ಫಪ", "ಭಬ", "ಣನ", "ಳಲ", "ಶಸ", "ಷಸ", "ಶಷ").forEach { pair(it[0], it[1], Kind.CONSONANT) }
    }

    /** Keeps only letters the ear knows (and single spaces), so indices into the result line up with the model. */
    fun clean(s: String): String = buildString {
        for (c in s) when {
            c == ' ' -> if (isNotEmpty() && last() != ' ') append(' ')
            c in ID -> append(c)
        }
    }.trim()

    private fun encode(s: String): IntArray = s.mapNotNull { if (it == ' ') SPACE else ID[it] }.toIntArray()

    /** What the ear heard, letter by letter (best path, repeats merged, blanks dropped). */
    fun greedy(lp: Array<FloatArray>): String = buildString {
        var prev = -1
        for (f in lp) {
            var best = 0
            for (k in f.indices) if (f[k] > f[best]) best = k
            if (best != prev && best != BLANK && best > 3) append(if (best == SPACE) " " else VOCAB[best])
            prev = best
        }
    }.trim().replace(Regex(" +"), " ")

    private fun logAdd(a: Double, b: Double): Double =
        if (a == Double.NEGATIVE_INFINITY) b else if (b == Double.NEGATIVE_INFINITY) a
        else if (a > b) a + kotlin.math.ln1p(kotlin.math.exp(b - a)) else b + kotlin.math.ln1p(kotlin.math.exp(a - b))

    /** log P(ids | lp) by the CTC forward algorithm. */
    fun logLik(lp: Array<FloatArray>, ids: IntArray): Double {
        val t = lp.size
        if (t == 0) return Double.NEGATIVE_INFINITY
        val s = 2 * ids.size + 1
        val ext = IntArray(s) { if (it % 2 == 0) BLANK else ids[it / 2] }
        var a = DoubleArray(s) { Double.NEGATIVE_INFINITY }
        a[0] = lp[0][BLANK].toDouble()
        if (s > 1) a[1] = lp[0][ext[1]].toDouble()
        var n = DoubleArray(s)
        for (ti in 1 until t) {
            val f = lp[ti]
            for (j in 0 until s) {
                var v = a[j]
                if (j >= 1) v = logAdd(v, a[j - 1])
                if (j >= 2 && ext[j] != BLANK && ext[j] != ext[j - 2]) v = logAdd(v, a[j - 2])
                n[j] = v + f[ext[j]]
            }
            val tmp = a; a = n; n = tmp
        }
        return if (s > 1) logAdd(a[s - 1], a[s - 2]) else a[s - 1]
    }

    /** Margins for every letter of [text] (already [clean]ed) that has a usual mistake. */
    fun margins(lp: Array<FloatArray>, text: String): List<Mark> {
        val base = logLik(lp, encode(text))
        val out = ArrayList<Mark>()
        for ((i, c) in text.withIndex()) {
            val alts = HashMap(ALT[c].orEmpty())
            if (c == 'ಾ') alts[""] = Kind.LENGTH   // long ā said short = the sign dropped
            // CTC needs a blank between two equal letters, so next to the same letter (ಸತತ, ಜನನ) — or one the swap
            // would make equal — the comparison measures the doubling, not the sound: don't judge those
            val nb = setOf(text.getOrNull(i - 1)?.toString() ?: "", text.getOrNull(i + 1)?.toString() ?: "")
            if (c.toString() in nb) continue
            alts.keys.removeAll { it != "" && it in nb }
            if (alts.isEmpty()) continue
            var bestLl = Double.NEGATIVE_INFINITY; var bestAlt = ""; var kind = Kind.LENGTH
            for ((b, k) in alts) {
                val ll = logLik(lp, encode(text.substring(0, i) + b + text.substring(i + 1)))
                if (ll > bestLl) { bestLl = ll; bestAlt = b; kind = k }
            }
            out += Mark(i, c, (base - bestLl).toFloat(), kind, bestAlt)
        }
        return out
    }

    /** A sound to fix in one word of the step: [said] is how the ear heard the word (the mistake swapped in). */
    data class Issue(val word: Int, val kind: Kind, val margin: Float, val said: String)

    /**
     * Judges the pronunciation of a recited step. [words] are the step's units, [joins] says per unit whether a real
     * word ends after it (units of one word are scored together: ಸ + ಮೀರ = ಸಮೀರ). Only words up to [reached] that
     * were heard ([present]) are judged. Returns the issues per word index, at [strictness].
     */
    fun judge(
        lp: Array<FloatArray>, words: List<String>, joins: List<Boolean>, reached: Int,
        present: (Int) -> Boolean, strictness: Strictness,
    ): Map<Int, List<Issue>> {
        if (strictness.judge.isEmpty() || reached < 0 || lp.isEmpty()) return emptyMap()
        // the text sung so far, and which word each of its letters belongs to
        val owner = ArrayList<Int>(); val starts = IntArray(words.size)
        val text = StringBuilder()
        for (w in 0..reached.coerceAtMost(words.lastIndex)) {
            starts[w] = text.length
            for (c in clean(words[w])) if (c != ' ') { text.append(c); owner += w }
            if (joins.getOrElse(w) { true } && w < reached) { text.append(' '); owner += -1 }
        }
        val out = HashMap<Int, MutableList<Issue>>()
        for (m in margins(lp, text.toString())) {
            val w = owner[m.index]
            if (w < 0 || m.kind !in strictness.judge || m.margin >= strictness.flagBelow || !present(w)) continue
            val unit = clean(words[w]).replace(" ", "")
            val at = m.index - starts[w]
            val said = unit.substring(0, at) + m.best + unit.substring(at + 1)
            out.getOrPut(w) { mutableListOf() } += Issue(w, m.kind, m.margin, said)
        }
        return out
    }
}
