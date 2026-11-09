package com.example.harikathamruthasara.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.harikathamruthasara.data.Reward
import com.example.harikathamruthasara.data.ScriptMode
import com.example.harikathamruthasara.data.Script
import com.example.harikathamruthasara.data.Scripts
import com.example.harikathamruthasara.data.LEVELS
import com.example.harikathamruthasara.i18n.strings
import com.example.harikathamruthasara.audio.Step
import com.example.harikathamruthasara.audio.StepKind
import com.example.harikathamruthasara.i18n.Strs
import com.example.harikathamruthasara.data.Verse
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** How a single word should look. */
/** Reading scripts shown under every verse line besides the language's own (Settings → also show in). */
val LocalAlsoScripts = androidx.compose.runtime.staticCompositionLocalOf<Set<Script>> { emptySet() }

/** The order extra scripts are shown in, and each one's name in its own script. */
val ALSO_SCRIPTS = listOf(Script.DEVANAGARI to "देवनागरी", Script.TAMIL to "தமிழ்", Script.TELUGU to "తెలుగు", Script.LATIN to "Latin", Script.KANNADA to "ಕನ್ನಡ")

/** Chips to pick more reading scripts (the language's own and Kannada are always there, so they aren't offered). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AlsoScriptsPicker(selected: Set<Script>, onChange: (Set<Script>) -> Unit) {
    val own = strings().lang.script
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ALSO_SCRIPTS.filter { it.first != own && it.first != Script.KANNADA }.forEach { (sc, name) ->
            androidx.compose.material3.FilterChip(
                selected = sc in selected,
                onClick = { onChange(if (sc in selected) selected - sc else selected + sc) },
                label = { Text(name, fontSize = 16.sp) },
            )
        }
    }
}

/** SOUND = said, but a sound in it needs fixing (Recite & check) */
enum class WordLook { NORMAL, IN_STEP, CURRENT, GOOD, BAD, SOUND, NEXT, HIDDEN }

@Composable
fun VerseView(
    verse: Verse,
    script: ScriptMode,
    superscripts: Boolean,
    fontScale: Float,
    look: (Int) -> WordLook,
    modifier: Modifier = Modifier,
    hideTamil: Boolean = false,
    onlyLines: Set<Int>? = null,
    focusLine: Int = -1,
    onWordTap: ((Int) -> Unit)? = null,
) {
    val requesters = remember(verse) { List(verse.lines.size) { androidx.compose.foundation.relocation.BringIntoViewRequester() } }
    LaunchedEffect(focusLine, verse) { requesters.getOrNull(focusLine)?.bringIntoView() }
    val cs = MaterialTheme.colorScheme
    val reader = strings().lang.script
    val mine = remember(verse, superscripts, reader) { verse.words.map { Scripts.render(it.text, reader, superscripts) } }
    val showKn = script != ScriptMode.MINE || reader == Script.KANNADA
    val showMine = reader != Script.KANNADA && script != ScriptMode.KANNADA && !hideTamil
    val mineOnly = showMine && !showKn
    // more scripts the reader asked for (e.g. Devanagari), each as its own smaller line
    val also = LocalAlsoScripts.current
    val extras = remember(also, reader, showMine, script, hideTamil) {
        if (script == ScriptMode.KANNADA || hideTamil) emptyList()
        else ALSO_SCRIPTS.map { it.first }.filter { it in also && it != Script.KANNADA && !(it == reader && (showMine || reader == Script.KANNADA)) }
    }
    val extraWords = remember(verse, superscripts, extras) { extras.associateWith { sc -> verse.words.map { Scripts.render(it.text, sc, superscripts) } } }
    fun build(words: List<Pair<Int, String>>, base: Color, line: com.example.harikathamruthasara.data.Line): AnnotatedString = buildAnnotatedString {
        words.forEachIndexed { k, (g, w) ->
            // the script's marks: '-' between ganas, '--' = silent pause; drawn faint so the words lead
            if (k > 0) withStyle(SpanStyle(color = cs.outline)) { append(line.sepAfter(k - 1)) }
            val l = look(g)
            val style = SpanStyle(color = base, textDecoration = TextDecoration.None).merge(when (l) {
                WordLook.NORMAL -> SpanStyle()
                WordLook.IN_STEP -> SpanStyle(background = cs.tertiaryContainer, color = cs.onTertiaryContainer)
                WordLook.CURRENT -> SpanStyle(background = cs.primary, color = cs.onPrimary, fontWeight = FontWeight.Bold)
                WordLook.GOOD -> SpanStyle(background = HkColors.goodBg, color = HkColors.good, fontWeight = FontWeight.SemiBold)
                WordLook.BAD -> SpanStyle(background = HkColors.badBg, color = HkColors.bad, fontWeight = FontWeight.SemiBold)
                WordLook.SOUND -> SpanStyle(background = HkColors.soundBg, color = HkColors.sound, fontWeight = FontWeight.SemiBold)
                WordLook.NEXT -> SpanStyle(background = cs.tertiaryContainer, color = cs.onTertiaryContainer, fontWeight = FontWeight.Bold)
                WordLook.HIDDEN -> SpanStyle(color = cs.outline)
            })
            val text = if (l == WordLook.HIDDEN) "•".repeat((w.length / 2).coerceIn(2, 6)) else w
            if (onWordTap != null) {
                withLink(LinkAnnotation.Clickable("w$g", TextLinkStyles(style)) { onWordTap(g) }) { append(text) }
            } else withStyle(style) { append(text) }
        }
        // a word running on into the next line ends with the script's dash ("ಬ್ರಹ್ಮ ಸ-" | "ಮೀರ…")
        if (words.isNotEmpty()) line.sepAfter(line.words.lastIndex).takeIf { it.isNotEmpty() }?.let { withStyle(SpanStyle(color = cs.outline)) { append(it) } }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        verse.lines.forEachIndexed { li, line ->
            if (onlyLines != null && li !in onlyLines) return@forEachIndexed
            val refs = verse.words.filter { it.line == li }
            Column(Modifier.bringIntoViewRequester(requesters[li])) {
                if (showKn) {
                    Text(
                        build(refs.map { it.global to it.text }, cs.onSurface, line),
                        fontSize = (23 * fontScale).sp, lineHeight = (36 * fontScale).sp,
                        color = cs.onSurface,
                    )
                }
                if (showMine) {
                    Text(
                        build(refs.map { it.global to mine[it.global] }, if (mineOnly) cs.onSurface else cs.secondary, line),
                        fontSize = ((if (mineOnly) 22 else 17) * fontScale).sp,
                        lineHeight = ((if (mineOnly) 34 else 27) * fontScale).sp,
                        color = if (mineOnly) cs.onSurface else cs.secondary,
                    )
                }
                for (sc in extras) {
                    val ws = extraWords.getValue(sc)
                    Text(
                        build(refs.map { it.global to ws[it.global] }, cs.tertiary, line),
                        fontSize = (16 * fontScale).sp, lineHeight = (25 * fontScale).sp,
                        color = cs.tertiary,
                    )
                }
            }
            if (line.sentenceEnd && li != verse.lines.lastIndex) Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
fun Stars(n: Int, max: Int = 3, size: Int = 16) {
    Text("★".repeat(n) + "☆".repeat((max - n).coerceAtLeast(0)), color = HkColors.gold, fontSize = size.sp)
}

@Composable
fun Pill(text: String, bg: Color = MaterialTheme.colorScheme.secondaryContainer, fg: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * A home tile: a big emoji in a bright blob on its own festival colour, title and a hint. Squishes when pressed.
 */
@Composable
fun BigTile(emoji: String, title: String, subtitle: String, tone: Tone, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BouncyCard(onClick = onClick, modifier = modifier, color = tone.container, contentColor = tone.on, shape = MaterialTheme.shapes.large) {
        Box(Modifier.fillMaxWidth()) {
            // a soft decorative circle peeking in from the corner
            Canvas(Modifier.matchParentSize()) {
                drawCircle(tone.accent.copy(alpha = 0.18f), radius = size.minDimension * 0.55f, center = Offset(size.width * 1.02f, -size.height * 0.05f))
            }
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Box(Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tone.accent.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 30.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp)
                Text(subtitle, fontSize = 13.sp, lineHeight = 17.sp, color = tone.on.copy(alpha = 0.78f))
            }
        }
    }
}

/** A section heading with a little colour bar in front: cheerful but still a clear hierarchy. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, tone: Tone = tones.lotus) {
    Row(modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 6.dp, height = 22.dp).clip(RoundedCornerShape(50)).background(tone.accent))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------------- celebration ----------------

@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(2600, easing = LinearEasing)) }
    val parts = remember {
        val colors = listOf(Color(0xFFFFC93C), Color(0xFFFF6B2C), Color(0xFFE0457B), Color(0xFF0E9A97), Color(0xFF3D6FE0), Color(0xFF8752D6), Color(0xFF3E9C48))
        List(70) {
            Triple(Random.nextFloat(), Random.nextFloat() * 0.6f + 0.4f, colors[it % colors.size]) to Random.nextFloat() * 360f
        }
    }
    Canvas(modifier) {
        val t = anim.value
        parts.forEachIndexed { i, (p, rot) ->
            val (x0, speed, color) = p
            val angle = (i * 37 % 360) * Math.PI / 180
            val x = size.width * x0 + (sin(angle + t * 8) * 30).toFloat()
            val y = -40f + t * speed * size.height * 1.3f
            rotate(rot + t * 720f * speed, Offset(x, y)) {
                drawRect(color.copy(alpha = (1f - t).coerceIn(0f, 1f) + 0.2f), Offset(x, y), Size(14f, 8f + (cos(angle) * 4).toFloat()))
            }
        }
    }
}

@Composable
fun RewardDialog(reward: Reward, onDismiss: () -> Unit) {
    val S = strings()
    val (emoji, title, body) = when (reward) {
        is Reward.BadgeEarned -> Triple(reward.badge.emoji, S.newBadge, "${S.badgeTitle(reward.badge.id)}\n${S.badgeHint(reward.badge.id)}")
        is Reward.LevelUp -> Triple(reward.level.emoji, S.levelUp, S.nowLevel(S.levelNames[LEVELS.indexOf(reward.level)]))
        is Reward.GoalMet -> Triple("🎯", S.goalDone, S.goalDoneBody(reward.minutes) + "\n\n" + S.krishnarpanam + " 🙏")
    }
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 260f)) }
    val tone = tones.marigold
    Dialog(onDismissRequest = onDismiss) {
        Box(contentAlignment = Alignment.Center) {
            Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(28.dp).width(260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(120.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tone.container), contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 68.sp, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(body, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDismiss) { Text(S.ok + " 🙏") }
                }
            }
            Confetti(Modifier.matchParentSizeSafe())
        }
    }
}

private fun Modifier.matchParentSizeSafe() = this.fillMaxSize()

@Composable
fun ProgressRing(fraction: Float, label: String, sub: String, modifier: Modifier = Modifier, tone: Tone = tones.peacock) {
    val cs = MaterialTheme.colorScheme
    val ringTrack = tone.accent.copy(alpha = 0.18f)
    val ringA = tone.accent
    val ringB = tones.leaf.accent
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(ringTrack, -90f, 360f, false, style = stroke)
            drawArc(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(ringA, ringB, ringA)), -90f, 360f * fraction.coerceIn(0f, 1f), false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(sub, fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun Banner(text: String, action: String, onAction: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontSize = 14.sp)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

fun Modifier.softBg(color: Color) = this.clip(RoundedCornerShape(18.dp)).background(color)

/** Text in the reader's own script (Latin / Tamil / Devanagari / Telugu), or Kannada for Kannada readers. */
@Composable
fun mine(kn: String, superscripts: Boolean = true): String = Scripts.render(kn, strings().lang.script, superscripts)

fun Strs.stepLabel(st: Step): String = when (st.kind) {
    StepKind.WORD -> stepWord(st.n)
    StepKind.LINE -> stepLine(st.n)
    StepKind.PART -> stepPart(st.n)
    StepKind.WHOLE -> stepWhole
    StepKind.LINES -> stepLines(st.n)
}

fun Strs.verseLabel(v: Verse): String = if (v.isRefrain) refrain else padya(v.number)

fun Strs.levelName(l: com.example.harikathamruthasara.data.Level): String = levelNames[LEVELS.indexOf(l)]

/** The app logo (rounded artwork). */
@Composable
fun BrandLogo(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.example.harikathamruthasara.R.drawable.brand_logo),
        contentDescription = null,
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.16f)),
    )
}

/** An icon with its name under it, sized for an easy tap; the label stays readable in every language. */
@Composable
fun LabeledControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
            androidx.compose.material3.Icon(icon, null, Modifier.padding(14.dp).size(28.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
