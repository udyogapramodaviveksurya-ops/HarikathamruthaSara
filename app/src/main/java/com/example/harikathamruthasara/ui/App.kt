package com.example.harikathamruthasara.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.data.Reward
import com.example.harikathamruthasara.data.Sandhi
import com.example.harikathamruthasara.data.Scripts
import com.example.harikathamruthasara.i18n.Lang
import com.example.harikathamruthasara.i18n.LocalStrings
import com.example.harikathamruthasara.i18n.Strs
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface Screen {
    data object Home : Screen
    /** [forRecite]: picking a sandhi → padya for Recite & check instead of for listening. */
    data class Library(val forRecite: Boolean = false) : Screen
    data class Verses(val sandhiId: Int, val forRecite: Boolean = false) : Screen
    data object Learn : Screen
    data class Recite(val sandhiId: Int, val verseIdx: Int) : Screen
    data object Quiz : Screen
    data object Letters : Screen
    data object Progress : Screen
    data object Settings : Screen
    data object Credits : Screen
    data class SettingsSub(val page: SettingsPage) : Screen
    data object Account : Screen
}

class Nav(private val stack: MutableList<Screen>) {
    val current: Screen get() = stack.last()
    fun go(s: Screen) { stack += s }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
    /** Jump to a top-level destination: it sits right above Home, so Back always leads home. */
    fun top(s: Screen) { home(); if (s != Screen.Home) stack += s }
    val depth: Int get() = stack.size
}

@Composable
fun App(app: HkApp) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val nav = remember { Nav(stack) }
    val library by app.library.collectAsStateWithLifecycle()
    val rewards = remember { mutableStateListOf<Reward>() }
    LaunchedEffect(Unit) { app.progress.rewards.collect { rewards += it } }

    BackHandler(enabled = stack.size > 1) { nav.back() }
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val lang = settings.lang

    CompositionLocalProvider(LocalStrings provides Strs(lang ?: Lang.EN), LocalAlsoScripts provides settings.alsoScripts) {
    ProvideWidthClass {
    TourHost {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val lib = library
        if (lang == null) {
            LanguagePicker { picked -> app.settings.update { it.copy(lang = picked) } }
        } else if (lib == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val current = stack.last()
            AdaptiveFrame(current.topDest(), onPick = { d -> nav.top(d.screen()) }) {
                // Material motion: forward = shared axis X (slide in from the end), back = the reverse,
                // switching tabs = fade through.
                AnimatedContent(
                    targetState = nav.depth to current,
                    transitionSpec = {
                        val (from, _) = initialState
                        val (to, _) = targetState
                        val spec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        when {
                            to > from -> (slideInHorizontally(spec) { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally(spec) { -it / 8 } + fadeOut())
                            to < from -> (slideInHorizontally(spec) { -it / 8 } + fadeIn()) togetherWith (slideOutHorizontally(spec) { it / 5 } + fadeOut())
                            else -> (fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(androidx.compose.animation.core.tween(90))
                        }
                    },
                    label = "nav",
                ) { (_, screen) ->
                    CompositionLocalProvider(LocalIsTopLevel provides (screen.topDest() != null)) {
                        ScreenHost(screen, app, lib, nav)
                    }
                }
            }
        }
    }
    }
    }
    rewards.firstOrNull()?.let { r -> RewardDialog(r) { rewards.removeAt(0) } }
    }
}

/** First launch: everything else in the app follows this choice. */
@Composable
fun LanguagePicker(onPick: (Lang) -> Unit) {
    val t = tones
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).readable(560.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        BrandLogo(180.dp)
        Text(androidx.compose.ui.res.stringResource(com.example.harikathamruthasara.R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("ಹರಿಕಥಾಮೃತಸಾರ", fontSize = 22.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))
        Text("Choose your language · மொழி · ಭಾಷೆ · भाषा · భాష · भाषा (संस्कृतम्)", textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        Lang.entries.forEachIndexed { i, l ->
            val tone = t[i]
            BouncyCard(onClick = { onPick(l) }, modifier = Modifier.fillMaxWidth(), color = tone.container, contentColor = tone.on, shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.native, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(l.english + " · " + Scripts.render("ಹರಿಕಥಾಮೃತಸಾರ", l.script), fontSize = 14.sp, color = tone.on.copy(alpha = 0.8f))
                    }
                    Text("›", fontSize = 30.sp, color = tone.accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScreenHost(screen: Screen, app: HkApp, lib: List<Sandhi>, nav: Nav) {
    fun sandhi(id: Int) = lib.first { it.id == id }
    when (screen) {
        Screen.Home -> HomeScreen(app, lib, nav)
        is Screen.Library -> LibraryScreen(app, lib, nav, screen.forRecite)
        is Screen.Verses -> VersesScreen(app, sandhi(screen.sandhiId), nav, screen.forRecite)
        Screen.Learn -> LearnScreen(app, nav)
        is Screen.Recite -> ReciteScreen(app, sandhi(screen.sandhiId), screen.verseIdx, nav)
        Screen.Quiz -> QuizScreen(app, lib, nav)
        Screen.Letters -> LettersScreen(app, nav)
        Screen.Progress -> ProgressScreen(app, nav)
        Screen.Settings -> SettingsScreen(app, nav)
        Screen.Credits -> CreditsScreen(nav)
        is Screen.SettingsSub -> SettingsPageScreen(app, nav, screen.page)
        is Screen.Account -> AccountScreen(app, nav)
    }
}
