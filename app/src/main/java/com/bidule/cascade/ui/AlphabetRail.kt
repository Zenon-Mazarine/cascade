package com.bidule.cascade.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp

/**
 * The letter strip on the right edge. Slide your thumb along it and the letters near your
 * finger swell and bow outward in a wave; [onLetter] fires for the letter under the finger.
 * On release the wave springs back flat. Search sits on top, settings at the bottom.
 */
@Composable
fun AlphabetRail(
    letters: List<Char>,
    active: Char?,
    onLetter: (Char) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalLook.current
    val currentOnLetter by rememberUpdatedState(onLetter)
    val density = LocalDensity.current
    val bulgePx = with(density) { 54.dp.toPx() }
    val sigmaPx = with(density) { 46.dp.toPx() }

    var heightPx by remember { mutableIntStateOf(1) }
    var fingerY by remember { mutableFloatStateOf(-10_000f) }
    var touching by remember { mutableStateOf(false) }
    val wave by animateFloatAsState(if (touching) 1f else 0f, Motion.bouncy, label = "wave")

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onSearch, modifier = Modifier.size(RailWidth)) {
            Icon(Icons.Filled.Search, contentDescription = "Search apps", tint = look.text.copy(alpha = 0.85f))
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
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
                Text(
                    c.toString(),
                    style = look.style(
                        size = 12.sp,
                        weight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) look.accent else look.text.copy(alpha = 0.8f),
                    ),
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
                        },
                )
            }
        }

        IconButton(onClick = onSettings, modifier = Modifier.size(RailWidth)) {
            Icon(Icons.Filled.Settings, contentDescription = "Cascade settings", tint = look.text.copy(alpha = 0.85f))
        }
    }
}
