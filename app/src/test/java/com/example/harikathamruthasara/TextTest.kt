package com.example.harikathamruthasara

import com.example.harikathamruthasara.audio.Planner
import com.example.harikathamruthasara.data.Line
import com.example.harikathamruthasara.data.StudyUnit
import com.example.harikathamruthasara.data.Translit
import com.example.harikathamruthasara.data.Script
import com.example.harikathamruthasara.data.Scripts
import com.example.harikathamruthasara.data.Verse
import com.example.harikathamruthasara.speech.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTest {
    @Test fun tamilTransliteration() {
        assertEquals("ஹரிகதா²ம்ருதஸார", Translit.toTamil("ಹರಿಕಥಾಮೃತಸಾರ"))
        assertEquals("ஶ்ரீ", Translit.toTamil("ಶ್ರೀ"))
        assertEquals("மங்க³ள", Translit.toTamil("ಮಂಗಳ"))
        assertEquals("ப⁴க³வத்³ப⁴க்த", Translit.toTamil("ಭಗವದ್ಭಕ್ತ"))
        assertEquals("க்ருஷ்ண", Translit.toTamil("ಕೃಷ್ಣ"))
        assertEquals("க்³ருஹ", Translit.toTamil("ಗೃಹ"))
        assertEquals("ஸந்தி⁴", Translit.toTamil("ಸಂಧಿ"))
        assertEquals("பகவத்பக்த", Translit.toTamil("ಭಗವದ್ಭಕ್ತ", superscripts = false))
        assertEquals("து³꞉க²", Translit.toTamil("ದುಃಖ").replace("ஃ", "꞉"))
        assertEquals("13", Translit.toTamil("೧೩"))
    }

    @Test fun otherScripts() {
        assertEquals("harikathāmṛtasāra", Scripts.toLatin("ಹರಿಕಥಾಮೃತಸಾರ"))
        assertEquals("śrīramaṇi", Scripts.toLatin("ಶ್ರೀರಮಣಿ"))
        assertEquals("maṁgaḷa", Scripts.toLatin("ಮಂಗಳ"))
        assertEquals("pēḷuve", Scripts.toLatin("ಪೇಳುವೆ"))
        assertEquals("हरिकथामृतसार", Scripts.render("ಹರಿಕಥಾಮೃತಸಾರ", Script.DEVANAGARI))
        assertEquals("హరికథామృతసార", Scripts.render("ಹರಿಕಥಾಮೃತಸಾರ", Script.TELUGU))
        assertEquals("ಹರಿ", Scripts.render("ಹರಿ", Script.KANNADA))
    }

    @Test fun perfectRecitationScores100() {
        val words = "ಶ್ರೀರಮಣಿಕರಕಮಲಪೂಜಿತ ಚಾರುಚರಣಸರೋಜ ಬ್ರಹ್ಮಸ".split(" ")
        val r = Matcher.match(words, "ಶ್ರೀರಮಣಿ ಕರಕಮಲ ಪೂಜಿತ ಚಾರು ಚರಣ ಸರೋಜ ಬ್ರಹ್ಮಸ", lenient = true)
        assertEquals(100, r.overall)
        assertTrue(r.wordScores.all { it == 1f })
    }

    @Test fun lenientIgnoresSmallSoundDifferences() {
        val words = listOf("ಕರುಣದಿಂದಾಪನಿತು", "ಪೇಳುವೆ")
        val r = Matcher.match(words, "ಕರುನದಿಂದಾಪನಿತು ಪೆಲುವೆ", lenient = true)
        assertEquals(100, r.overall)
        val strict = Matcher.match(words, "ಕರುನದಿಂದಾಪನಿತು ಪೆಲುವೆ", lenient = false)
        assertTrue(strict.overall < 100)
    }

    @Test fun wrongWordIsMarked() {
        val words = listOf("ನಾರಸಿಂಹನೆ", "ನಮಿಪೆ", "ಕರುಣಿಪುದೆಮಗೆ", "ಮಂಗಳವ")
        val r = Matcher.match(words, "ನಾರಸಿಂಹನೆ ನಮಿಪೆ ಗೋವಿಂದ ಮಂಗಳವ", lenient = true)
        assertTrue(r.isCorrect(0, true)); assertTrue(r.isCorrect(1, true)); assertTrue(r.isCorrect(3, true))
        assertTrue(!r.isCorrect(2, true))
    }

    @Test fun partialRecitationReachesPrefix() {
        val words = listOf("ನೀರಜಭವಾಂಡೋದಯಸ್ಥಿತಿ", "ಕಾರಣನೆ", "ಕೈವಲ್ಯದಾಯಕ")
        val r = Matcher.match(words, "ನೀರಜಭವಾಂಡೋದಯಸ್ಥಿತಿ", lenient = true)
        assertEquals(0, r.reached)
        assertTrue(r.isCorrect(0, true)); assertTrue(!r.isCorrect(1, true))
    }

    @Test fun planner() {
        val v = Verse(1, 1, listOf(
            Line(listOf("a", "b"), false), Line(listOf("c"), false), Line(listOf("d", "e"), true),
            Line(listOf("f"), false), Line(listOf("g"), false), Line(listOf("h", "i"), true),
        ))
        assertEquals(6, Planner.plan(v, StudyUnit.LINE).size)
        val halves = Planner.plan(v, StudyUnit.SENTENCE)
        assertEquals(2, halves.size); assertEquals(0, halves[0].from); assertEquals(5, halves[1].from)
        assertEquals(1, Planner.plan(v, StudyUnit.VERSE).size)
        val b = Planner.plan(v, StudyUnit.BUILDUP)
        assertEquals(listOf(2, 3, 5, 6, 7, 9), b.map { it.words.size })
        val line = Planner.plan(v, StudyUnit.LINE)[2]
        assertEquals(listOf("d", "e"), line.words); assertEquals(3, line.from)
    }

    /** Lines print as the Tadipatri script does: '-' between ganas, ' -- ' at the silent pause. */
    @Test fun scriptMarks() {
        val l = Line(listOf("ಹರಿಕ", "ಥಾಮೃತ", "ಸಾರ", "ಗುರುಗಳ"), false, "-|-.")
        assertEquals("ಹರಿಕ-ಥಾಮೃತ -- ಸಾರ-ಗುರುಗಳ", l.text)
        assertEquals("ಮಿಗೆ- ಹರುಷ-ದಿಂ", Line(listOf("ಮಿಗೆ", "ಹರುಷ", "ದಿಂ"), false, "_-.").text)
        assertEquals("a b", Line(listOf("a", "b"), false).text)   // no marks: plain spaces
        // a word running on into the next line keeps its dash, as printed: "ಬ್ರಹ್ಮ ಸ-" | "ಮೀರ…" (ಸಮೀರ)
        assertEquals("ರೋಜ- ಬ್ರಹ್ಮ ಸ-", Line(listOf("ರೋಜ", "ಬ್ರಹ್ಮ", "ಸ"), false, "_ -").text)
    }

    /** The local source-of-truth server is reached without a proxy; hosted servers keep the device proxy. */
    @Test fun localServerHosts() {
        assertTrue(com.example.harikathamruthasara.data.isLocalHost("10.0.2.2"))
        assertTrue(com.example.harikathamruthasara.data.isLocalHost("192.168.1.20"))
        assertTrue(com.example.harikathamruthasara.data.isLocalHost("localhost"))
        assertTrue(!com.example.harikathamruthasara.data.isLocalHost("hks.example.org"))
        assertTrue(!com.example.harikathamruthasara.data.isLocalHost("8.8.8.8"))
    }

    @Test fun practiceWordsUseNaturalSpelling() {
        val cases = mapOf("ಅಮ್ಮ" to "அம்மா", "ಅಪ್ಪ" to "அப்பா", "ಮನೆ" to "மனெ", "ಅನ್ನ" to "அன்ன", "ಆನೆ" to "ஆனெ", "ಮೀನು" to "மீனு",
            "ಹನುಮ" to "ஹனும", "ಜ್ಞಾನ" to "ஜ்ஞான", "ನೀರು" to "நீரு", "ನಾಯಿ" to "நாயி", "ಚಂದ್ರ" to "சந்த்ர", "ರಾಮ" to "ராம")
        for ((kn, ta) in cases) assertEquals(kn, ta, Scripts.renderWord(kn, Script.TAMIL, false))
        assertEquals("अम्मा", Scripts.renderWord("ಅಮ್ಮ", Script.DEVANAGARI))
        assertEquals("అమ్మ", Scripts.renderWord("ಅಮ್ಮ", Script.TELUGU))
        // verses stay letter for letter
        assertEquals("ஜநந", Scripts.render("ಜನನ", Script.TAMIL, false))
    }
}
