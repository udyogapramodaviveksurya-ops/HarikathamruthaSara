package com.example.harikathamruthasara.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.harikathamruthasara.i18n.TourText
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ============================================================================ the model
/**
 * One step of a guided tour. [target] = the id given to [Modifier.tourTarget] on the control to highlight (null: a
 * centred card with no spotlight). [tap] = the reader must tap the highlighted control to go on (it really does its
 * job: plays, opens the chooser…), so the tour teaches by doing.
 */
class TourStep(val target: String?, val title: String, val body: String, val tap: Boolean = false)

/** A first-run guide for one screen. [id] is remembered once finished or skipped. */
class Tour(val id: String, val steps: List<TourStep>)

/** Runs tours: knows where every target is on screen, which tour is showing, and which were already seen. */
class TourController(context: Context) {
    private val prefs = context.getSharedPreferences("tours", Context.MODE_PRIVATE)
    internal val bounds = mutableStateMapOf<String, Rect>()
    internal val requesters = HashMap<String, BringIntoViewRequester>()
    var active by mutableStateOf<Tour?>(null); private set
    var index by mutableIntStateOf(0); private set

    val step: TourStep? get() = active?.steps?.getOrNull(index)

    fun seen(id: String) = prefs.getBoolean(id, false)
    fun start(t: Tour) { if (t.steps.isNotEmpty()) { index = 0; active = t } }
    fun next() { val t = active ?: return; if (index < t.steps.lastIndex) index++ else finish() }
    fun prev() { if (index > 0) index-- }
    fun finish() { active?.let { prefs.edit().putBoolean(it.id, true).apply() }; active = null }
    /** Every screen's guide will show again (Settings). */
    fun resetAll() { prefs.edit().clear().apply() }

    internal fun tapped(id: String) { if (step?.let { it.tap && it.target == id } == true) next() }
}

val LocalTour = staticCompositionLocalOf<TourController?> { null }

/**
 * Marks a control as a place the tour can point at. It only watches taps (never consumes them), so the control
 * works exactly as usual; a tap on it moves a "tap here" step on.
 */
fun Modifier.tourTarget(id: String): Modifier = composed {
    val tour = LocalTour.current ?: return@composed this
    val req = remember { BringIntoViewRequester() }
    DisposableEffect(id) {
        tour.requesters[id] = req
        onDispose { tour.requesters.remove(id); tour.bounds.remove(id) }
    }
    this
        .bringIntoViewRequester(req)
        .onGloballyPositioned { tour.bounds[id] = it.boundsInRoot() }
        .pointerInput(id) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (waitForUpOrCancellation(PointerEventPass.Initial) != null) tour.tapped(id)
            }
        }
}

/** Starts [tour] the first time its screen is opened (after the screen has settled). */
@Composable
fun AutoTour(tour: Tour) {
    val c = LocalTour.current ?: return
    LaunchedEffect(tour.id) {
        delay(700)
        if (!c.seen(tour.id) && c.active == null) c.start(tour)
    }
}

/** A "?" button for an app bar: replays this screen's guide any time. */
@Composable
fun TourHelpButton(tour: Tour, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val c = LocalTour.current ?: return
    IconButton(onClick = { c.start(tour) }) { Icon(Icons.AutoMirrored.Rounded.HelpOutline, tourText().showGuide, tint = tint) }
}

// ============================================================================ the overlay
/**
 * Dims the screen except a rounded "spotlight" around the current target, with a gently pulsing ring, and shows the
 * step card beside it. Touches outside the spotlight are blocked; inside it they reach the real control.
 */
@Composable
fun TourOverlay(c: TourController) {
    val tour = c.active ?: return
    val step = c.step ?: return
    val T = tourText()
    val density = LocalDensity.current
    val accent = tones.marigold.accent

    BackHandler { if (c.index > 0) c.prev() else c.finish() }
    // scroll the target into view, then let the layout settle
    LaunchedEffect(tour.id, c.index) { step.target?.let { c.requesters[it]?.bringIntoView() } }

    val pad = with(density) { 8.dp.toPx() }
    val raw = step.target?.let { c.bounds[it] }
    val hole = raw?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }

    val pulse by rememberInfiniteTransition(label = "tour").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse",
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        // ---- the dim layer with a hole (drawn only; it does not take touches)
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.62f))
            if (hole != null) {
                val r = CornerRadius(20.dp.toPx())
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, r, blendMode = BlendMode.Clear)
                val grow = 4.dp.toPx() + pulse * 6.dp.toPx()
                drawRoundRect(
                    accent.copy(alpha = 0.9f - pulse * 0.5f),
                    Offset(hole.left - grow, hole.top - grow), Size(hole.width + 2 * grow, hole.height + 2 * grow),
                    CornerRadius(r.x + grow), style = Stroke(3.dp.toPx()),
                )
            }
        }
        // ---- touch blockers around the hole (or over everything when there is no target)
        if (hole == null) Blocker(0f, 0f, wPx, hPx)
        else {
            Blocker(0f, 0f, wPx, hole.top)                          // above
            Blocker(0f, hole.bottom, wPx, hPx - hole.bottom)        // below
            Blocker(0f, hole.top, hole.left, hole.height)           // left
            Blocker(hole.right, hole.top, wPx - hole.right, hole.height)   // right
        }

        // ---- the card: on the roomier side of the target
        val minRoom = with(density) { 230.dp.toPx() }
        val below = hole == null || (hPx - hole.bottom >= hole.top) || hole.top < minRoom && hPx - hole.bottom >= minRoom
        val gap = with(density) { 16.dp.toPx() }
        val cardMod = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp).widthIn(max = 460.dp).fillMaxWidth()
        val placed = when {
            hole == null -> Modifier.align(Alignment.Center)
            below && hPx - hole.bottom >= minRoom -> Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (hole.bottom + gap).roundToInt()) }
            !below && hole.top >= minRoom -> Modifier.align(Alignment.BottomCenter).offset { IntOffset(0, -(hPx - hole.top + gap).roundToInt()) }
            // a very tall target: put the card over the screen's far edge
            hole.center.y > hPx / 2 -> Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            else -> Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        }
        Box(placed.then(cardMod)) {
            AnimatedContent(c.index, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tourCard") { i ->
                val st = tour.steps.getOrNull(i) ?: return@AnimatedContent
                TourCard(st, i, tour.steps.size, T, onPrev = c::prev, onNext = c::next, onSkip = c::finish)
            }
        }
    }
}

@Composable
private fun Blocker(x: Float, y: Float, w: Float, h: Float) {
    if (w <= 0f || h <= 0f) return
    val d = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(d) { w.toDp() }, with(d) { h.toDp() })
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
                }
            },
    )
}

@Composable
private fun TourCard(st: TourStep, i: Int, n: Int, T: TourText, onPrev: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val t = tones
    Surface(shape = MaterialTheme.shapes.large, color = cs.surfaceContainerHigh, shadowElevation = 12.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // step counter + progress dots
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = t.marigold.container, contentColor = t.marigold.on) {
                    Text(T.stepOf(i + 1, n), Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                repeat(n) { k ->
                    Box(Modifier.padding(start = 4.dp).size(if (k == i) 18.dp else 7.dp, 7.dp).clip(CircleShape)
                        .background(if (k <= i) t.marigold.accent else cs.outlineVariant))
                }
            }
            Text(st.title, style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
            Text(st.body, fontSize = 16.sp, lineHeight = 23.sp, color = cs.onSurfaceVariant)
            if (st.tap) {
                Surface(shape = MaterialTheme.shapes.small, color = t.leaf.container, contentColor = t.leaf.on) {
                    Text(T.tapHint, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSkip) { Text(T.skip) }
                Spacer(Modifier.weight(1f))
                if (i > 0) { OutlinedButton(onClick = onPrev) { Text("‹ " + T.prev) }; Spacer(Modifier.width(8.dp)) }
                Button(onClick = onNext) { Text(if (i == n - 1) T.done else T.next + " ›") }
            }
        }
    }
}

/** Installs the controller and draws the overlay above [content]. */
@Composable
fun TourHost(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val c = remember { TourController(ctx.applicationContext) }
    androidx.compose.runtime.CompositionLocalProvider(LocalTour provides c) {
        Box(Modifier.fillMaxSize()) {
            content()
            TourOverlay(c)
        }
    }
}
