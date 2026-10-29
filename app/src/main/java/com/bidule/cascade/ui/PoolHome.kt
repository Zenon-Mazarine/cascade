package com.bidule.cascade.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bidule.cascade.AppEntry
import com.bidule.cascade.AppRepository
import com.bidule.cascade.NotifPreview
import com.bidule.cascade.NotificationService
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Water's home screen: favorites float as bubbles on a pool. Bubbles you open often grow
 * bigger; ones with notifications glow and carry a droplet. New notifications drift along
 * the tide line. Pull the water up to reach every app; pull down for the notification shade.
 */
@Composable
fun PoolHome(
    favorites: List<AppEntry>,
    appsByPackage: Map<String, AppEntry>,
    notifications: Map<String, List<NotifPreview>>,
    launchCounts: Map<String, Int>,
    favoriteKeys: List<String>,
    bookmarkKeys: List<String>,
    resumeTick: Int,
    header: @Composable () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onShortcuts: (AppEntry) -> Unit,
    onPullUp: () -> Unit,
    onPullDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalLook.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pull = remember { Animatable(0f) }
    val thresholdPx = with(density) { 90.dp.toPx() }

    Column(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Tracked synchronously so the release decision never waits on the animation.
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val v = total
                        when {
                            v < -thresholdPx -> onPullUp()
                            v > thresholdPx -> onPullDown()
                        }
                        scope.launch { pull.animateTo(0f, Motion.liquid) }
                    },
                    onDragCancel = { scope.launch { pull.animateTo(0f, Motion.liquid) } },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        total += dy * 0.6f
                        // Resistance makes the water feel heavy.
                        scope.launch { pull.snapTo(pull.value + dy * 0.6f) }
                    },
                )
            }
    ) {
        header()

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val radii = remember(favorites, launchCounts, notifications.keys, wPx, hPx) {
                bubbleRadii(favorites, launchCounts, notifications.keys, wPx, hPx, density.density)
            }
            val centers = remember(radii, wPx, hPx, look.kind) {
                poolLayout(
                    radii, wPx, hPx,
                    labelPx = 22f * density.density,
                    spread = if (look.kind == CascadeStyle.Cosmos) 1.7f else 1.1f,
                    centerY = if (look.kind == CascadeStyle.Cosmos) 0.5f else 0.56f,
                )
            }
            // What lies under the objects: rake rings around stones, lines between stars.
            FieldUnderlay(look, radii, centers)
            if (favorites.isEmpty()) {
                Text(
                    when (look.kind) {
                        CascadeStyle.Stone -> "Pull up for all apps, then long-press one and choose “Pin to home” to place a stone here."
                        CascadeStyle.Cosmos -> "Pull up for all apps, then long-press one and choose “Pin to home” to add a star here."
                        else -> "Pull the water up, long-press any app and choose “Pin to home” to set it afloat here."
                    },
                    style = look.style(15.sp, color = look.subtext),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
            favorites.forEachIndexed { i, app ->
                val r = radii.getOrElse(i) { 30f }
                val c = centers.getOrElse(i) { Offset(wPx / 2, hPx / 2) }
                val rDp = with(density) { r.toDp() }
                Box(
                    Modifier.offset {
                        IntOffset((c.x - r - 8.dp.toPx()).roundToInt(), (c.y - r).roundToInt())
                    }
                ) {
                    CascadeIn(i, resumeTick) {
                        Bubble(
                            app = app,
                            radius = rDp,
                            notifCount = notifications[app.packageName]?.size ?: 0,
                            phase = i * 1.7f,
                            isFavorite = app.key in favoriteKeys,
                            isBookmarked = app.key in bookmarkKeys,
                            onLaunch = onLaunch,
                            onToggleFavorite = onToggleFavorite,
                            onToggleBookmark = onToggleBookmark,
                            onShortcuts = { onShortcuts(app) },
                        )
                    }
                }
            }
        }

        Tide(notifications, appsByPackage)
        when (look.kind) {
            CascadeStyle.Stone -> GardenEdge(pullPx = pull.value)
            CascadeStyle.Cosmos -> Horizon(pullPx = pull.value)
            else -> Waterline(pullPx = pull.value)
        }
    }
}

private fun bubbleRadii(
    favorites: List<AppEntry>,
    counts: Map<String, Int>,
    withNotifs: Set<String>,
    w: Float,
    h: Float,
    density: Float,
): List<Float> {
    if (favorites.isEmpty()) return emptyList()
    // Biggest bubble size that still lets everyone fit comfortably.
    val fit = sqrt((w * h) / (favorites.size * PI.toFloat() * 2.6f)) - 12f * density
    val base = fit.coerceIn(26f * density, 44f * density)
    val max = favorites.maxOf { counts[it.key] ?: 0 }.coerceAtLeast(1)
    return favorites.map { app ->
        val f = ln(1f + (counts[app.key] ?: 0)) / ln(1f + max)
        base * (0.8f + 0.4f * f) + if (app.packageName in withNotifs) 3f * density else 0f
    }
}

/**
 * Places bubbles on a sunflower spiral around a point low in the pool (where the thumb is),
 * then lets them push each other apart until nothing overlaps.
 */
private fun poolLayout(
    radii: List<Float>,
    w: Float,
    h: Float,
    labelPx: Float,
    spread: Float = 1.1f,
    centerY: Float = 0.56f,
): List<Offset> {
    val n = radii.size
    if (n == 0 || w <= 0f || h <= 0f) return emptyList()
    val cx = w * 0.5f
    val cy = h * centerY
    val avg = radii.average().toFloat()
    val xs = FloatArray(n)
    val ys = FloatArray(n)
    for (i in 0 until n) {
        val a = i * 2.39996f
        val d = sqrt(i.toFloat()) * avg * 2.1f
        xs[i] = cx + cos(a) * d
        ys[i] = cy + sin(a) * d * 0.85f
    }
    val gap = labelPx * spread
    repeat(220) {
        for (i in 0 until n) for (j in i + 1 until n) {
            val dx = xs[j] - xs[i]
            val dy = ys[j] - ys[i]
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val min = radii[i] + radii[j] + gap
            if (dist < min) {
                val push = (min - dist) / 2f
                val ux = dx / dist
                val uy = dy / dist
                xs[i] -= ux * push; ys[i] -= uy * push
                xs[j] += ux * push; ys[j] += uy * push
            }
        }
        for (i in 0 until n) {
            xs[i] += (cx - xs[i]) * 0.004f
            ys[i] += (cy - ys[i]) * 0.004f
            val r = radii[i]
            xs[i] = xs[i].coerceIn(r + 12f, w - r - 12f)
            ys[i] = ys[i].coerceIn(r + 8f, h - r - labelPx - 8f)
        }
    }
    return List(n) { Offset(xs[it], ys[it]) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    app: AppEntry,
    radius: Dp,
    notifCount: Int,
    phase: Float,
    isFavorite: Boolean,
    isBookmarked: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onShortcuts: () -> Unit,
) {
    val look = LocalLook.current
    val feel = LocalFeel.current
    var pressed by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val kind = look.kind
    val squish by animateFloatAsState(
        if (pressed || menuOpen) (if (kind == CascadeStyle.Stone) 0.93f else 0.86f) else 1f,
        if (kind == CascadeStyle.Stone) Motion.brush else Motion.liquid,
        label = "squish",
    )
    val t by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(4200 + (phase * 300).toInt() % 1600, easing = LinearEasing), RepeatMode.Restart),
        label = "bobT",
    )
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "glowT",
    )

    Column(
        Modifier.width(radius * 2 + 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(radius * 2)
                .graphicsLayer {
                    when (kind) {
                        // Stones don't move.
                        CascadeStyle.Stone -> {
                            scaleX = squish
                            scaleY = squish
                        }
                        // Stars drift slowly.
                        CascadeStyle.Cosmos -> {
                            translationY = sin(t * 0.5f + phase) * 5.dp.toPx()
                            translationX = cos(t * 0.35f + phase) * 4.dp.toPx()
                            scaleX = squish
                            scaleY = squish
                        }
                        else -> {
                            translationY = sin(t + phase) * 3.dp.toPx()
                            translationX = cos(t * 0.5f + phase) * 1.5.dp.toPx()
                            // A droplet is never perfectly round: it breathes a little.
                            scaleX = squish * (1f + 0.025f * sin(2 * t + phase))
                            scaleY = squish * (1f - 0.025f * sin(2 * t + phase))
                        }
                    }
                }
                .drawBehind {
                    if (kind == CascadeStyle.Stone) {
                        drawStone(look, phase, notifCount > 0, glow)
                        return@drawBehind
                    }
                    if (kind == CascadeStyle.Cosmos) {
                        drawStar(look, t, phase, notifCount > 0, glow)
                        return@drawBehind
                    }
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    if (notifCount > 0) {
                        drawCircle(
                            look.accent.copy(alpha = 0.5f * (1f - glow)),
                            radius = r * (1f + 0.35f * glow),
                            center = c,
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                    drawCircle(
                        Brush.radialGradient(
                            listOf(look.effect.copy(alpha = 0.04f), look.effect.copy(alpha = 0.22f)),
                            center = Offset(c.x - r * 0.25f, c.y - r * 0.3f),
                            radius = r * 1.2f,
                        ),
                        radius = r,
                        center = c,
                    )
                    drawCircle(look.effect.copy(alpha = 0.55f), radius = r, center = c, style = Stroke(1.2.dp.toPx()))
                    // Glassy highlight.
                    drawArc(
                        Color.White.copy(alpha = 0.6f),
                        startAngle = 200f,
                        sweepAngle = 55f,
                        useCenter = false,
                        topLeft = Offset(c.x - r * 0.78f, c.y - r * 0.78f),
                        size = Size(r * 1.56f, r * 1.56f),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawCircle(Color.White.copy(alpha = 0.7f), radius = 2.dp.toPx(), center = Offset(c.x - r * 0.5f, c.y - r * 0.62f))
                }
                .pointerInput(app.key) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { onLaunch(app) },
                        onLongPress = {
                            feel.longPress()
                            menuOpen = true
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                app.icon,
                contentDescription = app.label,
                colorFilter = look.iconFilter,
                modifier = Modifier.size(radius * if (kind == CascadeStyle.Water) 1.15f else 0.95f),
            )
            if (notifCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(look.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (notifCount > 9) "9+" else notifCount.toString(),
                        style = look.style(10.sp, FontWeight.Bold, look.background.takeIf { it != Color.Transparent } ?: Color(0xFF04121A)).copy(shadow = null),
                    )
                }
            }
            AppMenu(
                app = app,
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                isBookmarked = isBookmarked,
                onToggleBookmark = onToggleBookmark,
                shortcutsLabel = "Shortcuts",
                onShortcuts = onShortcuts,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            app.label,
            style = look.style(12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** The latest notifications, drifting along the tide line above the water. */
@Composable
private fun Tide(notifications: Map<String, List<NotifPreview>>, appsByPackage: Map<String, AppEntry>) {
    val look = LocalLook.current
    val context = LocalContext.current
    val latest = remember(notifications, appsByPackage) {
        notifications.values.flatten()
            .sortedByDescending { it.postTime }
            .mapNotNull { n -> appsByPackage[n.packageName]?.let { n to it } }
            .take(6)
    }
    AnimatedVisibility(
        visible = latest.isNotEmpty(),
        enter = fadeIn(Motion.soft) + expandVertically(),
        exit = fadeOut(Motion.soft) + shrinkVertically(),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            items(latest, key = { it.first.key }) { (n, app) ->
                Row(
                    Modifier
                        .animateItem()
                        .width(250.dp)
                        .background(look.panel.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                        .clickable { NotificationService.open(context, n, app) }
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(app.icon, contentDescription = null, colorFilter = look.iconFilter, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            n.title.ifBlank { app.label },
                            style = look.style(13.sp, FontWeight.SemiBold).copy(shadow = null),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (n.text.isNotBlank()) {
                            Text(
                                n.text,
                                style = look.style(12.sp, color = look.subtext).copy(shadow = null),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { NotificationService.dismiss(n) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = look.subtext, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** The surface of the water. Rises as you pull up. */
@Composable
private fun Waterline(pullPx: Float) {
    val look = LocalLook.current
    val t by rememberInfiniteTransition(label = "waves").animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "waveT",
    )
    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val rise = (-pullPx).coerceIn(-30.dp.toPx(), 120.dp.toPx())
            val base = size.height * 0.42f - rise * 0.5f
            val amp = 4.dp.toPx() + (rise.coerceAtLeast(0f) / 120.dp.toPx()) * 6.dp.toPx()
            fun wave(phase: Float, k: Float): Path = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                lineTo(0f, base)
                while (x <= size.width + 8f) {
                    lineTo(x, base + sin(x * k + phase) * amp + sin(x * k * 2.3f - phase * 1.3f) * amp * 0.35f)
                    x += 8f
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(
                wave(t + 1.2f, 0.018f),
                Brush.verticalGradient(
                    listOf(look.effect.copy(alpha = 0.10f), look.effect.copy(alpha = 0.22f)),
                    startY = base, endY = size.height,
                ),
            )
            val front = wave(t, 0.022f)
            drawPath(
                front,
                Brush.verticalGradient(
                    listOf(look.effect.copy(alpha = 0.14f), look.effect.copy(alpha = 0.30f)),
                    startY = base, endY = size.height,
                ),
            )
            drawPath(front, look.effect.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
        }
        Text(
            "︿  pull up for all apps",
            style = look.style(12.sp, color = look.text.copy(alpha = 0.75f)),
            modifier = Modifier.padding(top = 26.dp),
        )
    }
}

/** Water's app drawer: every app, rising from the bottom of the pool. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsSheet(
    apps: List<AppEntry>,
    row: @Composable (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    val look = LocalLook.current
    var query by remember { mutableStateOf("") }
    val results = remember(query, apps) {
        val q = AppRepository.normalize(query.trim())
        if (q.isEmpty()) apps else apps.filter { it.searchKey.contains(q) }
    }
    val grouped = remember(results) { results.groupBy { it.letter } }
    val closeThreshold = with(LocalDensity.current) { 110.dp.toPx() }
    // Pull down past the top of the list to sink the sheet again.
    val sink = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > closeThreshold) {
                        pulled = Float.NEGATIVE_INFINITY
                        onClose()
                    }
                } else if (available.y < 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                pulled = 0f
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(look.overlay)
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            // A little handle, like the surface of the water.
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 6.dp)
                    .size(width = 44.dp, height = 5.dp)
                    .background(look.effect.copy(alpha = 0.5f), RoundedCornerShape(50))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "All apps",
                    style = look.display(30.sp).copy(shadow = null),
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                TextButton(onClick = onClose) { Text("Close", color = look.accent) }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).nestedScroll(sink)) {
                grouped.forEach { (letter, list) ->
                    stickyHeader(key = "h$letter") {
                        Box(Modifier.fillMaxWidth().background(look.overlay).padding(vertical = 4.dp)) {
                            LetterBadge(letter, Modifier.padding(start = 4.dp))
                        }
                    }
                    items(list, key = { it.key }) { app -> Box(Modifier.animateItem()) { row(app) } }
                }
            }
        }
    }
}

/** A floating card with one app's shortcuts (Pool and Scroll use this instead of unfolding rows). */
@Composable
fun ShortcutCard(app: AppEntry, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    LaunchedEffect(app.key) { }
    Column(
        modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .background(look.panel, RoundedCornerShape(26.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(app.icon, contentDescription = null, colorFilter = look.iconFilter, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(app.label, style = look.style(19.sp, FontWeight.SemiBold).copy(shadow = null), modifier = Modifier.weight(1f))
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = look.subtext) }
        }
        ShortcutList(app, indent = 0.dp, onUsed = onDone)
    }
}

@Suppress("unused")
private fun clamp01(v: Float) = min(1f, kotlin.math.max(0f, v))


// ─────────────────────────────────────────────────────────────────────────────
// Stone garden and constellation pieces
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FieldUnderlay(look: Look, radii: List<Float>, centers: List<Offset>) {
    if (look.kind != CascadeStyle.Stone && look.kind != CascadeStyle.Cosmos) return
    val t by rememberInfiniteTransition(label = "lines").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "linesT",
    )
    Canvas(Modifier.fillMaxSize()) {
        if (look.kind == CascadeStyle.Stone) {
            // Concentric rake rings around each stone (the stone sits a little above its label).
            centers.forEachIndexed { i, c ->
                val r = radii.getOrElse(i) { 30f }
                for (k in 1..4) {
                    drawCircle(
                        look.effect.copy(alpha = (if (look.isLight) 0.32f else 0.22f) * (1f - k * 0.15f)),
                        radius = r + k * 9.dp.toPx(),
                        center = c,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
        } else {
            // Constellation: join the stars with the shortest set of lines that connects them all.
            val n = centers.size
            if (n < 2) return@Canvas
            val inTree = BooleanArray(n).also { it[0] = true }
            repeat(n - 1) {
                var best = Float.MAX_VALUE
                var a = -1
                var b = -1
                for (i in 0 until n) if (inTree[i]) for (j in 0 until n) if (!inTree[j]) {
                    val d = (centers[i] - centers[j]).getDistance()
                    if (d < best) { best = d; a = i; b = j }
                }
                if (b < 0) return@repeat
                inTree[b] = true
                val pa = centers[a]
                val pb = centers[b]
                val dir = (pb - pa) / best
                val ra = radii[a] + 4.dp.toPx()
                val rb = radii[b] + 4.dp.toPx()
                if (best > ra + rb) {
                    drawLine(
                        look.effect.copy(alpha = 0.22f + 0.12f * t),
                        pa + dir * ra,
                        pb - dir * rb,
                        1.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStone(look: Look, seed: Float, hasNotif: Boolean, glow: Float) {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    val dark = if (look.isLight) Color(0xFF4A4640) else Color(0xFF2E2B27)
    val lightTone = if (look.isLight) Color(0xFF8E8880) else Color(0xFF6A655D)
    val shape = blobPath(c, r * 0.98f, seed = seed, roughness = 0.45f)
    // Soft shadow on the sand.
    drawPath(blobPath(Offset(c.x + r * 0.08f, c.y + r * 0.14f), r, seed = seed, roughness = 0.45f), Color.Black.copy(alpha = 0.18f))
    drawPath(shape, Brush.radialGradient(listOf(lightTone, dark), Offset(c.x - r * 0.3f, c.y - r * 0.35f), r * 1.5f))
    drawCircle(Color.White.copy(alpha = 0.18f), r * 0.18f, Offset(c.x - r * 0.4f, c.y - r * 0.45f))
    if (hasNotif) {
        // A little moss grows on stones with something new.
        drawCircle(look.accent.copy(alpha = 0.6f + 0.3f * glow), r * 0.16f, Offset(c.x + r * 0.62f, c.y - r * 0.55f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(look: Look, t: Float, phase: Float, hasNotif: Boolean, glow: Float) {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    val twinkle = 0.5f + 0.5f * sin(t * 1.6f + phase)
    val halo = r * (1.25f + 0.15f * twinkle + if (hasNotif) 0.35f * glow else 0f)
    drawCircle(
        Brush.radialGradient(listOf(look.effect.copy(alpha = 0.45f), Color.Transparent), c, halo),
        halo,
        c,
    )
    drawCircle(Color(0xFF0B0E22).copy(alpha = if (look.isLight) 0.12f else 0.75f), r * 0.82f, c)
    drawCircle(look.effect.copy(alpha = 0.7f), r * 0.82f, c, style = Stroke(1.dp.toPx()))
    drawSparkle(Offset(c.x + r * 0.72f, c.y - r * 0.72f), r * (0.22f + 0.1f * twinkle), Color.White.copy(alpha = 0.9f))
}

/** The wooden edge of the garden. */
@Composable
private fun GardenEdge(pullPx: Float) {
    val look = LocalLook.current
    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val lift = (-pullPx).coerceIn(0f, 120.dp.toPx()) * 0.3f
            val top = size.height * 0.35f - lift
            val wood = if (look.isLight) Color(0xFF7A5F43) else Color(0xFF4A3A2A)
            drawRect(wood, Offset(0f, top), Size(size.width, size.height - top))
            for (k in 0 until 5) {
                val y = top + 5.dp.toPx() + k * 7.dp.toPx()
                val path = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    while (x <= size.width) {
                        lineTo(x, y + sin(x * 0.01f + k * 1.3f) * 1.5.dp.toPx())
                        x += 14f
                    }
                }
                drawPath(path, Color.Black.copy(alpha = 0.18f), style = Stroke(1.dp.toPx()))
            }
            drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, top), Offset(size.width, top), 1.dp.toPx())
        }
        Text(
            "︿  pull up for all apps",
            style = look.style(12.sp, color = Color(0xFFF3EDE1).copy(alpha = 0.85f)).copy(shadow = null),
            modifier = Modifier.padding(top = 26.dp),
        )
    }
}

/** A planet's glowing limb at the bottom of the sky. */
@Composable
private fun Horizon(pullPx: Float) {
    val look = LocalLook.current
    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val rise = (-pullPx).coerceIn(0f, 120.dp.toPx()) * 0.4f
            val r = size.width * 1.4f
            val c = Offset(size.width / 2f, size.height * 0.45f - rise + r)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Transparent, look.accent.copy(alpha = 0.35f), Color.Transparent),
                    c, r + 30.dp.toPx(),
                ),
                r + 30.dp.toPx(),
                c,
            )
            drawCircle(if (look.isLight) Color(0xFFDDE1F2) else Color(0xFF070918), r, c)
            drawCircle(look.effect.copy(alpha = 0.7f), r, c, style = Stroke(1.5.dp.toPx()))
        }
        Text(
            "︿  pull up for all apps",
            style = look.style(12.sp, color = look.text.copy(alpha = 0.75f)),
            modifier = Modifier.padding(top = 30.dp),
        )
    }
}
