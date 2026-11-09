package com.example.harikathamruthasara.speech

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * Read Kannada → "Say it": did she say this letter / word right?
 *
 * The ear ([EarStore]) is a sentence model; on a lone letter its own verdict is unreliable (it hears ಕ as ಕಾ even from
 * a clean voice). So instead her attempt is compared with REFERENCE recordings of the same model's output
 * (assets/saycheck/refs.bin, made by tools/letters/make_saycheck.py from two Google Kannada voices): DTW over the
 * per-frame letter probabilities. Is her voice closest to the right letter — or to its usual mistake (ಕಾ for ಕ, ಖ for
 * ಕ, ಲ for ಳ …), or to a different letter altogether?
 * tools/letters/saycheck_ref.py is the reference version (and where [K_LETTER]/[SLACK] were tuned, 2026-11-08: with one
 * Google voice as templates and the other as the learner, letters ~2/3 of right ones pass and ~9/10 wrong ones are
 * caught; words 47/50 pass, 50/50 wrong caught). Mistakes INSIDE a word are not judged — not reliable enough.
 */
object SayCheck {
    private const val CLIP = 15f
    private const val BLANK = 1
    /** a letter is "wrong" when this many other letters are closer than it */
    const val K_LETTER = 7
    /** a usual mistake must be this much (relative) closer than the right letter to be pointed out */
    const val SLACK = 0.1f

    sealed interface Verdict {
        data object Ok : Verdict
        /** she said the usual mistake [said] (kind: vowel length / consonant) */
        data class Mistake(val said: String, val kind: Pronunciation.Kind) : Verdict
        /** it sounded like something else — [heard]: the closest item, a guess */
        data class Wrong(val heard: String?) : Verdict
        data object NotHeard : Verdict
    }

    /** One reference: log-probs [frames][66] and their probabilities (precomputed for the KL cost). */
    class Print(val lp: Array<FloatArray>) {
        val p: Array<FloatArray> = Array(lp.size) { i -> FloatArray(lp[i].size) { exp(lp[i][it]) } }
    }

    /** text → fingerprints, one per reference voice */
    class Refs(val ear: String, val prints: Map<String, List<Print>>) {
        operator fun contains(t: String) = t in prints
    }

    fun load(bytes: ByteArray): Refs {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { b.get(it) }
        require(String(magic) == "SAY1") { "not a SayCheck pack" }
        val n = b.int
        val ear = ByteArray(b.short.toInt() and 0xffff).also { b.get(it) }.toString(Charsets.UTF_8)
        val out = HashMap<String, MutableList<Print>>()
        repeat(n) {
            val t = ByteArray(b.short.toInt() and 0xffff).also { b.get(it) }.toString(Charsets.UTF_8)
            b.get() // voice index (order doesn't matter: the closest voice counts)
            val frames = b.short.toInt() and 0xffff
            val lp = Array(frames) { FloatArray(66) { -(b.get().toInt() and 0xff) * CLIP / 255f } }
            out.getOrPut(t) { mutableListOf() } += Print(lp)
        }
        return Refs(ear, out)
    }

    /** Her attempt, like the references: silent (blank) frames dropped, clipped at -15. */
    fun fingerprint(lp: Array<FloatArray>): Print {
        val keep = lp.filter { exp(it[BLANK]) < 0.95f }
        val x = if (keep.size >= 3) keep else lp.toList()
        return Print(Array(x.size) { i -> FloatArray(x[i].size) { maxOf(x[i][it], -CLIP) } })
    }

    /** Mean symmetric-KL frame cost along the best alignment, plus a length-ratio penalty (like saycheck_ref.dtw). */
    fun dtw(a: Print, b: Print): Double {
        val n = a.lp.size; val m = b.lp.size
        if (n == 0 || m == 0) return Double.POSITIVE_INFINITY
        var prev = DoubleArray(m + 1) { Double.POSITIVE_INFINITY }; prev[0] = 0.0
        var cur = DoubleArray(m + 1)
        for (i in 1..n) {
            cur[0] = Double.POSITIVE_INFINITY
            val al = a.lp[i - 1]; val ap = a.p[i - 1]
            for (j in 1..m) {
                val bl = b.lp[j - 1]; val bp = b.p[j - 1]
                var c = 0.0
                for (k in al.indices) c += (ap[k] - bp[k]) * (al[k] - bl[k])
                cur[j] = 0.5 * c + min(prev[j], min(cur[j - 1], prev[j - 1]))
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[m] / (n + m) + 0.5 * abs(ln(n.toDouble() / m))
    }

    private fun dist(her: Print, t: String, refs: Refs): Double =
        refs.prints[t]?.minOfOrNull { dtw(her, it) } ?: Double.POSITIVE_INFINITY

    // ---- usual mistakes (same table as saycheck_ref.py)
    private val SWAP: Map<Char, List<Pair<Char, Pronunciation.Kind>>> = buildMap<Char, MutableList<Pair<Char, Pronunciation.Kind>>> {
        fun pair(a: Char, b: Char, k: Pronunciation.Kind) { getOrPut(a) { mutableListOf() } += b to k; getOrPut(b) { mutableListOf() } += a to k }
        listOf("ಅಆ", "ಇಈ", "ಉಊ", "ಎಏ", "ಒಓ", "ಐಏ", "ಔಓ", "ಿೀ", "ುೂ", "ೆೇ", "ೊೋ", "ೈೇ", "ೌೋ").forEach { pair(it[0], it[1], Pronunciation.Kind.LENGTH) }
        listOf("ಕಖ", "ಗಘ", "ಚಛ", "ಜಝ", "ಟಠ", "ಡಢ", "ತಥ", "ದಧ", "ಪಫ", "ಬಭ", "ಟತ", "ಠಥ", "ಡದ", "ಢಧ", "ಣನ", "ಳಲ", "ಶಸ", "ಷಸ", "ಶಷ", "ಙನ", "ಞನ", "ವಬ")
            .forEach { pair(it[0], it[1], Pronunciation.Kind.CONSONANT) }
    }
    private fun isConsonant(c: Char) = c in 'ಕ'..'ಹ'

    /** Usual mistakes for a letter/syllable: each letter swapped for its usual mistake; a short "a" said long and back. */
    fun mistakes(t: String): List<Pair<String, Pronunciation.Kind>> {
        val out = LinkedHashSet<Pair<String, Pronunciation.Kind>>()
        for ((i, c) in t.withIndex()) for ((b, k) in SWAP[c].orEmpty()) out += (t.substring(0, i) + b + t.substring(i + 1)) to k
        if (t.isNotEmpty() && isConsonant(t.last())) out += (t + "ಾ") to Pronunciation.Kind.LENGTH
        if (t.endsWith("ಾ")) out += t.dropLast(1) to Pronunciation.Kind.LENGTH
        return out.toList()
    }

    /**
     * A letter: [pool] = the other letters it could be confused with (the practice letters); [strictness] decides
     * which mistakes are pointed out (GENTLE: none, only "right letter or not").
     */
    fun judgeLetter(herLp: Array<FloatArray>?, target: String, pool: List<String>, refs: Refs, strictness: Strictness): Verdict {
        if (herLp == null || herLp.isEmpty()) return Verdict.NotHeard
        val her = fingerprint(herLp)
        val ms = mistakes(target)
        val cands = (pool + ms.map { it.first } + target).distinct().filter { it in refs }
        val d = cands.associateWith { dist(her, it, refs) }
        val dt = d[target] ?: return Verdict.Ok      // no reference: can't judge
        val closer = d.filter { (x, v) -> x != target && v < dt }
        if (closer.size >= K_LETTER) return Verdict.Wrong(closer.minByOrNull { it.value }?.key)
        for ((m, k) in ms) {
            val dm = d[m] ?: continue
            if (k in strictness.judge && dm * (1 + SLACK) < dt) return Verdict.Mistake(m, k)
        }
        return Verdict.Ok
    }

    /** A word: right if no other practice word is closer. */
    fun judgeWord(herLp: Array<FloatArray>?, target: String, words: List<String>, refs: Refs): Verdict {
        if (herLp == null || herLp.isEmpty()) return Verdict.NotHeard
        val her = fingerprint(herLp)
        val d = words.filter { it in refs }.associateWith { dist(her, it, refs) }
        val dt = d[target] ?: return Verdict.Ok
        val best = d.minByOrNull { it.value }!!
        return if (best.key == target || best.value >= dt) Verdict.Ok else Verdict.Wrong(best.key)
    }
}
