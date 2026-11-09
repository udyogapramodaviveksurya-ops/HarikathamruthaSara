package com.example.harikathamruthasara

import com.example.harikathamruthasara.i18n.Lang
import com.example.harikathamruthasara.i18n.Strs
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {
    /** every String property of the table, by reflection */
    private fun texts(s: Strs) = Strs::class.java.declaredMethods
        .filter { it.parameterCount == 0 && it.returnType == String::class.java && it.name.startsWith("get") }
        .associate { it.name.removePrefix("get") to (it.invoke(s) as String) }

    @Test fun everyLanguageHasEveryText() {
        for (l in Lang.entries) texts(Strs(l)).forEach { (k, v) -> assertTrue("$l.$k is blank", v.isNotBlank()) }
    }

    @Test fun sanskritIsInDevanagari() {
        val sa = texts(Strs(Lang.SA)); val hi = texts(Strs(Lang.HI))
        val deva = sa.filterValues { v -> v.any { it in 'ऀ'..'ॿ' } }
        assertTrue("only ${deva.size} of ${sa.size} Sanskrit texts use Devanagari", deva.size >= sa.size * 9 / 10)
        // not just the Hindi copied over
        assertTrue(sa.count { (k, v) -> v != hi[k] } >= sa.size * 8 / 10)
        assertTrue(Strs(Lang.SA).levelNames.size == 7)
    }
}
