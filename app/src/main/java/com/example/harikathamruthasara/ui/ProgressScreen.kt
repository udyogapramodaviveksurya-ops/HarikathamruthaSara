package com.example.harikathamruthasara.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.harikathamruthasara.HkApp
import com.example.harikathamruthasara.data.BADGES
import com.example.harikathamruthasara.data.LEVELS
import com.example.harikathamruthasara.data.today
import com.example.harikathamruthasara.i18n.strings

@Composable
fun ProgressScreen(app: HkApp, nav: Nav) {
    val p by app.progress.state.collectAsStateWithLifecycle()
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val S = strings()
    val tour = progressTour()
    AutoTour(tour)
    Scaffold(topBar = { BackBar(S.progressTitle, nav) { TourHelpButton(tour) } }) { pad ->
        val t = tones
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).readable(960.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val stats = listOf(
                Triple("🌿", "${p.points}", S.statLeaves), Triple("🔥", "${p.streak}", S.statStreak), Triple("⭐", "${p.mastered}", S.statMastered),
                Triple("🏆", "${p.bestStreak}", S.statBest), Triple("🎧", "${p.verses.values.sumOf { it.listens }}", S.statHeard),
                Triple("🎤", "${p.verses.values.sumOf { it.recites }}", S.statRecited),
            )
            TileGrid(stats.withIndex().toList(), tileColumns(3, 6, 6), Modifier.tourTarget(TT.PROG_STATS), gap = 10.dp) { (i, st), m -> Stat(st.first, st.second, st.third, t[i + 3], m.fillMaxHeight()) }

            SectionTitle(S.last14, tone = t.leaf)
            val d0 = today()
            val days = (13 downTo 0).map { (p.history[d0 - it] ?: 0) / 60 }
            val goal = settings.dailyGoalMinutes
            val maxV = maxOf(goal, days.maxOrNull() ?: 0, 1)
            val leafA = t.leaf.accent; val peacockA = t.peacock.accent
            Card(Modifier.tourTarget(TT.PROG_CHART), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
                Column(Modifier.padding(14.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                        val w = size.width / days.size
                        val gy = size.height * (1 - goal.toFloat() / maxV)
                        drawLine(cs.outline, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2f)
                        days.forEachIndexed { i, v ->
                            val h = size.height * v / maxV
                            drawRoundRect(
                                if (v >= goal) leafA else peacockA.copy(alpha = 0.55f),
                                Offset(i * w + w * 0.18f, size.height - h), Size(w * 0.64f, h.coerceAtLeast(3f)), CornerRadius(6f, 6f),
                            )
                        }
                    }
                    Text(S.goalLine(goal), fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }

            SectionTitle(S.badgesCount(p.badges.size, BADGES.size), tone = t.marigold)
            TileGrid(BADGES.withIndex().toList(), tileColumns(3, 4, 6), Modifier.tourTarget(TT.PROG_BADGES), gap = 10.dp) { (i, b), m ->
                        val got = b.id in p.badges
                        Card(m.fillMaxHeight().alpha(if (got) 1f else 0.5f), shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = if (got) t[i].container else cs.surfaceVariant, contentColor = if (got) t[i].on else cs.onSurfaceVariant)) {
                            Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (got) b.emoji else "🔒", fontSize = 30.sp)
                                Text(S.badgeTitle(b.id), fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
                                Text(S.badgeHint(b.id), fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
                            }
                        }
            }

            SectionTitle(S.levels, tone = t.berry)
            LEVELS.forEach { l ->
                val reached = p.points >= l.minPoints
                Row(Modifier.alpha(if (reached) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
                    Text(l.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(S.levelName(l), Modifier.weight(1f), fontWeight = if (l == p.level) FontWeight.Bold else FontWeight.Normal)
                    Text("${l.minPoints} 🌿", color = cs.onSurfaceVariant)
                }
            }
            SectionTitle(S.howToEarn, tone = t.sky)
            Text(S.howToEarnBody, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Stat(emoji: String, value: String, label: String, tone: Tone, modifier: Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.on)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(short(label), fontSize = 12.sp, color = tone.on.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
    }
}
