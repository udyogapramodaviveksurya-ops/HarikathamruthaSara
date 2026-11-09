package com.example.harikathamruthasara

import com.example.harikathamruthasara.speech.Matcher
import com.example.harikathamruthasara.speech.Pronunciation
import com.example.harikathamruthasara.speech.Strictness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PronunciationTest {
    /** The ear's real output for Vāgdhenu's padya 1 (first half), dumped by the server's tools/ear (int8 model). */
    private val lp: Array<FloatArray> by lazy {
        val b = ByteBuffer.wrap(javaClass.getResourceAsStream("/ear_h0_lp.bin")!!.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val t = b.int; val v = b.int
        Array(t) { FloatArray(v) { b.float } }
    }
    private val expected by lazy { javaClass.getResourceAsStream("/ear_h0_expected.tsv")!!.bufferedReader(Charsets.UTF_8).readLines() }

    @Test fun matchesThePythonReference() {
        val text = expected[0]
        assertEquals(expected[1].toDouble(), Pronunciation.logLik(lp, text.map { if (it == ' ') 4 else Pronunciation.VOCAB.indexOf(it.toString()) }.toIntArray()), 1e-3)
        val want = expected.drop(2).filter { it.isNotBlank() }.map { it.split('\t') }
        val got = Pronunciation.margins(lp, text)
        assertEquals(want.size, got.size)
        for ((w, g) in want.zip(got)) {
            assertEquals(w[0].toInt(), g.index)
            assertEquals(w[2].toDouble(), g.margin.toDouble(), 1e-2)
            assertEquals(if (w[3] == "len") Pronunciation.Kind.LENGTH else Pronunciation.Kind.CONSONANT, g.kind)
        }
    }

    @Test fun greedyHearsTheChant() {
        val heard = Pronunciation.greedy(lp)
        val r = Matcher.match("ಶ್ರೀರ ಮಣಿ ಕರ ಕಮಲ ಪೂಜಿತ ಚಾರು ಚರಣ ಸ ರೋಜ ಬ್ರಹ್ಮ ಸ ಮೀರ ವಾಣಿ ಫ ಣೀಂದ್ರ ವೀಂದ್ರ ಭ ವೇಂದ್ರ ಮುಖ ವಿನು ತ".split(" "), heard, true)
        assertTrue("$heard → ${r.overall}", r.overall >= 90)
    }

    /** A made-up ear output that "hears" [said] clearly: each letter 4 frames, a blank between letters. */
    private fun clearly(said: String): Array<FloatArray> {
        val ids = said.map { if (it == ' ') 4 else Pronunciation.VOCAB.indexOf(it.toString()) }
        val frames = ArrayList<FloatArray>()
        fun frame(k: Int) = FloatArray(Pronunciation.VOCAB.size) { if (it == k) -0.05f else -6f }
        frames += frame(Pronunciation.BLANK)
        for (k in ids) { repeat(4) { frames += frame(k) }; frames += frame(Pronunciation.BLANK) }
        return frames.toTypedArray()
    }

    private val words = listOf("ಸ", "ಮೀರ", "ವಾಣಿ", "ಭವೇಂದ್ರ")
    private val joins = listOf(false, true, true, true)          // ಸ + ಮೀರ is one word: ಸಮೀರ

    @Test fun rightSoundsHaveNoIssues() {
        val j = Pronunciation.judge(clearly("ಸಮೀರ ವಾಣಿ ಭವೇಂದ್ರ"), words, joins, 3, { true }, Strictness.STRICT)
        assertTrue(j.toString(), j.isEmpty())
    }

    @Test fun shortVowelIsCaughtFromNormalUp() {
        val ear = clearly("ಸಮಿರ ವಾಣಿ ಭವೇಂದ್ರ")             // ಮೀ said short
        assertTrue(Pronunciation.judge(ear, words, joins, 3, { true }, Strictness.GENTLE).isEmpty())
        val normal = Pronunciation.judge(ear, words, joins, 3, { true }, Strictness.NORMAL)
        assertEquals(setOf(1), normal.keys)
        assertEquals("ಮಿರ", normal.getValue(1).single().said)
        assertEquals(setOf(1), Pronunciation.judge(ear, words, joins, 3, { true }, Strictness.STRICT).keys)
    }

    @Test fun unaspiratedIsCaughtOnlyByStrict() {
        val ear = clearly("ಸಮೀರ ವಾಣಿ ಬವೇಂದ್ರ")             // ಭ said as ಬ
        assertTrue(Pronunciation.judge(ear, words, joins, 3, { true }, Strictness.NORMAL).isEmpty())
        val strict = Pronunciation.judge(ear, words, joins, 3, { true }, Strictness.STRICT)
        assertEquals(setOf(3), strict.keys)
        assertEquals("ಬವೇಂದ್ರ", strict.getValue(3).single().said)
    }

    @Test fun aSoundToFixCostsTheWholeWord() {
        val r = Matcher.match(words, "ಸಮಿರ ವಾಣಿ ಭವೇಂದ್ರ", Strictness.NORMAL)
        val sounds = Pronunciation.judge(clearly("ಸಮಿರ ವಾಣಿ ಭವೇಂದ್ರ"), words, joins, r.reached, { r.isSaid(it, Strictness.NORMAL) }, Strictness.NORMAL)
        val scored = Matcher.withSounds(r, words, sounds, Strictness.NORMAL)
        assertTrue(scored.isSaid(1, Strictness.NORMAL)); assertTrue(!scored.isCorrect(1, Strictness.NORMAL))
        assertTrue(scored.overall in 70..90)
        assertEquals(100, Matcher.withSounds(r, words, emptyMap(), Strictness.GENTLE).overall)
    }
}
