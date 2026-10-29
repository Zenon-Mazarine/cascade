package com.bidule.cascade.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp

/**
 * The letter strip on the right edge. Slide your thumb along it and the letters near your
 * finger swell and bow outward; [onLetter] fires for the letter under the finger.
 *
 * Water: a wobbly liquid wave with a droplet that chases your finger and stretches as it moves.
 * Ink: a firmer wave, an ink blot under your finger, and the current letter stamped with a red seal.
 */
@Composable
fun AlphabetRail(
    letters: List<Char>,
    active: Char?,
    onLetter: (Char) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onBookmarks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalLook.current
    val kind = look.kind
    val currentOnLetter by rememberUpdatedState(onLetter)
    val density = LocalDensity.current

    val bulgePx = with(density) {
        when (kind) {
            CascadeStyle.Water -> 60.dp
            CascadeStyle.Ink -> 46.dp
            else -> 54.dp
        }.toPx()
    }
    val sigmaPx = with(density) { (if (kind == CascadeStyle.Water) 52.dp else 46.dp).toPx() }

    var heightPx by remember { mutableIntStateOf(1) }
    var widthPx by remember { mutableIntStateOf(1) }
    var fingerY by remember { mutableFloatStateOf(-10_000f) }
    var touching by remember { mutableStateOf(false) }
    val wave by animateFloatAsState(
        if (touching) 1f else 0f,
        when (kind) {
            CascadeStyle.Water -> Motion.liquid
            CascadeStyle.Ink -> Motion.brush
            else -> Motion.bouncy
        },
        label = "wave",
    )
    // The droplet / blot lags behind the finger a little, which is what makes it feel liquid.
    val chaseY by animateFloatAsState(
        fingerY,
        spring(dampingRatio = if (kind == CascadeStyle.Water) 0.55f else 0.9f, stiffness = Spring.StiffnessMedium),
        label = "chase",
    )
    val iconTint = look.text.copy(alpha = 0.85f)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onSearch, modifier = Modifier.size(RailWidth)) {
            Icon(Icons.Filled.Search, contentDescription = "Search apps", tint = iconTint)
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged {
                    heightPx = it.height.coerceAtLeast(1)
                    widthPx = it.width.coerceAtLeast(1)
                }
                .drawBehind {
                    if (wave < 0.02f || fingerY < -1000f) return@drawBehind
                    val cx = size.width / 2f - bulgePx * wave
                    when (kind) {
                        CascadeStyle.Water -> {
                            val stretch = (abs(fingerY - chaseY) / 40.dp.toPx()).coerceIn(0f, 0.7f)
                            val r = 22.dp.toPx() * wave
                            val w = r * (1f - stretch * 0.35f)
                            val h = r * (1f + stretch)
                            drawOval(
                                look.effect.copy(alpha = 0.22f),
                                topLeft = Offset(cx - w, chaseY - h),
                                size = Size(w * 2, h * 2),
                            )
                            drawOval(
                                look.effect.copy(alpha = 0.5f),
                                topLeft = Offset(cx - w, chaseY - h),
                                size = Size(w * 2, h * 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()),
                            )
                        }
                        CascadeStyle.Ink -> {
                            drawPath(
                                blobPath(Offset(cx, chaseY), 24.dp.toPx() * wave, seed = chaseY * 0.01f, roughness = 1.3f),
                                look.effect.copy(alpha = if (look.isLight) 0.12f else 0.18f),
                            )
                        }
                        else -> Unit
                    }
                }
                .pointerInput(letters) {
                    if (letters.isEmpty()) return@pointerInput
                    fun letterAt(y: Float): Char {
                        val index = (y / heightPx * letters.size).toInt()
                        return letters[index.coerceIn(0, letters.lastIndex)]
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        touching = true
                        fingerY = down.position.y
                        currentOnLetter(letterAt(fingerY))
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                fingerY = change.position.y
                                currentOnLetter(letterAt(fingerY))
                                change.consume()
                            }
                        } finally {
                            touching = false
                        }
                    }
                },
        ) {
            val count = letters.size.coerceAtLeast(1)
            letters.forEachIndexed { i, c ->
                val isActive = c == active
                val color = when {
                    isActive && kind == CascadeStyle.Ink -> Color(0xFFFBF7EE)
                    isActive -> look.accent
                    else -> look.text.copy(alpha = 0.8f)
                }
                Text(
                    c.toString(),
                    style = look.style(
                        size = 12.sp,
                        weight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = color,
                    ).let { if (isActive && kind == CascadeStyle.Ink) it.copy(shadow = null) else it },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            // Lay letters out evenly, then push the ones near the finger outward.
                            val slot = heightPx.toFloat() / count
                            val centerY = slot * (i + 0.5f)
                            val d = centerY - fingerY
                            val g = exp(-(d * d) / (2f * sigmaPx * sigmaPx)) * wave
                            translationY = centerY - size.height / 2f
                            translationX = -bulgePx * g
                            val s = 1f + 0.9f * g
                            scaleX = s
                            scaleY = s
                        }
                        .drawBehind {
                            if (!isActive) return@drawBehind
                            when (kind) {
                                CascadeStyle.Ink -> {
                                    val side = maxOf(size.width, size.height) + 4.dp.toPx()
                                    drawRoundRect(
                                        look.accent,
                                        topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                                        size = Size(side, side),
                                        cornerRadius = CornerRadius(2.dp.toPx()),
                                    )
                                }
                                CascadeStyle.Water -> drawCircle(
                                    look.accent.copy(alpha = 0.22f),
                                    radius = maxOf(size.width, size.height) * 0.75f,
                                )
                                else -> Unit
                            }
                        },
                )
            }
        }

        IconButton(onClick = onBookmarks, modifier = Modifier.size(RailWidth)) {
            Icon(BookmarkIcon, contentDescription = "Bookmarks", tint = look.accent)
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(RailWidth)) {
            Icon(Icons.Filled.Settings, contentDescription = "Cascade settings", tint = iconTint)
        }
    }
}
