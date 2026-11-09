package com.example.harikathamruthasara.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.harikathamruthasara.i18n.strings

// ---------------------------------------------------------------- window size classes
/** Material 3 window width classes: phone (< 600 dp), foldable / small tablet (< 840 dp), tablet & desktop. */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

val LocalWidthClass = staticCompositionLocalOf { WidthClass.COMPACT }

/** How many columns a grid of tiles gets in the current window. */
@Composable
fun tileColumns(compact: Int = 2, medium: Int = 3, expanded: Int = 4): Int = when (LocalWidthClass.current) {
    WidthClass.COMPACT -> compact
    WidthClass.MEDIUM -> medium
    WidthClass.EXPANDED -> expanded
}

/** Measures the window and provides [LocalWidthClass] to everything inside. */
@Composable
fun ProvideWidthClass(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wc = when {
            maxWidth < 600.dp -> WidthClass.COMPACT
            maxWidth < 840.dp -> WidthClass.MEDIUM
            else -> WidthClass.EXPANDED
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalWidthClass provides wc) { content() }
    }
}

/**
 * Keeps text at a comfortable line length on tablets / landscape: full width on phones, centred and at most [max]
 * wide on bigger windows.
 */
fun Modifier.readable(max: Dp = 720.dp): Modifier =
    this.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max).fillMaxWidth()

/** A simple grid for a known, small number of items inside a scrolling column (no nested lazy lists). */
@Composable
fun <T> TileGrid(items: List<T>, columns: Int, modifier: Modifier = Modifier, gap: Dp = 12.dp, cell: @Composable (T, Modifier) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------- playful touch
/**
 * A card that squishes a little when pressed and springs back — a friendly, tactile feel. Ripple, focus, semantics
 * and the 48 dp touch target all come from Material's [Card].
 */
@Composable
fun BouncyCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = MaterialTheme.shapes.medium,
    enabled: Boolean = true,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "bounce",
    )
    Card(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = color, contentColor = contentColor,
            disabledContainerColor = color, disabledContentColor = contentColor),
        border = border,
        interactionSource = src,
        content = content,
    )
}

// ---------------------------------------------------------------- top-level navigation
/** The five top-level destinations, shown in the navigation bar (phones) or rail (tablets, landscape). */
enum class TopDest { HOME, LISTEN, GAMES, LETTERS, PROGRESS }

fun TopDest.screen(): Screen = when (this) {
    TopDest.HOME -> Screen.Home
    TopDest.LISTEN -> Screen.Library()
    TopDest.GAMES -> Screen.Quiz
    TopDest.LETTERS -> Screen.Letters
    TopDest.PROGRESS -> Screen.Progress
}

/** The destination a screen belongs to, if it is a top-level screen (detail screens get a back arrow instead). */
fun Screen.topDest(): TopDest? = when (this) {
    Screen.Home -> TopDest.HOME
    is Screen.Library -> if (forRecite) null else TopDest.LISTEN
    Screen.Quiz -> TopDest.GAMES
    Screen.Letters -> TopDest.LETTERS
    Screen.Progress -> TopDest.PROGRESS
    else -> null
}

/** True on the top-level screens: their app bar shows no back arrow (Material navigation guidance). */
val LocalIsTopLevel = staticCompositionLocalOf { false }

@Composable
private fun DestIcon(d: TopDest, tint: Color) {
    when (d) {
        TopDest.HOME -> Icon(Icons.Rounded.Home, null, tint = tint)
        TopDest.LISTEN -> Icon(Icons.Rounded.Headphones, null, tint = tint)
        TopDest.GAMES -> Icon(Icons.Rounded.Casino, null, tint = tint)
        // the Kannada letter itself: the clearest icon for "learn to read Kannada"
        TopDest.LETTERS -> Text("ಅ", color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        TopDest.PROGRESS -> Icon(Icons.Rounded.EmojiEvents, null, tint = tint)
    }
}

@Composable
private fun destLabel(d: TopDest): String {
    val S = strings()
    return when (d) {
        TopDest.HOME -> S.navHome
        TopDest.LISTEN -> S.navListen
        TopDest.GAMES -> S.navGames
        TopDest.LETTERS -> S.navLetters
        TopDest.PROGRESS -> S.navProgress
    }
}

/** Each destination wears its own festival colour when selected. */
@Composable
private fun destTone(d: TopDest): Tone = when (d) {
    TopDest.HOME -> tones.marigold
    TopDest.LISTEN -> tones.lotus
    TopDest.GAMES -> tones.peacock
    TopDest.LETTERS -> tones.sky
    TopDest.PROGRESS -> tones.leaf
}

/**
 * The app frame. Phones: content above a bottom navigation bar. Medium/expanded windows: a navigation rail on the
 * start edge. The bar/rail only appears on top-level screens, so reading and reciting get the whole screen.
 */
@Composable
fun AdaptiveFrame(current: TopDest?, onPick: (TopDest) -> Unit, content: @Composable () -> Unit) {
    val wide = LocalWidthClass.current != WidthClass.COMPACT
    val show = current != null
    val cs = MaterialTheme.colorScheme
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            AnimatedVisibility(show, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                NavigationRail(Modifier.fillMaxHeight().tourTarget(TT.NAV), containerColor = cs.surfaceContainer) {
                    Spacer(Modifier.height(12.dp))
                    BrandLogo(48.dp)
                    Spacer(Modifier.height(20.dp))
                    TopDest.entries.forEach { d ->
                        val t = destTone(d)
                        val sel = d == current
                        NavigationRailItem(
                            selected = sel, onClick = { onPick(d) },
                            icon = { DestIcon(d, if (sel) t.on else cs.onSurfaceVariant) },
                            label = { Text(destLabel(d), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationRailItemDefaults.colors(indicatorColor = t.container, selectedTextColor = cs.onSurface),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().let {
                if (show) it.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else it
            }) { content() }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().let {
                if (show) it.consumeWindowInsets(WindowInsets.navigationBars) else it
            }) { content() }
            AnimatedVisibility(show, enter = expandVertically(expandFrom = Alignment.Top), exit = shrinkVertically(shrinkTowards = Alignment.Top)) {
                NavigationBar(Modifier.tourTarget(TT.NAV), containerColor = cs.surfaceContainer) {
                    TopDest.entries.forEach { d ->
                        val t = destTone(d)
                        val sel = d == current
                        NavigationBarItem(
                            selected = sel, onClick = { onPick(d) },
                            icon = { DestIcon(d, if (sel) t.on else cs.onSurfaceVariant) },
                            label = { Text(destLabel(d), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = t.container, selectedTextColor = cs.onSurface),
                        )
                    }
                }
            }
        }
    }
}
