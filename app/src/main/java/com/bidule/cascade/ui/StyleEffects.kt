package com.bidule.cascade.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Shapes
// ─────────────────────────────────────────────────────────────────────────────

/** An irregular round blot, like ink soaking into paper. [seed] changes its outline. */
fun blobPath(center: Offset, radius: Float, seed: Float, roughness: Float = 1f): Path {
    val path = Path()
    val n = 56
    for (i in 0..n) {
        val a = (i.toFloat() / n) * 2f * PI.toFloat()
        val wobble = 1f + roughness * (
            0.10f * sin(3 * a + seed) +
                0.06f * sin(5 * a + seed * 1.7f) +
                0.035f * sin(11 * a + seed * 2.3f)
            )
        val r = radius * wobble
        val x = center.x + r * cos(a)
        val y = center.y + r * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** A horizontal brushstroke across a w×h box, drawn up to [progress] (0–1) of the width. */
fun brushPath(w: Float, h: Float, progress: Float, seed: Float): Path {
    val path = Path()
    val end = w * progress.coerceIn(0f, 1f)
    if (end < 2f) return path
    val steps = max(4, (end / 6f).toInt())
    val tops = ArrayList<Offset>(steps + 1)
    val bottoms = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val x = end * i / steps
        // Thin at the start, fat in the middle, tapering to the brush tip at the leading edge.
        val startTaper = min(1f, x / (h * 0.6f))
        val tipTaper = min(1f, (end - x) / (h * 0.9f))
        val body = 0.45f + 0.55f * min(startTaper, tipTaper)
        val mid = h / 2f + sin(x * 0.012f + seed) * h * 0.05f
        val half = h * 0.36f * body
        tops += Offset(x, mid - half * (1f + 0.07f * sin(x * 0.05f + seed * 3f)))
        bottoms += Offset(x, mid + half * (1f + 0.07f * sin(x * 0.043f + seed * 5f)))
    }
    path.moveTo(tops.first().x, tops.first().y)
    tops.forEach { path.lineTo(it.x, it.y) }
    bottoms.asReversed().forEach { path.lineTo(it.x, it.y) }
    path.close()
    return path
}

/** A water droplet pointing up, centered in a box of [size]. */
private fun dropPath(c: Offset, size: Float): Path {
    val r = size * 0.32f
    val cy = c.y + size * 0.12f
    return Path().apply {
        moveTo(c.x, cy - size * 0.48f)
        cubicTo(c.x + r * 0.25f, cy - size * 0.30f, c.x + r, cy - r * 0.55f, c.x + r, cy)
        cubicTo(c.x + r, cy + r * 0.56f, c.x + r * 0.56f, cy + r, c.x, cy + r)
        cubicTo(c.x - r * 0.56f, cy + r, c.x - r, cy + r * 0.56f, c.x - r, cy)
        cubicTo(c.x - r, cy - r * 0.55f, c.x - r * 0.25f, cy - size * 0.30f, c.x, cy - size * 0.48f)
        close()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Backdrops
// ─────────────────────────────────────────────────────────────────────────────

/** The living layer behind the home screen: drifting light for Water, paper grain for Ink. */
@Composable
fun StyleBackdrop(modifier: Modifier = Modifier) {
    val look = LocalLook.current
    when (look.kind) {
        CascadeStyle.Classic -> Unit
        CascadeStyle.Water -> WaterBackdrop(look, modifier)
        CascadeStyle.Ink -> InkBackdrop(look, modifier)
    }
}

@Composable
private fun WaterBackdrop(look: Look, modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "water").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val strength = when (look.mode) {
        ThemeMode.Wallpaper -> 0.10f
        ThemeMode.Dark -> 0.16f
        ThemeMode.Light -> 0.18f
    }
    Canvas(modifier.fillMaxSize()) {
        val a = t * 2f * PI.toFloat()
        val blobs = listOf(
            Triple(Offset(size.width * (0.25f + 0.15f * sin(a)), size.height * (0.30f + 0.10f * cos(a))), 0.75f, 1f),
            Triple(Offset(size.width * (0.80f + 0.12f * cos(a + 1.3f)), size.height * (0.62f + 0.12f * sin(a + 1.3f))), 0.65f, 0.8f),
            Triple(Offset(size.width * (0.45f + 0.20f * sin(2 * a + 2.1f)), size.height * (0.92f + 0.05f * cos(a))), 0.85f, 0.7f),
        )
        blobs.forEach { (c, r, k) ->
            val radius = size.width * r
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(look.effect.copy(alpha = strength * k), Color.Transparent),
                    center = c,
                    radius = radius,
                ),
                radius = radius,
                center = c,
            )
        }
        // Faint caustic lines, like light on the floor of a pool.
        val lineColor = look.effect.copy(alpha = strength * 0.35f)
        for (k in 0 until 4) {
            val y0 = size.height * (0.15f + 0.22f * k)
            val path = Path()
            var x = 0f
            path.moveTo(0f, y0)
            while (x <= size.width) {
                val y = y0 + sin(x * 0.012f + a * (1 + k * 0.3f) + k) * 14.dp.toPx() +
                    sin(x * 0.031f - a * 2f) * 5.dp.toPx()
                path.lineTo(x, y)
                x += 12f
            }
            drawPath(path, lineColor, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun InkBackdrop(look: Look, modifier: Modifier) {
    val darkSpecks = look.mode != ThemeMode.Dark
    val grain = remember(darkSpecks) { paperGrain(darkSpecks) }
    val brush = remember(grain) { ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)) }
    val grainAlpha = when (look.mode) {
        ThemeMode.Light -> 0.9f
        ThemeMode.Dark -> 0.6f
        ThemeMode.Wallpaper -> 0.45f
    }
    val ink = if (look.mode == ThemeMode.Dark) Color.Black else Color(0xFF1A1714)
    Canvas(modifier.fillMaxSize()) {
        // A soft ink wash on wallpapers keeps the text readable.
        if (look.mode == ThemeMode.Wallpaper) drawRect(Color(0xFF1A1714).copy(alpha = 0.22f))
        drawRect(brush, alpha = grainAlpha)
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, ink.copy(alpha = if (look.isLight) 0.10f else 0.35f)),
                center = Offset(size.width / 2f, size.height * 0.45f),
                radius = max(size.width, size.height) * 0.78f,
            )
        )
    }
}

/** Random speckles and fibers, tiled to look like rice paper. */
private fun paperGrain(dark: Boolean): androidx.compose.ui.graphics.ImageBitmap {
    val w = 160
    val h = 160
    val rnd = Random(11)
    val px = IntArray(w * h)
    val rgb = if (dark) 0x2A2218 else 0xF5EFE2
    for (i in px.indices) {
        val r = rnd.nextFloat()
        val alpha = when {
            r > 0.995f -> 70
            r > 0.97f -> 28
            else -> (rnd.nextFloat() * 14).toInt()
        }
        px[i] = (alpha shl 24) or rgb
    }
    // A few long fibers.
    repeat(18) {
        var x = rnd.nextInt(w).toFloat()
        var y = rnd.nextInt(h).toFloat()
        val ang = rnd.nextFloat() * 2f * PI.toFloat()
        repeat(14 + rnd.nextInt(18)) {
            val xi = ((x.toInt() % w) + w) % w
            val yi = ((y.toInt() % h) + h) % h
            px[yi * w + xi] = (40 shl 24) or rgb
            x += cos(ang)
            y += sin(ang)
        }
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

// ─────────────────────────────────────────────────────────────────────────────
// Touch marks: a ripple (Water) or an ink drop (Ink) wherever you touch the home screen
// ─────────────────────────────────────────────────────────────────────────────

class TouchMark(val center: Offset, val seed: Float) {
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f)
}

@Stable
class TouchMarks(private val scope: CoroutineScope) {
    val marks = mutableStateListOf<TouchMark>()

    fun add(at: Offset, kind: CascadeStyle) {
        if (kind == CascadeStyle.Classic) return
        if (marks.size > 6) marks.removeAt(0)
        val m = TouchMark(at, Random.nextFloat() * 10f)
        marks += m
        scope.launch {
            m.progress.animateTo(
                1f,
                tween(if (kind == CascadeStyle.Water) 950 else 1300, easing = FastOutSlowInEasing),
            )
            marks.remove(m)
        }
    }
}

/** Watches touches without consuming them, so everything underneath still works normally. */
fun Modifier.observeTouches(marks: TouchMarks, kind: CascadeStyle): Modifier =
    pointerInput(marks, kind) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { if (it.changedToDownIgnoreConsumed()) marks.add(it.position, kind) }
            }
        }
    }

@Composable
fun TouchMarksLayer(marks: TouchMarks, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Canvas(modifier.fillMaxSize()) {
        marks.marks.forEach { m ->
            val p = m.progress.value
            when (look.kind) {
                CascadeStyle.Water -> {
                    val r = 10.dp.toPx() + 80.dp.toPx() * p
                    drawCircle(
                        look.effect.copy(alpha = 0.55f * (1f - p)),
                        radius = r,
                        center = m.center,
                        style = Stroke(width = (2.5f * (1f - p) + 0.5f).dp.toPx()),
                    )
                    if (p > 0.12f) {
                        val q = (p - 0.12f) / 0.88f
                        drawCircle(
                            look.effect.copy(alpha = 0.35f * (1f - q)),
                            radius = 6.dp.toPx() + 55.dp.toPx() * q,
                            center = m.center,
                            style = Stroke(width = (1.8f * (1f - q) + 0.4f).dp.toPx()),
                        )
                    }
                }
                CascadeStyle.Ink -> {
                    val grow = 1f - (1f - p).pow(3)
                    val r = 4.dp.toPx() + 30.dp.toPx() * grow
                    drawPath(
                        blobPath(m.center, r, m.seed + p * 1.5f, roughness = 1.4f),
                        look.effect.copy(alpha = 0.16f * (1f - p).pow(1.5f)),
                    )
                }
                CascadeStyle.Classic -> Unit
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The style switch: a full-screen ripple (Water) or ink bloom (Ink) from the button
// ─────────────────────────────────────────────────────────────────────────────

class StyleSwitch(val origin: Offset, val target: CascadeStyle) {
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f)
}

@Composable
fun StyleSwitchLayer(switch: StyleSwitch?, modifier: Modifier = Modifier) {
    if (switch == null) return
    Canvas(modifier.fillMaxSize()) {
        val p = switch.progress.value
        val maxR = hypot(size.width, size.height) * 1.05f
        val growT = (p / 0.5f).coerceIn(0f, 1f)
        val grow = 1f - (1f - growT).pow(3)
        val fade = if (p < 0.5f) 1f else (1f - (p - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val o = switch.origin
        when (switch.target) {
            CascadeStyle.Water -> {
                drawCircle(Color(0xFF0B4459).copy(alpha = 0.94f * fade), radius = grow * maxR, center = o)
                for (k in 0 until 4) {
                    val rr = maxR * (p * 1.3f - k * 0.1f)
                    if (rr > 0f) {
                        drawCircle(
                            Color(0xFFBFF3FF).copy(alpha = 0.5f * (1f - p)),
                            radius = rr,
                            center = o,
                            style = Stroke(width = (4f - k).dp.toPx()),
                        )
                    }
                }
            }
            CascadeStyle.Ink -> {
                drawPath(
                    blobPath(o, grow * maxR, seed = 2f + p * 2.5f, roughness = 1.6f),
                    Color(0xFF15120F).copy(alpha = 0.96f * fade),
                )
                // Splatter drops flung out ahead of the bloom.
                val rnd = Random(5)
                repeat(9) {
                    val ang = rnd.nextFloat() * 2f * PI.toFloat()
                    val dist = grow * maxR * (0.9f + rnd.nextFloat() * 0.5f)
                    val c = Offset(o.x + cos(ang) * dist, o.y + sin(ang) * dist)
                    drawPath(
                        blobPath(c, (6 + rnd.nextInt(14)).dp.toPx() * grow, seed = it.toFloat()),
                        Color(0xFF15120F).copy(alpha = 0.9f * fade),
                    )
                }
            }
            CascadeStyle.Classic -> {
                drawCircle(Color(0xFF111317).copy(alpha = 0.92f * fade), radius = grow * maxR, center = o)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The style button: a droplet (Water) or an ensō brush circle (Ink)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StyleOrb(onSwitch: (Offset) -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val look = LocalLook.current
    var center by remember { mutableStateOf(Offset.Zero) }
    var spins by remember { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(spins * 360f, Motion.soft, label = "orbSpin")
    val breathe by rememberInfiniteTransition(label = "orb").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )

    Box(
        modifier
            .size(size)
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .graphicsLayer {
                rotationZ = rotation
                scaleX = breathe
                scaleY = breathe
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                spins += 1f
                onSwitch(center)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) { drawOrb(look) }
    }
}

private fun DrawScope.drawOrb(look: Look) {
    val c = Offset(size.width / 2f, size.height / 2f)
    when (look.kind) {
        CascadeStyle.Water, CascadeStyle.Classic -> {
            val drop = dropPath(c, size.minDimension * 0.9f)
            drawPath(drop, look.accent.copy(alpha = 0.9f))
            drawPath(drop, look.text.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
            // Highlight.
            drawCircle(
                Color.White.copy(alpha = 0.55f),
                radius = size.minDimension * 0.07f,
                center = Offset(c.x - size.minDimension * 0.1f, c.y + size.minDimension * 0.02f),
            )
        }
        CascadeStyle.Ink -> {
            // Ensō: one brushstroke circle, thick where the brush lands and thinning as it lifts.
            val r = size.minDimension * 0.34f
            val steps = 40
            for (i in 0 until steps) {
                val t = i.toFloat() / steps
                val sweep = 320f / steps
                val start = -70f + 320f * t
                val width = (1f - t * 0.75f) * size.minDimension * 0.13f
                drawArc(
                    look.text.copy(alpha = 0.92f - t * 0.25f),
                    startAngle = start,
                    sweepAngle = sweep + 1.5f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
            }
            // The red seal.
            val s = size.minDimension * 0.2f
            drawRect(look.accent, topLeft = Offset(c.x + r * 0.55f, c.y + r * 0.55f), size = Size(s, s))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Letter badge for the letter panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LetterBadge(letter: Char, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    when (look.kind) {
        CascadeStyle.Ink -> {
            // A red hanko seal with the letter cut out in paper color.
            Box(
                modifier
                    .size(44.dp)
                    .graphicsLayer { rotationZ = -3f }
                    .drawSeal(look.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter.toString(),
                    style = look.display(26.sp, color = Color(0xFFFBF7EE)).copy(fontWeight = FontWeight.Bold, shadow = null),
                )
            }
        }
        CascadeStyle.Water -> {
            Box(modifier.size(46.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(46.dp)) {
                    drawCircle(look.accent.copy(alpha = 0.18f))
                    drawCircle(look.accent.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))
                }
                Text(letter.toString(), style = look.display(26.sp, color = look.accent).copy(shadow = null, fontWeight = FontWeight.Light))
            }
        }
        CascadeStyle.Classic -> {
            Text(
                letter.toString(),
                style = look.style(30.sp, FontWeight.Bold, look.accent).copy(shadow = null),
                modifier = modifier,
            )
        }
    }
}

private fun Modifier.drawSeal(color: Color): Modifier = drawBehind {
    val inset = 2.dp.toPx()
    drawRoundRect(color, cornerRadius = CornerRadius(5.dp.toPx()))
    drawRoundRect(
        Color(0xFFFBF7EE).copy(alpha = 0.35f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(3.dp.toPx()),
        style = Stroke(1.dp.toPx()),
    )
}
