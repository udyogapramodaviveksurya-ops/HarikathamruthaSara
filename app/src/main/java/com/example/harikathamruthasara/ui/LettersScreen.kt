package com.example.harikathamruthasara.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.data.Scripts
import com.example.harikathamruthasara.i18n.strings

internal val VOWELS = listOf("ಅ", "ಆ", "ಇ", "ಈ", "ಉ", "ಊ", "ಋ", "ಎ", "ಏ", "ಐ", "ಒ", "ಓ", "ಔ", "ಅಂ", "ಅಃ")
internal val CONSONANTS = listOf(
    "ಕ", "ಖ", "ಗ", "ಘ", "ಙ", "ಚ", "ಛ", "ಜ", "ಝ", "ಞ", "ಟ", "ಠ", "ಡ", "ಢ", "ಣ",
    "ತ", "ಥ", "ದ", "ಧ", "ನ", "ಪ", "ಫ", "ಬ", "ಭ", "ಮ", "ಯ", "ರ", "ಲ", "ವ", "ಶ",
    "ಷ", "ಸ", "ಹ", "ಳ", "ಕ್ಷ", "ಜ್ಞ",
)
internal val SIGNS = listOf("", "ಾ", "ಿ", "ೀ", "ು", "ೂ", "ೃ", "ೆ", "ೇ", "ೈ", "ೊ", "ೋ", "ೌ", "ಂ", "ಃ", "್")
internal val GAME_SYLLABLES = listOf("ಕಾ", "ಗಿ", "ದು", "ಭೂ", "ಮೆ", "ನೇ", "ಹೊ", "ಶ್ರೀ", "ಕೃ", "ತ್ತ", "ಮಂ", "ವೈ", "ಧ್ಯ", "ಪ್ರ", "ಲ್ಲ", "ಳು", "ಜ್ಞಾ", "ಷ್ಣು", "ರ್ಮ")

/** Every sound the Letters screen can play (letters/ assets, see letterAsset()). */
internal val ALL_LETTER_SOUNDS = (VOWELS + CONSONANTS + GAME_SYLLABLES + CONSONANTS.flatMap { c -> SIGNS.map { c + it } }).distinct()

/** Simple words for "Say it" (words/ assets, see wordAsset()): everyday first, then words from the bhakti world.
 *  Several carry the sounds learners mix up: ಳ (ಮಳೆ ಕೋಳಿ ಗಿಳಿ ತುಳಸಿ), ಣ (ಹಣ್ಣು ಕಣ್ಣು), ಶ/ಷ (ಶಾಲೆ ಕೃಷ್ಣ), aspirates
 *  (ಭಕ್ತಿ ಧರ್ಮ ಘಂಟೆ ಫಲ), long vowels (ನೀರು ಮೀನು ಊಟ ಓದು ಏಳು ದೀಪ). */
internal val PRACTICE_WORDS = listOf(
    "ಅಮ್ಮ", "ಅಪ್ಪ", "ಮನೆ", "ನೀರು", "ಹಾಲು", "ಅನ್ನ", "ಊಟ", "ಉಪ್ಪು", "ಮರ", "ಹೂವು",
    "ಹಣ್ಣು", "ಎಲೆ", "ಕಣ್ಣು", "ಕಿವಿ", "ಬಾಯಿ", "ಕೈ", "ಕಾಲು", "ಮಗು", "ಆನೆ", "ಹಸು",
    "ಬೆಕ್ಕು", "ನಾಯಿ", "ಇಲಿ", "ಮೀನು", "ಗಿಳಿ", "ಕೋಳಿ", "ಮಳೆ", "ಒಲೆ", "ಓದು", "ಏಳು",
    "ಶಾಲೆ", "ಪುಸ್ತಕ", "ಸೂರ್ಯ", "ಚಂದ್ರ", "ದೀಪ", "ಗಂಗೆ", "ತುಳಸಿ", "ಘಂಟೆ", "ಫಲ", "ಗುರು",
    "ದೇವರು", "ಹರಿ", "ರಾಮ", "ಕೃಷ್ಣ", "ಲಕ್ಷ್ಮಿ", "ಹನುಮ", "ವಿಠಲ", "ಭಕ್ತಿ", "ಧರ್ಮ", "ಜ್ಞಾನ",
)

@Composable
fun LettersScreen(app: HkApp, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val S = strings()
    val tabs = listOf("ಸ್ವರ " + S.tabVowels, "ವ್ಯಂಜನ " + S.tabConsonants, "ಕಾಗುಣಿತ " + S.tabSigns, S.tabSay, S.tabGame)
    val tour = lettersTour()
    AutoTour(tour)
    Scaffold(topBar = { BackBar(S.lettersTitle, nav, S.lettersSub) { TourHelpButton(tour) } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = tab, Modifier.tourTarget(TT.LETTERS_TABS), edgePadding = 8.dp) {   // 5 tabs: scroll, never wrap a word
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 12.sp, textAlign = TextAlign.Center) }) }
            }
            when (tab) {
                0 -> LetterGrid(app, VOWELS, header = S.vowelsHeader)
                1 -> LetterGrid(app, CONSONANTS, header = S.consonantsHeader)
                2 -> KagunitaTab(app)
                3 -> SayItTab(app)
                else -> LetterGame(app)
            }
        }
    }
}

@Composable
private fun LetterGrid(app: HkApp, letters: List<String>, header: String) {
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    LazyVerticalGrid(GridCells.Adaptive(76.dp), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Text(header, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(letters) { i, l -> LetterCell(l, mine(l, settings.tamilSuperscripts), tones[i], if (i == 0) Modifier.tourTarget(TT.LETTERS_FIRST) else Modifier) { app.player.sayLetter(l) } }
    }
}

@Composable
private fun LetterCell(kn: String, ta: String, tone: Tone, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BouncyCard(onClick = onClick, modifier = modifier, color = tone.container, contentColor = tone.on) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(kn, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(ta, fontSize = 18.sp, color = tone.on.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun KagunitaTab(app: HkApp) {
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    var base by rememberSaveable { mutableStateOf("ಕ") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(strings().letterLabel, Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                CONSONANTS.forEach { c ->
                    TextButton(onClick = { base = c }) {
                        Text(c, fontSize = 22.sp, fontWeight = if (c == base) FontWeight.Bold else FontWeight.Normal,
                            color = if (c == base) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        val combos = SIGNS.map { base + it }
        LazyVerticalGrid(GridCells.Adaptive(76.dp), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text(strings().signsHeader, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            itemsIndexed(combos) { i, c -> LetterCell(c, mine(c, settings.tamilSuperscripts), tones[i]) { app.player.sayLetter(c) } }
        }
    }
}

@Composable
private fun LetterGame(app: HkApp) {
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val p by app.progress.state.collectAsStateWithLifecycle()
    val pool = remember {
        VOWELS + CONSONANTS + GAME_SYLLABLES
    }
    var q by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var run by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val S = strings()
    val (target, options) = remember(q) {
        val t = pool.random()
        val wrong = pool.filter { it != t && Scripts.render(it, S.lang.script) != Scripts.render(t, S.lang.script) }.shuffled().take(3)
        t to (wrong + t).shuffled()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).readable(560.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(S.score(score), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            if (run >= 3) Pill("🔥 $run")
            Spacer(Modifier.padding(4.dp))
            Pill(S.forBadge(p.lettersRight))
        }
        Text(S.whichMatches, fontSize = 16.sp)
        BouncyCard(onClick = { app.player.sayLetter(target) }, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Text(target, Modifier.padding(horizontal = 48.dp, vertical = 20.dp), fontSize = 64.sp, fontWeight = FontWeight.Bold)
        }
        options.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, opt ->
                    val i = row * 2 + col
                    val chosen = picked
                    val color = when {
                        chosen == null -> MaterialTheme.colorScheme.outline
                        opt == target -> HkColors.good
                        i == chosen -> HkColors.bad
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                    BouncyCard(
                        onClick = {
                            picked = i
                            val ok = opt == target
                            if (ok) { score++; run++ } else run = 0
                            app.progress.recordLetter(ok)
                            app.player.sayLetter(target)
                        },
                        modifier = Modifier.weight(1f), enabled = chosen == null,
                        border = BorderStroke(2.dp, if (chosen == null) tones[i].accent.copy(alpha = 0.6f) else color),
                        color = tones[i].container, contentColor = tones[i].on,
                    ) {
                        Text(mine(opt, settings.tamilSuperscripts), Modifier.fillMaxWidth().padding(18.dp), fontSize = 30.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (picked != null) {
            Spacer(Modifier.height(4.dp))
            Text(if (options[picked!!] == target) S.rightMark else S.itIs(mine(target, settings.tamilSuperscripts)), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            androidx.compose.material3.Button(onClick = { picked = null; q++ }) { Text(S.next + " ▶") }
        }
    }
}
