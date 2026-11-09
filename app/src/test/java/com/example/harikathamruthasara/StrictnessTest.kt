package com.example.harikathamruthasara

import com.example.harikathamruthasara.speech.Matcher
import com.example.harikathamruthasara.speech.Strictness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which words were said (the letters the ear heard); pronunciation itself is in PronunciationTest. */
class StrictnessTest {
    private val words = "ಶ್ರೀರ ಮಣಿ ಕರ ಕಮಲ ಪೂಜಿತ ಚಾರು ಚರಣ ಸ ರೋಜ ಬ್ರಹ್ಮ ಸ ಮೀರ ವಾಣಿ ಫ ಣೀಂದ್ರ ವೀಂದ್ರ ಭ ವೇಂದ್ರ ಮುಖ ವಿನು ತ".split(" ")
    private fun scored(heard: String, s: Strictness) = Matcher.withSounds(Matcher.match(words, heard, s), words, emptyMap(), s)

    @Test fun exactRecitationScoresFullAtEveryLevel() {
        val h = "ಶ್ರೀರಮಣಿ ಕರಕಮಲ ಪೂಜಿತ ಚಾರು ಚರಣ ಸರೋಜ ಬ್ರಹ್ಮ ಸಮೀರ ವಾಣಿ ಫಣೀಂದ್ರ ವೀಂದ್ರ ಭವೇಂದ್ರ ಮುಖ ವಿನುತ"
        Strictness.entries.forEach { assertEquals(it.name, 100, scored(h, it).overall) }
    }

    @Test fun skippingAWordMarksItAtEveryLevel() {
        val h = "ಶ್ರೀರಮಣಿ ಕರಕಮಲ ಚಾರು ಚರಣ ಸರೋಜ ಬ್ರಹ್ಮ ಸಮೀರ ವಾಣಿ ಫಣೀಂದ್ರ ವೀಂದ್ರ ಭವೇಂದ್ರ ಮುಖ ವಿನುತ"   // ಪೂಜಿತ left out
        Strictness.entries.forEach { s -> assertTrue(s.name, !scored(h, s).isSaid(4, s)) }
    }

    @Test fun unrelatedTextFailsEveryLevel() {
        Strictness.entries.forEach { assertTrue(scored("ನಾರಾಯಣ ನಮೋ ಗೋವಿಂದ ಹರಿ", it).overall < it.passMark) }
    }

    @Test fun higherLevelsNeedMoreOfEachWord() {
        assertTrue(Strictness.GENTLE.wordPass < Strictness.NORMAL.wordPass && Strictness.NORMAL.wordPass < Strictness.STRICT.wordPass)
        assertTrue(Strictness.GENTLE.passMark < Strictness.NORMAL.passMark && Strictness.NORMAL.passMark < Strictness.STRICT.passMark)
    }
}
