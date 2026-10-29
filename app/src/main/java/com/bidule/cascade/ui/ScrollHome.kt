package com.bidule.cascade.ui

import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bidule.cascade.AppEntry
import com.bidule.cascade.AppRepository
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val TabWidth = 34.dp

/** Small icons along the top: search and bookmarks on the left, extras and settings on the right. */
@Composable
fun TopBar(
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val look = LocalLook.current
    val tint = look.text.copy(alpha = 0.8f)
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Search apps", tint = tint) }
        IconButton(onClick = onBookmarks) { Icon(BookmarkIcon, contentDescription = "Bookmarks", tint = look.accent) }
        Spacer(Modifier.weight(1f))
        trailing()
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Cascade settings", tint = tint) }
    }
}

/**
 * Ink's header: the time set like a title page, the date in italics with the day pressed in a
 * red seal, and a brushstroke drawn beneath it every time you come home.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InkHeader(resumeTick: Int, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLook.current
    val now by produceState(LocalDateTime.now(), resumeTick) {
        while (true) {
            value = LocalDateTime.now()
            delay((60 - value.second) * 1000L)
        }
    }
    val locale = Locale.getDefault()
    val time = now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale))
    val weekday = now.format(DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.titlecase(locale) }
    val month = now.format(DateTimeFormatter.ofPattern("MMMM", locale))

    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onLongPress,
                onClick = {
                    AppRepository.safeStart(
                        context,
                        Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(time, style = look.display(68.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(weekday, style = look.style(17.sp, color = look.subtext).copy(fontStyle = FontStyle.Italic))
            Spacer(Modifier.width(10.dp))
            // The day of the month, stamped.
            Box(
                Modifier
                    .size(26.dp)
                    .graphicsLayer { rotationZ = -4f }
                    .drawBehind { drawRoundRect(look.accent, cornerRadius = CornerRadius(3.dp.toPx())) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    now.dayOfMonth.toString(),
                    style = look.style(13.sp, FontWeight.Bold, Color(0xFFFBF7EE)).copy(shadow = null),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(month, style = look.style(17.sp, color = look.subtext).copy(fontStyle = FontStyle.Italic))
        }
        Spacer(Modifier.height(10.dp))
        BrushDivider(resumeTick)
    }
}

/** A single horizontal brushstroke that paints itself left to right. */
@Composable
fun BrushDivider(trigger: Int, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        delay(120)
        progress.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
    }
    Canvas(modifier.width(220.dp).height(16.dp)) {
        drawPath(brushPath(size.width, size.height, progress.value, seed = 4.2f), look.text.copy(alpha = 0.85f))
    }
}

/** Splits the letters into a handful of dictionary-style tabs: A–C, D–F… */
fun indexGroups(letters: List<Char>, maxTabs: Int = 8): List<List<Char>> {
    if (letters.isEmpty()) return emptyList()
    val per = (letters.size + maxTabs - 1) / maxTabs
    return letters.chunked(per.coerceAtLeast(1))
}

fun groupLabel(g: List<Char>): String = if (g.size == 1) g.first().toString() else "${g.first()}–${g.last()}"

/**
 * Paper tabs along the right edge, like the thumb index cut into a dictionary.
 * Touch or slide along them to open that section; the active tab slides out in red.
 */
@Composable
fun ThumbIndex(
    groups: List<List<Char>>,
    active: Int?,
    onGroup: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalLook.current
    val currentOnGroup by rememberUpdatedState(onGroup)
    var heightPx by remember { mutableIntStateOf(1) }
    val paperA = if (look.isLight) Color(0xFFE9E1D0) else Color(0xFF2A2621)
    val paperB = if (look.isLight) Color(0xFFDFD5C1) else Color(0xFF221F1B)

    Box(
        modifier
            .width(TabWidth)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(groups) {
                if (groups.isEmpty()) return@pointerInput
                fun groupAt(y: Float) = (y / heightPx * groups.size).toInt().coerceIn(0, groups.lastIndex)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    currentOnGroup(groupAt(down.position.y))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        currentOnGroup(groupAt(change.position.y))
                        change.consume()
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            groups.forEachIndexed { i, g ->
                val out by animateFloatAsState(if (i == active) 1f else 0f, Motion.brush, label = "tab")
                val isActive = i == active
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer { translationX = -out * 8.dp.toPx() }
                        .drawBehind {
                            val color = if (isActive) look.accent else if (i % 2 == 0) paperA else paperB
                            // A tab: rounded on the side facing the page, square into the edge.
                            drawRoundRect(color, size = Size(size.width + 12.dp.toPx(), size.height), cornerRadius = CornerRadius(6.dp.toPx()))
                            if (!isActive) {
                                drawLine(
                                    look.text.copy(alpha = 0.12f),
                                    Offset(0f, size.height),
                                    Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx(),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        groupLabel(g),
                        style = look.style(
                            11.sp,
                            if (isActive) FontWeight.Bold else FontWeight.Medium,
                            if (isActive) Color(0xFFFBF7EE) else look.text.copy(alpha = 0.8f),
                        ).copy(shadow = null),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The page that opens beside the tabs: every app in that section. */
@Composable
fun IndexPage(
    groupIndex: Int,
    groups: List<List<Char>>,
    apps: List<AppEntry>,
    row: @Composable (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalLook.current
    Surface(
        modifier = modifier.width(310.dp).fillMaxHeight(0.78f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 2.dp, bottomEnd = 2.dp),
        color = look.panel.copy(alpha = 1f),
        shadowElevation = 14.dp,
    ) {
        AnimatedContent(
            targetState = groupIndex,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) { w -> if (forward) w / 5 else -w / 5 } +
                    fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(180)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(140)))
            },
            label = "page",
        ) { gi ->
            val group = groups.getOrNull(gi).orEmpty()
            val pageApps = remember(apps, group) { apps.filter { it.letter in group } }
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Faint ruled lines, like an index page.
                        val step = 34.dp.toPx()
                        var y = 90.dp.toPx()
                        while (y < size.height) {
                            drawLine(look.text.copy(alpha = 0.05f), Offset(16.dp.toPx(), y), Offset(size.width - 16.dp.toPx(), y), 1.dp.toPx())
                            y += step
                        }
                        // The red margin line.
                        drawLine(look.accent.copy(alpha = 0.25f), Offset(28.dp.toPx(), 0f), Offset(28.dp.toPx(), size.height), 1.dp.toPx())
                    }
            ) {
                Column(Modifier.padding(top = 18.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        LetterBadge(group.firstOrNull() ?: '#')
                        Spacer(Modifier.width(12.dp))
                        Text(
                            groupLabel(group.ifEmpty { listOf('#') }),
                            style = look.display(26.sp).copy(shadow = null, color = look.text),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        itemsIndexed(pageApps, key = { _, a -> a.key }) { i, app ->
                            CascadeIn(i, gi) { row(app) }
                        }
                    }
                }
            }
        }
    }
}
