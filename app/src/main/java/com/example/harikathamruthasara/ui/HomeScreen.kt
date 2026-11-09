package com.example.harikathamruthasara.ui

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.data.Sandhi
import com.example.harikathamruthasara.i18n.strings
import com.example.harikathamruthasara.data.today
import java.util.Calendar

@Composable
fun HomeScreen(app: HkApp, lib: List<Sandhi>, nav: Nav) {
    val p by app.progress.state.collectAsStateWithLifecycle()
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val S = strings()
    val greeting = when (hour) { in 4..11 -> S.goodMorning; in 12..16 -> S.namaskara; else -> S.goodEvening }

    val t = tones
    val wc = LocalWidthClass.current
    val tour = homeTour()
    AutoTour(tour)
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).readable(1100.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---- header (+ streak & today beside it on wide windows)
        val header: @Composable (Modifier) -> Unit = { m -> HomeHeader(app, p, settings.tamilSuperscripts, greeting, m, tour, onProfile = { nav.go(Screen.Account) }) { nav.go(Screen.Settings) } }
        val streak: @Composable (Modifier) -> Unit = { m ->
            Card(m.tourTarget(TT.HOME_STREAK), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = t.marigold.container, contentColor = t.marigold.on)) {
                Column(Modifier.padding(16.dp)) {
                    Text(plantFor(p.streak), fontSize = 40.sp)
                    Text(S.streakDays(p.streak), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(S.bestDays(p.bestStreak), fontSize = 12.sp, color = t.marigold.on.copy(alpha = 0.75f))
                    Text(S.streakTip(p.streak), fontSize = 12.sp, color = t.marigold.on.copy(alpha = 0.75f))
                }
            }
        }
        val today: @Composable (Modifier) -> Unit = { m ->
            Card(m.tourTarget(TT.HOME_TODAY), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = t.peacock.container, contentColor = t.peacock.on)) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val min = p.todaySeconds / 60
                    ProgressRing(min.toFloat() / settings.dailyGoalMinutes, "$min/${settings.dailyGoalMinutes}", S.minutes, Modifier.size(96.dp), t.peacock)
                    Text(S.todayRepeats(p.todayRepeats), fontSize = 12.sp, color = t.peacock.on.copy(alpha = 0.75f))
                }
            }
        }
        if (wc == WidthClass.EXPANDED) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                header(Modifier.weight(1.6f))
                streak(Modifier.weight(1f))
                today(Modifier.weight(1f))
            }
        } else {
            header(Modifier.fillMaxWidth())
        }

        // the chant (Vāgdhenu) for the sandhi she is on; hidden once downloaded
        UpdatesBanner(app)   // corrected text / better chant waiting on the server
        VagBanner(app, p.lastSandhi.coerceIn(1, lib.size))

        if (wc != WidthClass.EXPANDED) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                streak(Modifier.weight(1f).fillMaxHeight())
                today(Modifier.weight(1f).fillMaxHeight())
            }
        }

        // ---- continue
        val sandhi = lib.firstOrNull { it.id == p.lastSandhi } ?: lib.first()
        val verse = sandhi.verses.getOrNull(p.lastVerse) ?: sandhi.verses.first()
        BouncyCard(
            onClick = { app.player.open(sandhi, sandhi.verses.indexOf(verse)); nav.go(Screen.Learn) },
            modifier = Modifier.fillMaxWidth().tourTarget(TT.HOME_CONTINUE), shape = MaterialTheme.shapes.large,
            color = cs.primaryContainer, contentColor = cs.onPrimaryContainer,
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${S.continueWord} · ${sandhi.id}. ${mine(sandhi.title, settings.tamilSuperscripts)}", fontWeight = FontWeight.Bold)
                    Text("${S.verseLabel(verse)}: ${mine(verse.firstLine, settings.tamilSuperscripts)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (S.lang.script != com.example.harikathamruthasara.data.Script.KANNADA) Text(verse.firstLine, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onPrimaryContainer.copy(alpha = 0.75f), fontSize = 14.sp)
                }
                Spacer(Modifier.size(8.dp))
                FilledIconButton(onClick = {
                    app.player.open(sandhi, sandhi.verses.indexOf(verse)); app.player.play(); nav.go(Screen.Learn)
                }, Modifier.size(64.dp)) { Icon(Icons.Filled.PlayArrow, S.play, Modifier.size(36.dp)) }
            }
        }

        // ---- tiles: 2 columns on phones, 3 on foldables & tablets
        data class TileSpec(val emoji: String, val title: String, val sub: String, val tone: Tone, val go: () -> Unit)
        val tiles = listOf(
            TileSpec("🎧", S.listenLearn, S.listenLearnSub, t.lotus) { nav.top(Screen.Library()) },
            TileSpec("🎤", S.reciteCheck, S.reciteCheckSub, t.marigold) { nav.go(Screen.Library(forRecite = true)) },   // choose the sandhi, then the padya
            TileSpec("🎲", S.games, S.gamesSub, t.peacock) { nav.top(Screen.Quiz) },
            TileSpec("ಅ", S.readKannada, S.readKannadaSub, t.sky) { nav.top(Screen.Letters) },
            TileSpec("🏅", S.myProgress, S.progressSub(p.badges.size, p.mastered), t.leaf) { nav.top(Screen.Progress) },
            TileSpec("⚙️", S.settings, S.settingsSub, t.berry) { nav.go(Screen.Settings) },
        )
        TileGrid(tiles, tileColumns(2, 3, 3), Modifier.tourTarget(TT.HOME_TILES)) { tile, m -> BigTile(tile.emoji, tile.title, tile.sub, tile.tone, m.fillMaxHeight(), tile.go) }

        // ---- verse of the day
        val all = remember(lib) { lib.flatMap { s -> s.verses.filter { !it.isRefrain }.map { s to it } } }
        val (vs, vd) = all[(today() % all.size).toInt()]
        BouncyCard(
            onClick = { app.player.open(vs, vs.verses.indexOf(vd)); nav.go(Screen.Learn) },
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = cs.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(2.dp, t.lotus.container),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("🌸 ${S.verseOfDay} · ${mine(vs.title, settings.tamilSuperscripts)} ${vd.number}", fontWeight = FontWeight.Bold, color = cs.secondary)
                Spacer(Modifier.height(8.dp))
                vd.lines.take(3).forEach { l ->
                    Text(mine(l.text, settings.tamilSuperscripts), fontSize = 17.sp)
                    if (S.lang.script != com.example.harikathamruthasara.data.Script.KANNADA) Text(l.text, fontSize = 14.sp, color = cs.onSurfaceVariant)
                    // the extra reading scripts chosen in Settings (e.g. Devanagari)
                    for (sc in ALSO_SCRIPTS.map { it.first }.filter { it in settings.alsoScripts && it != S.lang.script && it != com.example.harikathamruthasara.data.Script.KANNADA })
                        Text(com.example.harikathamruthasara.data.Scripts.render(l.text, sc, settings.tamilSuperscripts), fontSize = 14.sp, color = cs.tertiary)
                }
                Text("…", color = cs.onSurfaceVariant)
            }
        }
        // credits & offering
        androidx.compose.material3.TextButton(onClick = { nav.go(Screen.Credits) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("📖 Tadipatri Gurukula · 🎶 Vāgdhenu  ·  " + S.credits, fontSize = 13.sp)
        }
        Offering(size = 18)
        Spacer(Modifier.height(8.dp))
    }
}

/** The bright brand header: logo, greeting, title, level and points, on a navy → violet → magenta gradient. */
/** The reader's picture (or first letter, or a plain figure for guests); opens sign-in and the account. */
@Composable
private fun ProfileButton(app: HkApp, onClick: () -> Unit) {
    val user by app.account.user.collectAsStateWithLifecycle()
    val person = user?.takeUnless { it.anonymous }
    val photo by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, person?.photo) {
        val url = person?.photo
        value = if (url == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                com.example.harikathamruthasara.data.openHttp(java.net.URL(url)).inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    androidx.compose.material3.IconButton(onClick = onClick) {
        val shot = photo
        val label = accountText().account
        when {
            shot != null -> androidx.compose.foundation.Image(shot, label, Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            person != null -> Box(Modifier.semantics { contentDescription = label }) { Avatar(person.label, 40) }
            else -> Icon(Icons.Rounded.AccountCircle, label, Modifier.size(40.dp), tint = Color.White)
        }
    }
}

@Composable
private fun HomeHeader(app: HkApp, p: com.example.harikathamruthasara.data.ProgressSnapshot, sup: Boolean, greeting: String, modifier: Modifier, tour: Tour, onProfile: () -> Unit, onSettings: () -> Unit) {
    val S = strings()
    val lvl = p.level
    val next = p.nextLevel
    val onBrand = Color.White
    Box(modifier.clip(MaterialTheme.shapes.extraLarge).background(Brush.linearGradient(HkColors.header))) {
        // playful bubbles
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            drawCircle(HkColors.brandGold.copy(alpha = 0.16f), radius = size.height * 0.55f, center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.07f), radius = size.height * 0.35f, center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 1.05f))
            drawCircle(Color(0xFFFF8AB3).copy(alpha = 0.18f), radius = size.height * 0.12f, center = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.88f))
        }
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(72.dp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("$greeting 🙏", color = onBrand.copy(alpha = 0.9f), fontSize = 15.sp)
                    // the app's name (same as under the icon, user's choice 2026-11-08), the text's Kannada name below
                    Text(androidx.compose.ui.res.stringResource(com.example.harikathamruthasara.R.string.app_name).replace("thaSaa", "tha\u200BSaa"), color = HkColors.brandGold, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
                    Text("ಹರಿಕಥಾಮೃತಸಾರ", color = onBrand.copy(alpha = 0.85f), fontSize = 15.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProfileButton(app, onProfile)
                androidx.compose.material3.IconButton(onClick = onSettings, modifier = Modifier.tourTarget(TT.HOME_SETTINGS)) {
                    Icon(Icons.Rounded.Settings, S.settings, tint = onBrand)
                }
                TourHelpButton(tour, tint = onBrand)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(onBrand.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text(lvl.emoji, fontSize = 32.sp)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(S.levelName(lvl), color = onBrand, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(S.leaves(p.points), color = onBrand, fontSize = 14.sp)
                    if (next != null) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (p.points - lvl.minPoints).toFloat() / (next.minPoints - lvl.minPoints) },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = HkColors.brandGold, trackColor = onBrand.copy(alpha = 0.22f), drawStopIndicator = {},
                        )
                        Text(S.moreFor(next.minPoints - p.points, next.emoji + " " + S.levelName(next)), color = onBrand.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun plantFor(streak: Int) = when {
    streak >= 30 -> "🌳"
    streak >= 14 -> "🪴"
    streak >= 7 -> "🌿"
    streak >= 3 -> "☘️"
    streak >= 1 -> "🌱"
    else -> "💧"
}

