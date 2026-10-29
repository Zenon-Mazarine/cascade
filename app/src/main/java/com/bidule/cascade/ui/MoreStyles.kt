package com.bidule.cascade.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Visual building blocks for Stone, Paper, Neon and Cosmos.

private fun grain(rgb: Int, maxAlpha: Int, seed: Int): ImageBitmap {
    val w = 160
    val h = 160
    val rnd = Random(seed)
    val px = IntArray(w * h) {
        val r = rnd.nextFloat()
        val a = if (r > 0.985f) maxAlpha * 3 else (rnd.nextFloat() * maxAlpha).toInt()
        (a.coerceAtMost(255) shl 24) or rgb
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

// ─────────────────────────────────────────────────────────────────────────────
// Backdrops
// ─────────────────────────────────────────────────────────────────────────────

/** Raked sand: fine grain and long parallel lines. Rings around stones are drawn by the garden. */
@Composable
fun StoneBackdrop(look: Look, modifier: Modifier = Modifier) {
    val sand = remember(look.isLight) { grain(if (look.isLight) 0x5A4A35 else 0xE8DCC4, 18, 3) }
    val brush = remember(sand) { ShaderBrush(ImageShader(sand, TileMode.Repeated, TileMode.Repeated)) }
    Canvas(modifier.fillMaxSize()) {
        if (look.mode == ThemeMode.Wallpaper) drawRect(Color(0xFF2A241C).copy(alpha = 0.35f))
        drawRect(brush, alpha = if (look.mode == ThemeMode.Light) 0.8f else 0.5f)
        drawRakeLines(look.effect.copy(alpha = if (look.isLight) 0.22f else 0.14f), 0f, size.height)
    }
}

fun DrawScope.drawRakeLines(color: Color, top: Float, bottom: Float) {
    val step = 9.dp.toPx()
    var y = top + step
    while (y < bottom) {
        val path = Path()
        var x = 0f
        path.moveTo(0f, y)
        while (x <= size.width) {
            path.lineTo(x, y + sin(x * 0.006f + y * 0.002f) * 3.dp.toPx())
            x += 16f
        }
        drawPath(path, color, style = Stroke(1.dp.toPx()))
        y += step
    }
}

/** A sheet that's been folded and opened again: soft creases and facets. */
@Composable
fun PaperBackdrop(look: Look, modifier: Modifier = Modifier) {
    val fiber = remember(look.isLight) { grain(if (look.mode == ThemeMode.Dark) 0xFFFFFF else 0x3A3226, 10, 9) }
    val brush = remember(fiber) { ShaderBrush(ImageShader(fiber, TileMode.Repeated, TileMode.Repeated)) }
    Canvas(modifier.fillMaxSize()) {
        if (look.mode == ThemeMode.Wallpaper) drawRect(Color(0xFF1F1B17).copy(alpha = 0.3f))
        drawRect(brush, alpha = 0.6f)
        val shade = if (look.isLight) Color.Black else Color.White
        val w = size.width
        val h = size.height
        // Facets between crease lines, each a slightly different shade, like light on folds.
        val facets = listOf(
            listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w * 0.5f, h * 0.38f)) to 0.025f,
            listOf(Offset(0f, 0f), Offset(w * 0.5f, h * 0.38f), Offset(0f, h * 0.7f)) to 0.045f,
            listOf(Offset(w, 0f), Offset(w, h * 0.62f), Offset(w * 0.5f, h * 0.38f)) to 0.0f,
            listOf(Offset(0f, h * 0.7f), Offset(w * 0.5f, h * 0.38f), Offset(w, h * 0.62f), Offset(w, h), Offset(0f, h)) to 0.03f,
        )
        facets.forEach { (pts, a) ->
            if (a > 0f) {
                val p = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(p, shade.copy(alpha = a))
            }
        }
        val crease = shade.copy(alpha = 0.10f)
        val cw = 1.dp.toPx()
        drawLine(crease, Offset(0f, 0f), Offset(w * 0.5f, h * 0.38f), cw)
        drawLine(crease, Offset(w, 0f), Offset(w * 0.5f, h * 0.38f), cw)
        drawLine(crease, Offset(0f, h * 0.7f), Offset(w * 0.5f, h * 0.38f), cw)
        drawLine(crease, Offset(w, h * 0.62f), Offset(w * 0.5f, h * 0.38f), cw)
    }
}

/** Night street: rain, a skyline with lit windows, and color pooling on the wet ground. */
@Composable
fun NeonBackdrop(look: Look, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "rain").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "rainT",
    )
    val drops = remember { List(70) { Triple(Random.nextFloat(), Random.nextFloat(), 0.6f + Random.nextFloat() * 0.8f) } }
    val buildings = remember { buildSkyline() }
    val night = look.mode != ThemeMode.Light
    Canvas(modifier.fillMaxSize()) {
        if (look.mode == ThemeMode.Wallpaper) drawRect(Color(0xFF07060D).copy(alpha = 0.55f))
        val w = size.width
        val h = size.height
        // Glow pooling on the wet street.
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, look.accent.copy(alpha = 0.10f), look.effect.copy(alpha = 0.14f)),
                startY = h * 0.6f, endY = h,
            ),
            topLeft = Offset(0f, h * 0.6f),
            size = Size(w, h * 0.4f),
        )
        // Skyline.
        val skyline = if (night) Color(0xFF0E0B1A) else Color(0xFFCFCADB)
        val baseY = h * 0.94f
        buildings.forEach { b ->
            val left = b.x * w
            val bw = b.w * w
            val top = baseY - b.h * h * 0.28f
            drawRect(skyline, topLeft = Offset(left, top), size = Size(bw, baseY - top + 2f))
            b.windows.forEach { (wx, wy, lit) ->
                if (lit) {
                    drawRect(
                        NeonColors[(wx * 10 + wy * 7).toInt().mod(NeonColors.size)].copy(alpha = if (night) 0.55f else 0.35f),
                        topLeft = Offset(left + wx * bw, top + wy * (baseY - top)),
                        size = Size(2.5.dp.toPx(), 3.5.dp.toPx()),
                    )
                }
            }
        }
        // Rain.
        val rain = (if (night) Color.White else Color(0xFF5A5470)).copy(alpha = 0.16f)
        drops.forEach { (x0, y0, speed) ->
            val y = ((y0 + t * speed) % 1f) * (h + 80f) - 40f
            val x = x0 * w + (y / h) * 30f
            drawLine(rain, Offset(x, y), Offset(x - 4.dp.toPx(), y + 18.dp.toPx() * speed), 1.dp.toPx(), StrokeCap.Round)
        }
    }
}

private class Building(val x: Float, val w: Float, val h: Float, val windows: List<Triple<Float, Float, Boolean>>)

private fun buildSkyline(): List<Building> {
    val rnd = Random(21)
    val out = ArrayList<Building>()
    var x = -0.02f
    while (x < 1f) {
        val w = 0.06f + rnd.nextFloat() * 0.1f
        val h = 0.25f + rnd.nextFloat() * 0.75f
        val windows = ArrayList<Triple<Float, Float, Boolean>>()
        var wy = 0.08f
        while (wy < 0.92f) {
            var wx = 0.15f
            while (wx < 0.8f) {
                windows += Triple(wx, wy, rnd.nextFloat() > 0.72f)
                wx += 0.22f
            }
            wy += 0.09f
        }
        out += Building(x, w, h, windows)
        x += w + rnd.nextFloat() * 0.015f
    }
    return out
}

/** Deep space: a twinkling starfield and slow nebulae. */
@Composable
fun CosmosBackdrop(look: Look, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "cosmos").animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "cosmosT",
    )
    val stars = remember { List(170) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 6.28f) } }
    Canvas(modifier.fillMaxSize()) {
        if (look.mode == ThemeMode.Wallpaper) drawRect(Color(0xFF04050F).copy(alpha = 0.45f))
        val w = size.width
        val h = size.height
        val nebulaA = if (look.isLight) 0.10f else 0.16f
        listOf(
            Triple(Offset(w * (0.3f + 0.05f * sin(t)), h * 0.3f), w * 0.8f, look.accent),
            Triple(Offset(w * (0.8f + 0.05f * cos(t)), h * 0.7f), w * 0.7f, look.effect),
        ).forEach { (c, r, col) ->
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = nebulaA), Color.Transparent), c, r), r, c)
        }
        val starColor = if (look.isLight) look.text else Color.White
        stars.forEach { s ->
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (1f + s[2] * 2f) + s[3]))
            val r = (0.6f + s[2] * 1.2f).dp.toPx()
            drawCircle(starColor.copy(alpha = (if (look.isLight) 0.25f else 0.8f) * twinkle), r, Offset(s[0] * w, s[1] * h))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Touch marks
// ─────────────────────────────────────────────────────────────────────────────

fun DrawScope.drawTouchMark(look: Look, center: Offset, p: Float, seed: Float) {
    when (look.kind) {
        CascadeStyle.Stone -> for (k in 0 until 3) {
            val q = (p - k * 0.12f).coerceIn(0f, 1f)
            if (q > 0f) {
                drawCircle(
                    look.effect.copy(alpha = 0.45f * (1f - q)),
                    radius = 8.dp.toPx() + 46.dp.toPx() * q,
                    center = center,
                    style = Stroke(1.5.dp.toPx()),
                )
            }
        }
        CascadeStyle.Paper -> rotate(45f + p * 60f, center) {
            val s = 10.dp.toPx() + 40.dp.toPx() * p
            drawRect(
                look.effect.copy(alpha = 0.35f * (1f - p)),
                topLeft = Offset(center.x - s / 2, center.y - s / 2),
                size = Size(s, s),
                style = Stroke(1.2.dp.toPx()),
            )
            drawLine(look.effect.copy(alpha = 0.25f * (1f - p)), Offset(center.x - s / 2, center.y - s / 2), Offset(center.x + s / 2, center.y + s / 2), 1.dp.toPx())
        }
        CascadeStyle.Neon -> {
            val color = if (look.mono) look.text else NeonColors[(seed * 10).toInt().mod(NeonColors.size)]
            for (k in 0 until 9) {
                val a = k / 9f * 2f * PI.toFloat() + seed
                val r0 = 6.dp.toPx() + 30.dp.toPx() * p
                val r1 = r0 + 14.dp.toPx() * (1f - p)
                drawLine(
                    color.copy(alpha = 0.8f * (1f - p)),
                    Offset(center.x + cos(a) * r0, center.y + sin(a) * r0),
                    Offset(center.x + cos(a) * r1, center.y + sin(a) * r1),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
            }
        }
        CascadeStyle.Cosmos -> for (k in 0 until 6) {
            val a = k / 6f * 2f * PI.toFloat() + seed
            val d = 6.dp.toPx() + 40.dp.toPx() * p
            drawSparkle(
                Offset(center.x + cos(a) * d, center.y + sin(a) * d),
                (5.dp.toPx() * (1f - p)).coerceAtLeast(0.5f),
                look.effect.copy(alpha = 0.9f * (1f - p)),
            )
        }
        else -> Unit
    }
}

/** A four-pointed star. */
fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
}

// ─────────────────────────────────────────────────────────────────────────────
// Style switch transitions
// ─────────────────────────────────────────────────────────────────────────────

fun DrawScope.drawSwitch(target: CascadeStyle, o: Offset, p: Float, grow: Float, fade: Float, maxR: Float) {
    when (target) {
        CascadeStyle.Stone -> {
            drawPath(blobPath(o, grow * maxR, seed = 1.3f + p, roughness = 0.6f), Color(0xFFD9CDB4).copy(alpha = 0.97f * fade))
            for (k in 1..5) {
                val r = grow * maxR - k * 14.dp.toPx()
                if (r > 0f) drawCircle(Color(0xFF8C7B62).copy(alpha = 0.35f * fade), r, o, style = Stroke(1.5.dp.toPx()))
            }
        }
        CascadeStyle.Paper -> rotate(45f + p * 25f, o) {
            val half = grow * maxR
            drawRect(Color(0xFFF7F6F2).copy(alpha = 0.98f * fade), Offset(o.x - half, o.y - half), Size(half * 2, half * 2))
            drawLine(Color.Black.copy(alpha = 0.12f * fade), Offset(o.x - half, o.y), Offset(o.x + half, o.y), 1.5.dp.toPx())
            drawLine(Color.Black.copy(alpha = 0.12f * fade), Offset(o.x, o.y - half), Offset(o.x, o.y + half), 1.5.dp.toPx())
        }
        CascadeStyle.Neon -> {
            // The power cuts out with a flicker, then the signs come back on.
            val flicker = if (p < 0.5f) {
                if ((p * 34).toInt() % 3 == 1) 0.45f else 0.97f
            } else fade
            drawRect(Color(0xFF07060D).copy(alpha = flicker * min(1f, p * 5f)))
            val y = size.height * ((p * 1.6f) % 1f)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFF3EA5).copy(alpha = 0.35f * fade), Color.Transparent), y - 30f, y + 30f),
                Offset(0f, y - 30f),
                Size(size.width, 60f),
            )
        }
        CascadeStyle.Cosmos -> {
            drawCircle(Color(0xFF04050F).copy(alpha = 0.96f * fade), grow * maxR, o)
            val rnd = Random(3)
            repeat(48) {
                val a = rnd.nextFloat() * 2f * PI.toFloat()
                val d0 = rnd.nextFloat() * maxR * 0.3f + maxR * p * 0.6f
                val len = 30.dp.toPx() + maxR * 0.25f * p
                drawLine(
                    Color.White.copy(alpha = 0.7f * (1f - p)),
                    Offset(o.x + cos(a) * d0, o.y + sin(a) * d0),
                    Offset(o.x + cos(a) * (d0 + len), o.y + sin(a) * (d0 + len)),
                    (1f + rnd.nextFloat() * 1.5f).dp.toPx(),
                    StrokeCap.Round,
                )
            }
        }
        else -> drawCircle(Color(0xFF111317).copy(alpha = 0.92f * fade), radius = grow * maxR, center = o)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Style icons (the orb and the picker)
// ─────────────────────────────────────────────────────────────────────────────

fun DrawScope.drawStyleIcon(look: Look) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val m = size.minDimension
    when (look.kind) {
        CascadeStyle.Stone -> {
            for (k in 1..2) {
                drawArc(
                    look.effect.copy(alpha = 0.6f),
                    startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(c.x - m * (0.28f + k * 0.08f), c.y - m * (0.18f + k * 0.06f)),
                    size = Size(m * (0.56f + k * 0.16f), m * (0.36f + k * 0.12f) * 2f),
                    style = Stroke(1.2.dp.toPx()),
                )
            }
            val stone = blobPath(Offset(c.x, c.y + m * 0.08f), m * 0.26f, seed = 2.2f, roughness = 0.5f)
            drawPath(stone, Brush.radialGradient(listOf(Color(0xFF8A857D), Color(0xFF4A4640)), Offset(c.x - m * 0.08f, c.y), m * 0.4f))
            drawCircle(Color.White.copy(alpha = 0.35f), m * 0.05f, Offset(c.x - m * 0.08f, c.y - m * 0.02f))
        }
        CascadeStyle.Paper -> {
            // A folded square: four triangles in two shades.
            val r = m * 0.36f
            val top = Offset(c.x, c.y - r)
            val right = Offset(c.x + r, c.y)
            val bottom = Offset(c.x, c.y + r)
            val left = Offset(c.x - r, c.y)
            fun tri(a: Offset, b: Offset, color: Color) =
                drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)
            tri(top, right, look.accent)
            tri(right, bottom, look.accent.copy(alpha = 0.7f))
            tri(bottom, left, look.accent)
            tri(left, top, look.accent.copy(alpha = 0.7f))
            drawLine(look.text.copy(alpha = 0.35f), top, bottom, 1.dp.toPx())
        }
        CascadeStyle.Neon -> {
            val col = look.accent
            for (k in 4 downTo 1) {
                drawCircle(col.copy(alpha = 0.08f * k), m * 0.3f, c, style = Stroke((k * 3).dp.toPx()))
            }
            drawCircle(Color.White.copy(alpha = 0.9f), m * 0.3f, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(col, m * 0.3f, c, style = Stroke(2.5.dp.toPx()))
        }
        CascadeStyle.Cosmos -> {
            drawCircle(Brush.radialGradient(listOf(look.effect.copy(alpha = 0.5f), Color.Transparent), c, m * 0.45f), m * 0.45f, c)
            drawSparkle(c, m * 0.32f, look.effect)
            drawCircle(look.text.copy(alpha = 0.8f), 1.5.dp.toPx(), Offset(c.x + m * 0.3f, c.y - m * 0.28f))
            drawCircle(look.text.copy(alpha = 0.6f), 1.dp.toPx(), Offset(c.x - m * 0.32f, c.y + m * 0.22f))
        }
        else -> Unit
    }
}

@Suppress("unused")
private fun easeOut(x: Float) = 1f - (1f - x).pow(3) + max(0f, 0f)
