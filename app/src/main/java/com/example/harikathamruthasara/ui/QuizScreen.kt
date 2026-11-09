package com.example.harikathamruthasara.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.data.ProgressSnapshot
import com.example.harikathamruthasara.data.Sandhi
import com.example.harikathamruthasara.i18n.strings
import com.example.harikathamruthasara.data.Verse
import kotlin.random.Random

private enum class QType(val emoji: String) { FILL("🧩"), NEXT("➡️"), HEAR("👂") }

/**
 * [hearLine]: the verse line chanted for a "listen" question. [answerLine]/[answerWord]: what "hear the answer"
 * chants — a single word (fill-in) or a whole line. All audio is the Vāgdhenu chant.
 */
private class Question(
    val type: QType, val prompt: String, val verse: Verse, val options: List<String>, val answer: Int,
    val hearLine: Int? = null, val answerLine: Int = 0, val answerWord: Int? = null,
)

private object QuizMaker {
    fun pool(s: Sandhi, p: ProgressSnapshot): List<Verse> {
        val padyas = s.verses.filter { !it.isRefrain }
        val practised = padyas.filter { p.stat(it.key).listens > 0 }
        return if (practised.size >= 3) practised else padyas.take(6)
    }

    /** [canHear]: this sandhi's chant is downloaded, so "listen and pick" questions are possible. */
    fun make(s: Sandhi, pool: List<Verse>, rnd: Random, canHear: Boolean): Question {
        val allLines = s.verses.flatMap { it.lines }.map { it.text }.distinct()
        val allWords = s.verses.flatMap { v -> v.words.map { it.text } }.filter { it.length >= 3 }.distinct()
        repeat(20) {
            val v = pool[rnd.nextInt(pool.size)]
            val types = if (canHear) QType.entries else QType.entries - QType.HEAR
            when (types[rnd.nextInt(types.size)]) {
                QType.FILL -> {
                    val li = rnd.nextInt(v.lines.size)
                    val line = v.lines[li]
                    val cands = line.words.indices.filter { line.words[it].length >= 3 }
                    if (line.words.size < 2 || cands.isEmpty()) return@repeat
                    val k = cands[rnd.nextInt(cands.size)]
                    val ans = line.words[k]
                    val wrong = allWords.filter { it != ans && kotlin.math.abs(it.length - ans.length) <= 3 }.shuffled(rnd).take(3)
                    if (wrong.size < 3) return@repeat
                    val prompt = buildString { line.words.forEachIndexed { i, w -> append(if (i == k) "______" else w).append(line.sepAfter(i)) } }
                    val opts = (wrong + ans).shuffled(rnd)
                    val g = v.words.first { it.line == li && it.index == k }.global
                    return Question(QType.FILL, prompt, v, opts, opts.indexOf(ans), answerLine = li, answerWord = g)
                }
                QType.NEXT -> {
                    if (v.lines.size < 2) return@repeat
                    val k = rnd.nextInt(v.lines.size - 1)
                    val ans = v.lines[k + 1].text
                    val wrong = allLines.filter { it != ans && it != v.lines[k].text }.shuffled(rnd).take(3)
                    if (wrong.size < 3) return@repeat
                    val opts = (wrong + ans).shuffled(rnd)
                    return Question(QType.NEXT, v.lines[k].text, v, opts, opts.indexOf(ans), answerLine = k + 1)
                }
                QType.HEAR -> {
                    val li = rnd.nextInt(v.lines.size)
                    val ans = v.lines[li].text
                    val wrong = allLines.filter { it != ans }.shuffled(rnd).take(3)
                    if (wrong.size < 3) return@repeat
                    val opts = (wrong + ans).shuffled(rnd)
                    return Question(QType.HEAR, "", v, opts, opts.indexOf(ans), hearLine = li, answerLine = li)
                }
            }
        }
        val v = pool.first()
        return Question(QType.NEXT, v.firstLine, v, listOf(v.lines.getOrNull(1)?.text ?: v.firstLine), 0, answerLine = 1.coerceAtMost(v.lines.lastIndex))
    }
}

@Composable
fun QuizScreen(app: HkApp, lib: List<Sandhi>, nav: Nav) {
    val p by app.progress.state.collectAsStateWithLifecycle()
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    var sandhiId by remember { mutableIntStateOf(p.lastSandhi) }
    val sandhi = lib.firstOrNull { it.id == sandhiId } ?: lib.first()
    val rnd = remember { Random(System.currentTimeMillis()) }
    var round by remember { mutableIntStateOf(0) }
    var qNo by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var run by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val pool = remember(sandhiId, round) { QuizMaker.pool(sandhi, app.progress.state.value) }
    val q = remember(sandhiId, round, qNo) { QuizMaker.make(sandhi, pool, rnd, canHear = app.vag.isDownloaded(sandhi.id)) }
    val hearAnswer = { if (q.answerWord != null) app.player.sayWord(sandhi.id, q.verse, q.answerWord) else app.player.sayLine(sandhi.id, q.verse, q.answerLine) }
    val total = 10
    var menu by remember { mutableStateOf(false) }
    val S = strings()

    val tour = quizTour()
    LaunchedEffect(q) { app.player.pause(); q.hearLine?.let { app.player.sayLine(sandhi.id, q.verse, it) } }

    Scaffold(topBar = {
        BackBar(S.gamesTitle, nav, "${sandhi.id}. ${mine(sandhi.title, settings.tamilSuperscripts)}") {
            TourHelpButton(tour)
            Box {
                TextButton(onClick = { menu = true }, Modifier.tourTarget(TT.QUIZ_SANDHI)) { Text(short(S.changeSandhi)) }
                DropdownMenu(menu, { menu = false }) {
                    lib.forEach { s -> DropdownMenuItem(text = { Text("${s.id}. ${mine(s.title, settings.tamilSuperscripts)}") }, onClick = { sandhiId = s.id; menu = false; qNo = 0; correct = 0; run = 0; picked = null; round++ }) }
                }
            }
        }
    }) { pad ->
        val t = tones
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).readable(680.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (qNo >= total) {
                // round summary
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = t.marigold.container, contentColor = t.marigold.on)) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (correct >= 8) "🏆" else if (correct >= 5) "🌟" else "🌱", fontSize = 72.sp)
                        Text(S.correctOfTotal(correct, total), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Stars(when { correct >= 9 -> 3; correct >= 7 -> 2; correct >= 4 -> 1; else -> 0 }, size = 30)
                        Text("+" + S.leaves(correct * 3), color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { qNo = 0; correct = 0; picked = null; round++ }) { Text(S.playAgain) }
                    }
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.questionOf(qNo + 1, total), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (run >= 2) Pill(S.inARow(run))
                Spacer(Modifier.padding(4.dp))
                Pill("✔ $correct", t.leaf.container, t.leaf.on)
            }
            LinearProgressIndicator(progress = { qNo / total.toFloat() }, Modifier.fillMaxWidth().height(10.dp), color = t.peacock.accent, trackColor = t.peacock.container, drawStopIndicator = {})

            AutoTour(tour)
            Card(Modifier.tourTarget(TT.QUIZ_Q), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp).fillMaxWidth()) {
                    Text("${q.type.emoji}  " + when (q.type) { QType.FILL -> S.qFill; QType.NEXT -> S.qNext; QType.HEAR -> S.qHear }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(S.verseLabel(q.verse), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(10.dp))
                    if (q.type == QType.HEAR) {
                        FilledTonalButton(onClick = { q.hearLine?.let { app.player.sayLine(sandhi.id, q.verse, it) } }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null); Text("  " + S.hear) }
                        Text(S.pickWhatHeard, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    } else {
                        Text(mine(q.prompt, settings.tamilSuperscripts), fontSize = 21.sp, lineHeight = 32.sp)
                        if (S.lang.script != com.example.harikathamruthasara.data.Script.KANNADA) Text(q.prompt, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            Column(Modifier.tourTarget(TT.QUIZ_ANSWERS), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            q.options.forEachIndexed { i, opt ->
                val chosen = picked
                val border = when {
                    chosen == null -> MaterialTheme.colorScheme.outline
                    i == q.answer -> HkColors.good
                    i == chosen -> HkColors.bad
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
                val bg = when {
                    chosen != null && i == q.answer -> HkColors.goodBg
                    chosen == i -> HkColors.badBg
                    else -> MaterialTheme.colorScheme.surface
                }
                val tone = t[i]
                // the right answer does a little happy jump once revealed
                val jump = remember(q) { androidx.compose.animation.core.Animatable(1f) }
                LaunchedEffect(chosen) {
                    if (chosen != null && i == q.answer) { jump.animateTo(1.06f, androidx.compose.animation.core.tween(120)); jump.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.3f, stiffness = 400f)) }
                }
                BouncyCard(
                    onClick = {
                        picked = i
                        val ok = i == q.answer
                        if (ok) { correct++; run++ } else run = 0
                        app.progress.recordQuiz(ok)
                    },
                    modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = jump.value; scaleY = jump.value },
                    enabled = chosen == null, border = BorderStroke(2.dp, if (chosen == null) tone.accent.copy(alpha = 0.5f) else border),
                    color = if (chosen == null) MaterialTheme.colorScheme.surfaceContainerLow else bg,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(tone.container), contentAlignment = Alignment.Center) {
                            Text("ABCD".getOrElse(i) { '•' }.toString(), fontWeight = FontWeight.Black, color = tone.on)
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mine(opt, settings.tamilSuperscripts), fontSize = 19.sp)
                            if (S.lang.script != com.example.harikathamruthasara.data.Script.KANNADA) Text(opt, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            }
            if (picked != null) {
                val ok = picked == q.answer
                Text(if (ok) S.correctPlus else S.wrongGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = if (ok) HkColors.good else HkColors.bad)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (app.vag.isDownloaded(sandhi.id)) OutlinedButton(onClick = hearAnswer, Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null); Text(S.hearAnswer) }
                    Button(onClick = { picked = null; qNo++ }, Modifier.weight(1f)) { Text(S.next + " ▶") }
                }
            }
        }
    }
}
