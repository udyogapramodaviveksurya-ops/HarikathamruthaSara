package com.example.harikathamruthasara

import com.example.harikathamruthasara.speech.Pronunciation
import com.example.harikathamruthasara.speech.SayCheck
import com.example.harikathamruthasara.speech.Strictness
import com.example.harikathamruthasara.ui.CONSONANTS
import com.example.harikathamruthasara.ui.GAME_SYLLABLES
import com.example.harikathamruthasara.ui.PRACTICE_WORDS
import com.example.harikathamruthasara.ui.VOWELS
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** SayCheck must agree with tools/letters/saycheck_ref.py (fixtures: learner = voice 1, references = voice 0 only). */
class SayCheckTest {
    private val all by lazy { SayCheck.load(File("src/main/assets/saycheck/refs.bin").readBytes()) }
    private val v0 by lazy { SayCheck.Refs(all.ear, all.prints.mapValues { listOf(it.value[0]) }) }
    private val rows by lazy { javaClass.getResourceAsStream("/saycheck_expected.tsv")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.map { it.split('\t') } }
    private val pool = VOWELS + CONSONANTS + GAME_SYLLABLES

    @Test fun packHasEveryPracticeItemInBothVoices() {
        for (t in pool + PRACTICE_WORDS) assertEquals("references for $t", true, (all.prints[t]?.size ?: 0) >= 2)   // ಕೈ is a letter and a word: 4
    }

    @Test fun dtwMatchesPython() {
        for (r in rows.filter { it[0] == "dtw" }) {
            val d = SayCheck.dtw(all.prints[r[1]]!![1], all.prints[r[2]]!![0])
            assertEquals("dtw ${r[1]} ${r[2]}", r[3].toDouble(), d, 1e-3)
        }
    }

    @Test fun letterVerdictsMatchPython() {
        for (r in rows.filter { it[0] == "letter" }) {
            val (_, t, said, st, want, x, k) = r + List(8 - r.size) { "" }
            val v = SayCheck.judgeLetter(all.prints[said]!![1].lp, t, pool, v0, Strictness.valueOf(st))
            val got = when (v) {
                SayCheck.Verdict.Ok -> listOf("ok", "", "")
                is SayCheck.Verdict.Mistake -> listOf("mistake", v.said, if (v.kind == Pronunciation.Kind.LENGTH) "LENGTH" else "CONS")
                is SayCheck.Verdict.Wrong -> listOf("wrong", v.heard ?: "", "")
                SayCheck.Verdict.NotHeard -> listOf("notheard", "", "")
            }
            assertEquals("$t said as $said ($st)", listOf(want, x, k), got)
        }
    }

    @Test fun wordVerdictsMatchPython() {
        for (r in rows.filter { it[0] == "word" }) {
            val v = SayCheck.judgeWord(all.prints[r[2]]!![1].lp, r[1], PRACTICE_WORDS, v0)
            val got = if (v is SayCheck.Verdict.Wrong) listOf("wrong", v.heard ?: "") else listOf("ok", "")
            assertEquals("${r[1]} said as ${r[2]}", listOf(r[3], r.getOrElse(4) { "" }), got)
        }
    }

    @Test fun mistakesTable() {
        assertEquals(listOf("ಖ" to Pronunciation.Kind.CONSONANT, "ಕಾ" to Pronunciation.Kind.LENGTH), SayCheck.mistakes("ಕ"))
        assertEquals(true, SayCheck.mistakes("ಳು").any { it.first == "ಲು" })
        assertEquals(true, SayCheck.mistakes("ಕಾ").any { it.first == "ಕ" })
    }
}

private operator fun <T> List<T>.component6() = this[5]
private operator fun <T> List<T>.component7() = this[6]
