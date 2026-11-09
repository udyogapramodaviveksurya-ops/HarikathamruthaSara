package com.example.harikathamruthasara.speech

/**
 * How strictly a recitation is checked — above all, how carefully its pronunciation is judged by the ear.
 * [wordPass]: share of a word's letters that must be heard for the word to count as said.
 * [judge]: which sounds the ear judges ([Pronunciation.Kind]); [flagBelow]: a judged letter whose margin is below this
 * is marked as a sound to fix (and its word is not counted correct). Calibrated on Vāgdhenu's chant of sandhi 1:
 * at -3 about 3% of the reference's vowel lengths are flagged, at -1 about 5% of its letters.
 * [passMark]: overall score that counts as a good recitation (green, cheerful chime, hands-free moves on).
 */
enum class Strictness(val wordPass: Float, val passMark: Int, val judge: Set<Pronunciation.Kind>, val flagBelow: Float) {
    /** the right words; pronunciation not judged */
    GENTLE(0.6f, 60, emptySet(), 0f),
    /** + vowel length, when clearly wrong */
    NORMAL(0.75f, 75, setOf(Pronunciation.Kind.LENGTH), -3f),
    /** + aspirates, ಣ/ನ, ಳ/ಲ, ಶ/ಷ/ಸ, and anything leaning wrong (experimental) */
    STRICT(0.85f, 85, setOf(Pronunciation.Kind.LENGTH, Pronunciation.Kind.CONSONANT), -1f),
}

/**
 * Compares what was heard with the verse, letter by letter, so that differences in where words are
 * split ("ಬ್ರಹ್ಮಸ ಮೀರ" vs "ಬ್ರಹ್ಮಸಮೀರ") don't matter. Each target word gets a 0..1 score.
 * Which words were said is always compared leniently; pronunciation is the ear's job ([Pronunciation.judge]).
 */
object Matcher {
    data class Result(
        val wordScores: List<Float>,
        /** index of the last target word reached by the recitation (-1 if none) */
        val reached: Int,
        val overall: Int,
        /** sounds to fix, per word (filled by [withSounds]) */
        val sounds: Map<Int, List<Pronunciation.Issue>> = emptyMap(),
    ) {
        fun isCorrect(i: Int, lenient: Boolean) = wordScores.getOrElse(i) { 0f } >= if (lenient) 0.6f else 0.85f
        fun isSaid(i: Int, s: Strictness) = wordScores.getOrElse(i) { 0f } >= s.wordPass
        /** said, and (when judged) pronounced well */
        fun isCorrect(i: Int, s: Strictness) = isSaid(i, s) && i !in sounds
    }

    /**
     * Adds the ear's verdict and rescores: overall = share of the step's letters that are in correct words, so a
     * mispronounced word costs as much as a missing one.
     */
    fun withSounds(r: Result, targetWords: List<String>, sounds: Map<Int, List<Pronunciation.Issue>>, s: Strictness): Result {
        val withS = r.copy(sounds = sounds)
        val len = targetWords.map { normalize(it, true).length }
        val total = len.sum()
        if (total == 0) return withS
        val good = targetWords.indices.sumOf { if (withS.isCorrect(it, s)) len[it] else 0 }
        return withS.copy(overall = good * 100 / total)
    }

    private val lenientMap: Map<Char, Char> = buildMap {
        // aspirated → plain
        "ಖಕ ಘಗ ಛಚ ಝಜ ಠಟ ಢಡ ಥತ ಧದ ಫಪ ಭಬ".split(' ').forEach { put(it[0], it[1]) }
        // long → short vowels and vowel signs
        "ಆಅ ಈಇ ಊಉ ಏಎ ಓಒ ೀಿ ೂು ೇೆ ೋೊ ಣನ ಳಲ ಶಸ ಷಸ ೠಋ ೄೃ ಙಂ ಞಂ".split(' ').forEach { put(it[0], it[1]) }
    }

    private fun isKannada(c: Char) = c in 'ಀ'..'೿'

    fun normalize(s: String, lenient: Boolean): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            var c = s[i]
            if (!isKannada(c) || c == '಼' || c in '೦'..'೯') { i++; continue }
            if (lenient) {
                // nasal + virama before a consonant ≈ anusvara
                if (c in "ಙಞಣನಮ" && i + 1 < s.length && s[i + 1] == '್') { sb.append('ಂ'); i += 2; continue }
                c = lenientMap[c] ?: c
                if (c == '್' && i + 1 < s.length && !isKannada(s[i + 1])) { i++; continue }
            }
            sb.append(c); i++
        }
        return sb.toString()
    }

    fun match(targetWords: List<String>, heard: String, @Suppress("UNUSED_PARAMETER") s: Strictness): Result = match(targetWords, heard, true)

    fun match(targetWords: List<String>, heard: String, lenient: Boolean): Result {
        val tChars = StringBuilder()
        val owner = ArrayList<Int>()
        val wordLen = IntArray(targetWords.size)
        targetWords.forEachIndexed { wi, w ->
            val n = normalize(w, lenient)
            wordLen[wi] = n.length
            n.forEach { tChars.append(it); owner += wi }
        }
        val h = normalize(heard, lenient)
        val n = tChars.length
        val m = h.length
        if (n == 0) return Result(emptyList(), -1, 0)
        if (m == 0) return Result(List(targetWords.size) { 0f }, -1, 0)

        // Edit distance, target prefix i vs heard prefix j
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val cost = if (tChars[i - 1] == h[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j - 1] + cost, dp[i - 1][j] + 1, dp[i][j - 1] + 1)
        }
        // the recitation may stop part-way: pick the target prefix that best explains everything heard
        var endI = n
        var best = dp[n][m]
        for (i in 0..n) if (dp[i][m] < best) { best = dp[i][m]; endI = i }

        val matched = BooleanArray(n)
        var i = endI
        var j = m
        while (i > 0 && j > 0) {
            val cost = if (tChars[i - 1] == h[j - 1]) 0 else 1
            when {
                dp[i][j] == dp[i - 1][j - 1] + cost -> { if (cost == 0) matched[i - 1] = true; i--; j-- }
                dp[i][j] == dp[i - 1][j] + 1 -> i--
                else -> j--
            }
        }
        val got = IntArray(targetWords.size)
        for (k in 0 until n) if (matched[k]) got[owner[k]]++
        val scores = targetWords.indices.map { wi -> if (wordLen[wi] == 0) 1f else got[wi].toFloat() / wordLen[wi] }
        val reached = if (endI == 0) -1 else owner[endI - 1]
        val overall = (matched.count { it } * 100) / n
        return Result(scores, reached, overall)
    }
}
