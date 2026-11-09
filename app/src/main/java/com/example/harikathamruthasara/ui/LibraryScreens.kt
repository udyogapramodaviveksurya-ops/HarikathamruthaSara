package com.example.harikathamruthasara.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.data.Sandhi
import com.example.harikathamruthasara.i18n.strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, nav: Nav, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    // top-level screens (reached from the navigation bar/rail) have no back arrow; detail screens do
    val top = LocalIsTopLevel.current
    TopAppBar(
        title = {
            Column {
                Text(short(title), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            if (!top) androidx.compose.material3.FilledTonalIconButton(onClick = { nav.back() }, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, strings().back)
            }
        },
        actions = { actions() },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun LibraryScreen(app: HkApp, lib: List<Sandhi>, nav: Nav, forRecite: Boolean = false) {
    val p by app.progress.state.collectAsStateWithLifecycle()
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val S = strings()
    val tour = libraryTour()
    AutoTour(tour)
    Scaffold(topBar = {
        if (forRecite) BackBar(S.reciteCheck, nav, S.chooseSandhi) { TourHelpButton(tour) }
        else BackBar(S.sandhis, nav, mine("ಹರಿಕಥಾಮೃತಸಾರ", settings.tamilSuperscripts) + " · " + S.padyaCount(lib.sumOf { it.padyaCount })) { TourHelpButton(tour) }
    }) { pad ->
        val t = tones
        LazyVerticalGrid(GridCells.Adaptive(340.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (forRecite) {
                // shortcut: recite where she left off
                val ls = lib.firstOrNull { it.id == p.lastSandhi }
                val lv = ls?.verses?.getOrNull(p.lastVerse)
                if (ls != null && lv != null) item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                    BouncyCard(onClick = { nav.go(Screen.Recite(ls.id, p.lastVerse)) }, modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Mic, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(S.continueWord, fontWeight = FontWeight.Bold)
                                Text("${ls.id}. ${mine(ls.title, settings.tamilSuperscripts)} · ${S.verseLabel(lv)}", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            items(lib, key = { it.id }) { s ->
                val padyas = s.verses.filter { !it.isRefrain }
                val stars = padyas.sumOf { p.stat(it.key).stars }
                val tone = t[s.id - 1]
                val current = s.id == p.lastSandhi
                BouncyCard(onClick = { nav.go(Screen.Verses(s.id, forRecite)) }, modifier = Modifier.fillMaxWidth().let { if (s.id == lib.first().id) it.tourTarget(TT.LIB_FIRST) else it },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (current) androidx.compose.foundation.BorderStroke(2.dp, tone.accent) else null) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = MaterialTheme.shapes.small, color = if (current) tone.accent else tone.container, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (s.id == 33) "🙏" else "${s.id}", fontWeight = FontWeight.Black, fontSize = 18.sp,
                                    color = if (current) androidx.compose.ui.graphics.Color.White else tone.on)
                            }
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mine(s.title, settings.tamilSuperscripts), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (S.lang.script != com.example.harikathamruthasara.data.Script.KANNADA) Text(s.title, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                            if (s.alt.isNotBlank()) Text(mine(s.alt, settings.tamilSuperscripts), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { stars / (padyas.size * 3f) }, Modifier.fillMaxWidth().height(6.dp),
                                color = tone.accent, trackColor = tone.container, drawStopIndicator = {})
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${padyas.size}", fontWeight = FontWeight.Bold)
                            Text(S.padyasWord, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("★ $stars", color = HkColors.gold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VersesScreen(app: HkApp, sandhi: Sandhi, nav: Nav, forRecite: Boolean = false) {
    val p by app.progress.state.collectAsStateWithLifecycle()
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val S = strings()
    val tour = versesTour()
    if (!forRecite) AutoTour(tour)
    Scaffold(
        topBar = {
            BackBar("${sandhi.id}. ${mine(sandhi.title, settings.tamilSuperscripts)}", nav,
                if (forRecite) S.reciteCheck + " · " + S.choosePadya else if (S.lang.script != com.example.harikathamruthasara.data.Script.KANNADA) sandhi.title else null) {
                if (!forRecite) { TourHelpButton(tour); IconButton(onClick = { nav.go(Screen.Recite(sandhi.id, 0)) }) { Icon(Icons.Filled.Mic, S.reciteCheck) } }
            }
        },
        floatingActionButton = {
            if (!forRecite) ExtendedFloatingActionButton(
                onClick = { app.player.open(sandhi, 0); app.player.play(); nav.go(Screen.Learn) },
                modifier = Modifier.tourTarget(TT.VERSES_ALL),
                icon = { Icon(Icons.Filled.PlayArrow, null) },
                text = { Text(S.playWholeSandhi) },
            )
        },
    ) { pad ->
        val tone = tones[sandhi.id - 1]
        LazyVerticalGrid(GridCells.Adaptive(360.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(sandhi.verses, key = { _, v -> v.key }) { idx, v ->
                val st = p.stat(v.key)
                val pick = { if (forRecite) nav.go(Screen.Recite(sandhi.id, idx)) else { app.player.open(sandhi, idx); nav.go(Screen.Learn) } }
                BouncyCard(onClick = { pick() }, modifier = Modifier.fillMaxWidth().let { if (idx == 0) it.tourTarget(TT.VERSES_FIRST) else it }, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pill(S.verseLabel(v), tone.container, tone.on)
                                Spacer(Modifier.size(8.dp))
                                Stars(st.stars)
                                if (st.listens > 0) Text("  🎧${st.listens}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (st.bestScore > 0) Text("  🎤${st.bestScore}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(mine(v.firstLine, settings.tamilSuperscripts), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (S.lang.script != com.example.harikathamruthasara.data.Script.KANNADA) Text(v.firstLine, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.size(8.dp))
                        androidx.compose.material3.FilledTonalIconButton(
                            onClick = { if (forRecite) nav.go(Screen.Recite(sandhi.id, idx)) else { app.player.open(sandhi, idx); app.player.play(); nav.go(Screen.Learn) } },
                            modifier = Modifier.size(48.dp),
                        ) { if (forRecite) Icon(Icons.Filled.Mic, S.reciteCheck) else Icon(Icons.Filled.PlayArrow, S.play) }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Offering(Modifier.padding(top = 12.dp), size = 18) }
        }
    }
}
