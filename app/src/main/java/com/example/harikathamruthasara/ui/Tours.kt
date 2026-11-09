package com.example.harikathamruthasara.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.harikathamruthasara.i18n.TourText
import com.example.harikathamruthasara.i18n.strings

/** The guide's texts in the app language. */
@Composable
fun tourText(): TourText {
    val lang = strings().lang
    return remember(lang) { TourText(lang) }
}

/** Target ids: one place for every control a guide points at. */
object TT {
    const val HOME_CONTINUE = "home_continue"; const val HOME_TODAY = "home_today"; const val HOME_STREAK = "home_streak"
    const val HOME_TILES = "home_tiles"; const val HOME_SETTINGS = "home_settings"; const val NAV = "nav_bar"
    const val LEARN_VERSE = "learn_verse"; const val LEARN_NAV = "learn_nav"; const val LEARN_PLAY = "learn_play"
    const val LEARN_CHIPS = "learn_chips"; const val LEARN_REPEAT = "learn_repeat"; const val LEARN_RECITE = "learn_recite"; const val LEARN_MORE = "learn_more"
    const val RECITE_VERSE = "recite_verse"; const val RECITE_HEAR = "recite_hear"; const val RECITE_MIC = "recite_mic"; const val RECITE_HANDS = "recite_hands"
    const val LIB_FIRST = "lib_first"; const val VERSES_FIRST = "verses_first"; const val VERSES_ALL = "verses_all"
    const val QUIZ_Q = "quiz_q"; const val QUIZ_SANDHI = "quiz_sandhi"; const val QUIZ_ANSWERS = "quiz_answers"
    const val LETTERS_TABS = "letters_tabs"; const val LETTERS_FIRST = "letters_first"
    const val PROG_STATS = "prog_stats"; const val PROG_CHART = "prog_chart"; const val PROG_BADGES = "prog_badges"
}

@Composable fun homeTour(): Tour { val T = tourText(); return remember(T) { Tour("home", listOf(
    TourStep(null, T.h1t, T.h1b),
    TourStep(TT.HOME_CONTINUE, T.h2t, T.h2b),
    TourStep(TT.HOME_TODAY, T.h3t, T.h3b),
    TourStep(TT.HOME_STREAK, T.h4t, T.h4b),
    TourStep(TT.HOME_TILES, T.h5t, T.h5b),
    TourStep(TT.NAV, T.h6t, T.h6b),
    TourStep(TT.HOME_SETTINGS, T.h7t, T.h7b),
    TourStep(TT.HOME_CONTINUE, T.h8t, T.h8b, tap = true),
)) } }

@Composable fun learnTour(): Tour { val T = tourText(); return remember(T) { Tour("learn", listOf(
    TourStep(TT.LEARN_VERSE, T.l1t, T.l1b),
    TourStep(TT.LEARN_NAV, T.l2t, T.l2b),
    TourStep(TT.LEARN_PLAY, T.l3t, T.l3b, tap = true),
    TourStep(TT.LEARN_CHIPS, T.l4t, T.l4b),
    TourStep(TT.LEARN_REPEAT, T.l5t, T.l5b, tap = true),
    TourStep(TT.LEARN_RECITE, T.l6t, T.l6b),
    TourStep(TT.LEARN_MORE, T.l7t, T.l7b),
)) } }

@Composable fun reciteTour(): Tour { val T = tourText(); return remember(T) { Tour("recite", listOf(
    TourStep(TT.RECITE_VERSE, T.r1t, T.r1b),
    TourStep(TT.RECITE_HEAR, T.r2t, T.r2b),
    TourStep(TT.RECITE_MIC, T.r3t, T.r3b),
    TourStep(TT.RECITE_HANDS, T.r4t, T.r4b),
)) } }

@Composable fun libraryTour(): Tour { val T = tourText(); return remember(T) { Tour("library", listOf(
    TourStep(TT.LIB_FIRST, T.b1t, T.b1b, tap = true),
)) } }

@Composable fun versesTour(): Tour { val T = tourText(); return remember(T) { Tour("verses", listOf(
    TourStep(TT.VERSES_ALL, T.v2t, T.v2b),
    TourStep(TT.VERSES_FIRST, T.v1t, T.v1b, tap = true),
)) } }

@Composable fun quizTour(): Tour { val T = tourText(); return remember(T) { Tour("quiz", listOf(
    TourStep(TT.QUIZ_Q, T.q1t, T.q1b),
    TourStep(TT.QUIZ_SANDHI, T.q2t, T.q2b),
    TourStep(TT.QUIZ_ANSWERS, T.q3t, T.q3b, tap = true),
)) } }

@Composable fun lettersTour(): Tour { val T = tourText(); return remember(T) { Tour("letters", listOf(
    TourStep(TT.LETTERS_TABS, T.k1t, T.k1b),
    TourStep(TT.LETTERS_FIRST, T.k2t, T.k2b, tap = true),
)) } }

@Composable fun progressTour(): Tour { val T = tourText(); return remember(T) { Tour("progress", listOf(
    TourStep(TT.PROG_STATS, T.p1t, T.p1b),
    TourStep(TT.PROG_CHART, T.p2t, T.p2b),
    TourStep(TT.PROG_BADGES, T.p3t, T.p3b),
)) } }
