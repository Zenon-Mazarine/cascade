package com.bidule.cascade.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The letter strip on the right edge. Touch or slide your thumb along it and [onLetter]
 * fires for the letter under your finger. Search sits on top, settings at the bottom.
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
    val currentOnLetter by rememberUpdatedState(onLetter)
    var heightPx by remember { mutableIntStateOf(1) }
    val iconTint = Color.White.copy(alpha = 0.85f)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onSearch, modifier = Modifier.size(RailWidth)) {
            Icon(Icons.Filled.Search, contentDescription = "Search apps", tint = iconTint)
        }

        Column(
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
                        currentOnLetter(letterAt(down.position.y))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            currentOnLetter(letterAt(change.position.y))
                            change.consume()
                        }
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { c ->
                val isActive = c == active
                Text(
                    c.toString(),
                    style = if (isActive) {
                        OnWallpaper.letter.copy(color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    } else {
                        OnWallpaper.letter
                    },
                )
            }
        }

        IconButton(onClick = onSettings, modifier = Modifier.size(RailWidth)) {
            Icon(Icons.Filled.Settings, contentDescription = "Cascade settings", tint = iconTint)
        }
    }
}
