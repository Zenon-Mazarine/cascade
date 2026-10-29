package com.bidule.cascade.ui

import android.os.Build
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import kotlin.math.min
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.hypot
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bidule.cascade.AppEntry
import com.bidule.cascade.AppRepository
import com.bidule.cascade.NotifPreview
import com.bidule.cascade.NotificationService
import com.bidule.cascade.Shortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Callbacks for long-press-and-drag reordering. [onDrag] gets the vertical movement in pixels. */
class DragCallbacks(
    val onStart: () -> Unit,
    val onDrag: (Float) -> Unit,
    val onEnd: () -> Unit,
)

private enum class Gesture { Tap, Swipe, Cancel }

/**
 * One app in a list.
 * - Tap: open.
 * - Swipe right: show the app's shortcuts underneath.
 * - Long-press: menu. Keep holding and move to drag it to a new spot (favorites only).
 * In the favorites list ([big] = true) its latest notification unfolds underneath.
 */
@Composable
fun AppRow(
    app: AppEntry,
    isFavorite: Boolean,
    notifications: List<NotifPreview>,
    big: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onToggleFavorite: (String) -> Unit,
    isBookmarked: Boolean = false,
    onToggleBookmark: ((String) -> Unit)? = null,
    shortcutsOpen: Boolean = false,
    onToggleShortcuts: (() -> Unit)? = null,
    drag: DragCallbacks? = null,
    /** Ink's Scroll layout: a centered word, no icon, notes written underneath like marginalia. */
    word: Boolean = false,
    /** Neon's street: the row is a glowing sign. The number picks its color and which side it hangs on. */
    neon: Int = -1,
) {
    val look = LocalLook.current
    val context = LocalContext.current
    val feel = LocalFeel.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var touchPoint by remember { mutableStateOf(Offset.Zero) }
    val seed = remember(app.key) { (app.key.hashCode() and 0xFFFF) / 6553.5f }
    val swipeX = remember { Animatable(0f) }

    val currentLaunch by rememberUpdatedState(onLaunch)
    val currentShortcuts by rememberUpdatedState(onToggleShortcuts)
    val currentDrag by rememberUpdatedState(drag)

    val swipeTriggerPx = with(density) { 56.dp.toPx() }
    val swipeMaxPx = with(density) { 90.dp.toPx() }

    val scale by animateFloatAsState(if (pressed || menuOpen) 0.95f else 1f, Motion.bouncy, label = "press")
    // 0→1 while the row is held: drives the ripple (Water), brushstroke (Ink) or plain highlight.
    val pressFx by animateFloatAsState(
        if (pressed || menuOpen || shortcutsOpen) 1f else 0f,
        if (look.kind == CascadeStyle.Ink) tween(300) else tween(420),
        label = "pressFx",
    )

    // Keep the last notification on screen while it folds away.
    var lastNotif by remember { mutableStateOf<NotifPreview?>(null) }
    notifications.firstOrNull()?.let { lastNotif = it }
    val showNotif = big && notifications.isNotEmpty()

    val gestures = Modifier.pointerInput(app.key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            touchPoint = down.position
            // A short delay so a row doesn't flash "pressed" when you're just scrolling past it.
            val pressJob = scope.launch {
                delay(70)
                pressed = true
            }
            val slop = viewConfiguration.touchSlop
            try {
                var total = Offset.Zero
                // Decide what this touch is before the long-press timeout runs out.
                val gesture: Gesture? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var result = Gesture.Cancel
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!change.isConsumed) {
                                change.consume()
                                result = Gesture.Tap
                            }
                            break
                        }
                        if (change.isConsumed) break
                        total += change.positionChange()
                        if (currentShortcuts != null && total.x > slop && abs(total.x) > abs(total.y) * 1.5f) {
                            result = Gesture.Swipe
                            break
                        }
                        // Moving any other way means the list is scrolling: let it.
                        if (total.getDistance() > slop * 1.5f) break
                    }
                    result
                }

                when (gesture) {
                    Gesture.Tap -> currentLaunch(app)

                    Gesture.Swipe -> {
                        pressJob.cancel()
                        pressed = false
                        var x = total.x
                        var armed = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            x += change.positionChange().x
                            change.consume()
                            val shown = (x.coerceAtLeast(0f) * 0.55f).coerceAtMost(swipeMaxPx)
                            scope.launch { swipeX.snapTo(shown) }
                            val nowArmed = x > swipeTriggerPx
                            if (nowArmed && !armed) feel.swap()
                            armed = nowArmed
                        }
                        if (armed) currentShortcuts?.invoke()
                        scope.launch { swipeX.animateTo(0f, Motion.bouncy) }
                    }

                    Gesture.Cancel -> Unit

                    // Timed out while still holding: a long press.
                    null -> {
                        feel.longPress()
                        pressed = true
                        menuOpen = true
                        val dragCallbacks = currentDrag
                        var dragging = false
                        var pending = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dy = change.positionChange().y
                            change.consume()
                            if (dragCallbacks == null) continue
                            if (!dragging) {
                                pending += dy
                                if (abs(pending) > slop) {
                                    dragging = true
                                    menuOpen = false
                                    dragCallbacks.onStart()
                                    dragCallbacks.onDrag(pending)
                                }
                            } else {
                                dragCallbacks.onDrag(dy)
                            }
                        }
                        if (dragging) dragCallbacks?.onEnd()
                    }
                }
            } finally {
                pressJob.cancel()
                pressed = false
            }
        }
    }

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = swipeX.value
                }
                .clip(RoundedCornerShape(20.dp))
                .drawBehind { drawPressEffect(look, pressFx, touchPoint, seed, word, if (neon >= 0) neonColor(look, neon) else null) }
                .then(gestures)
                .semantics {
                    onClick(label = "Open ${app.label}") {
                        onLaunch(app)
                        true
                    }
                    onLongClick(label = "More options") {
                        menuOpen = true
                        true
                    }
                }
                .padding(horizontal = 8.dp, vertical = if (big) 10.dp else 8.dp)
        ) {
            if (neon >= 0) {
                NeonSign(app, neon, notifications.size)
            } else if (word) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        app.label,
                        style = look.display(30.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (notifications.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(9.dp)
                                .graphicsLayer { rotationZ = 8f }
                                .background(look.accent, RoundedCornerShape(1.5.dp))
                        )
                    }
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    app.icon,
                    contentDescription = null,
                    colorFilter = look.iconFilter,
                    modifier = Modifier.size(if (big) 42.dp else 34.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    app.label,
                    style = look.style(if (big) 22.sp else 17.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (notifications.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    PulseDot(look.accent)
                }
            }
            AnimatedVisibility(
                visible = shortcutsOpen,
                enter = fadeIn(Motion.soft) + expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(Motion.soft) + shrinkVertically(),
            ) {
                ShortcutList(
                    app,
                    indent = if (word) 0.dp else if (big) 56.dp else 48.dp,
                    onUsed = { onToggleShortcuts?.invoke() },
                )
            }
            AnimatedVisibility(
                visible = showNotif,
                enter = fadeIn(Motion.soft) + expandVertically(),
                exit = fadeOut(Motion.soft) + shrinkVertically(),
            ) {
                lastNotif?.let { n ->
                    if (word || neon >= 0) {
                        Text(
                            listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString(" — "),
                            style = look.style(14.sp, color = look.subtext).copy(
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 2.dp)
                                .clickable { NotificationService.open(context, n, app) },
                        )
                        return@let
                    }
                    NotificationLine(
                        preview = n,
                        more = (notifications.size - 1).coerceAtLeast(0),
                        onOpen = { NotificationService.open(context, n, app) },
                        onDismiss = { NotificationService.dismiss(n) },
                    )
                }
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
            shortcutsLabel = if (onToggleShortcuts == null) null else if (shortcutsOpen) "Hide shortcuts" else "Shortcuts",
            onShortcuts = { onToggleShortcuts?.invoke() },
        )
    }
}

fun neonColor(look: Look, index: Int): Color =
    if (look.mono) look.text else NeonColors[index.mod(NeonColors.size)]

/** A glowing tube sign that buzzes on and, every so often, flickers. */
@Composable
private fun NeonSign(app: AppEntry, index: Int, notifCount: Int) {
    val look = LocalLook.current
    val color = neonColor(look, index)
    val flicker = remember { Animatable(1f) }
    LaunchedEffect(app.key) {
        val rnd = kotlin.random.Random(app.key.hashCode())
        while (true) {
            delay(5000L + rnd.nextLong(14000L))
            for (a in floatArrayOf(0.25f, 1f, 0.1f, 0.9f, 0.35f, 1f)) {
                flicker.snapTo(a)
                delay(40L + rnd.nextLong(70L))
            }
        }
    }
    val night = look.mode != ThemeMode.Light
    val tube = if (night) lerp(color, Color.White, 0.35f) else color
    val glow = androidx.compose.ui.graphics.Shadow(color.copy(alpha = if (night) 1f else 0.6f), Offset.Zero, if (night) 32f else 16f)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = if (index % 2 == 0) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            app.label,
            style = look.display(36.sp, color = tube).copy(shadow = glow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .graphicsLayer { alpha = flicker.value },
        )
        if (notifCount > 0) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .drawBehind {
                        drawRoundRect(
                            color.copy(alpha = 0.9f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                            style = Stroke(1.5.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    if (notifCount > 9) "9+" else notifCount.toString(),
                    style = look.style(12.sp, FontWeight.Bold, tube).copy(shadow = glow),
                )
            }
        }
    }
}

/** The long-press menu, shared by rows, bubbles and words. */
@Composable
fun AppMenu(
    app: AppEntry,
    expanded: Boolean,
    onDismiss: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: (String) -> Unit,
    isBookmarked: Boolean,
    onToggleBookmark: ((String) -> Unit)?,
    shortcutsLabel: String?,
    onShortcuts: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
    ) {
        if (shortcutsLabel != null) {
            DropdownMenuItem(text = { Text(shortcutsLabel) }, onClick = { onShortcuts(); onDismiss() })
        }
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Unpin from home" else "Pin to home") },
            onClick = { onToggleFavorite(app.key); onDismiss() },
        )
        if (onToggleBookmark != null) {
            DropdownMenuItem(
                text = { Text(if (isBookmarked) "Remove from bookmarks" else "Add to bookmarks") },
                onClick = { onToggleBookmark(app.key); onDismiss() },
            )
        }
        DropdownMenuItem(
            text = { Text("App info") },
            onClick = { AppRepository.openAppInfo(context, app.packageName); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Uninstall") },
            onClick = { AppRepository.uninstall(context, app.packageName); onDismiss() },
        )
    }
}

/** The app's quick actions, unfolded under the row. */
@Composable
fun ShortcutList(app: AppEntry, indent: Dp, onUsed: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val result by produceState<Shortcuts.Result?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { Shortcuts.load(context, app.packageName) }
    }
    val hint = look.style(13.sp, color = look.subtext)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(look.notifBg)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            .padding(vertical = 4.dp)
    ) {
        when (val r = result) {
            null -> Text("Loading…", style = hint, modifier = Modifier.padding(14.dp))
            Shortcuts.Result.NotDefaultHome -> Text(
                "Android only shares shortcuts with your home screen app. Make Cascade your home screen to see them.",
                style = hint,
                modifier = Modifier.padding(14.dp),
            )
            is Shortcuts.Result.Ok -> {
                if (r.list.isEmpty()) {
                    Text("This app has no shortcuts.", style = hint, modifier = Modifier.padding(14.dp))
                }
                r.list.forEachIndexed { i, s ->
                    CascadeIn(i, 0) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Shortcuts.start(context, s)
                                    onUsed()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (s.icon != null) {
                                Image(s.icon, contentDescription = null, colorFilter = look.iconFilter, modifier = Modifier.size(24.dp))
                            } else {
                                Spacer(Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                s.label,
                                style = look.style(15.sp, FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A small dot that pops in when an app has unread notifications. */
@Composable
private fun PulseDot(color: Color) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, Motion.bouncy) }
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .background(color, CircleShape)
    )
}

@Composable
private fun NotificationLine(
    preview: NotifPreview,
    more: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val look = LocalLook.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(look.notifBg)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (preview.title.isNotBlank()) {
                Text(
                    preview.title,
                    style = look.style(14.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview.text.isNotBlank()) {
                Text(
                    preview.text,
                    style = look.style(13.sp, color = look.subtext),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (more > 0) {
                Text("+$more more", style = look.style(12.sp, FontWeight.Medium, look.accent))
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = look.subtext)
        }
    }
}

/**
 * Wraps a list item so it arrives one beat after the one before: the "cascade" you see every
 * time you come home. Classic floats up, Water drops in and squashes like a droplet,
 * Ink bleeds in from a blur like wet ink.
 */
@Composable
fun CascadeIn(index: Int, trigger: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val kind = LocalLook.current.kind
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        delay(index * if (kind == CascadeStyle.Ink || kind == CascadeStyle.Cosmos) 45L else 32L)
        progress.animateTo(
            1f,
            when (kind) {
                CascadeStyle.Water -> Motion.liquid
                CascadeStyle.Ink -> tween(520, easing = FastOutSlowInEasing)
                CascadeStyle.Classic -> Motion.gentle
                CascadeStyle.Stone -> spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium)
                CascadeStyle.Paper -> tween(520, easing = FastOutSlowInEasing)
                CascadeStyle.Neon -> tween(700, easing = LinearEasing)
                CascadeStyle.Cosmos -> tween(900, easing = FastOutSlowInEasing)
            },
        )
    }
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            when (kind) {
                CascadeStyle.Classic -> translationY = (1f - p) * 48.dp.toPx()
                CascadeStyle.Water -> {
                    translationY = -(1f - p) * 36.dp.toPx()
                    // Overshoot past 1 squashes the row like a landing droplet.
                    val over = (p - 1f).coerceIn(-0.3f, 0.3f)
                    scaleY = 1f - over * 0.9f
                    scaleX = 1f + over * 0.5f
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                CascadeStyle.Ink -> {
                    translationX = -(1f - p) * 10.dp.toPx()
                    val blur = (1f - p) * 14.dp.toPx()
                    renderEffect = if (Build.VERSION.SDK_INT >= 31 && blur > 0.5f) {
                        BlurEffect(blur, blur, TileMode.Decal)
                    } else {
                        null
                    }
                }
                // A stone drops into the sand and settles.
                CascadeStyle.Stone -> {
                    translationY = -(1f - p) * 60.dp.toPx()
                    val over = (p - 1f).coerceIn(-0.3f, 0.3f)
                    scaleY = 1f - over * 0.6f
                    scaleX = 1f + over * 0.3f
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                // A card unfolds toward you.
                CascadeStyle.Paper -> {
                    rotationX = (1f - p) * 85f
                    cameraDistance = 14f * density
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                // A sign stutters on.
                CascadeStyle.Neon -> {
                    alpha = when {
                        p >= 0.85f -> 1f
                        (p * 23f).toInt() % 3 == 0 -> 0.06f
                        else -> 0.25f + p * 0.6f
                    }
                }
                // A star brightens out of nothing.
                CascadeStyle.Cosmos -> {
                    val sc = 0.6f + 0.4f * p
                    scaleX = sc
                    scaleY = sc
                }
            }
        }
    ) { content() }
}

/** The pressed-row effect for each style, drawn behind the row's content. */
private fun DrawScope.drawPressEffect(
    look: Look,
    fx: Float,
    touch: Offset,
    seed: Float,
    word: Boolean = false,
    neonColor: Color? = null,
) {
    if (fx <= 0.001f) return
    if (neonColor != null) {
        drawRoundRect(neonColor.copy(alpha = 0.10f * fx), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))
        drawRoundRect(
            neonColor.copy(alpha = 0.6f * fx),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
            style = Stroke(1.5.dp.toPx()),
        )
        return
    }
    if (word) {
        // A brush underline swept beneath the word.
        val w = size.width * 0.56f
        val h = 16.dp.toPx()
        translate(left = (size.width - w) / 2f, top = size.height - h - 4.dp.toPx()) {
            drawPath(brushPath(w, h, fx, seed), look.accent.copy(alpha = 0.85f))
        }
        return
    }
    when (look.kind) {
        CascadeStyle.Classic -> drawRect(look.pressed.copy(alpha = look.pressed.alpha * fx))
        CascadeStyle.Water -> {
            val maxR = hypot(size.width, size.height)
            val strength = if (look.mode == ThemeMode.Wallpaper) 0.20f else 0.14f
            drawCircle(look.effect.copy(alpha = strength * fx), radius = maxR * fx, center = touch)
            drawCircle(
                look.effect.copy(alpha = 0.35f * (1f - fx)),
                radius = maxR * fx * 0.6f,
                center = touch,
                style = Stroke(1.5.dp.toPx()),
            )
        }
        CascadeStyle.Ink -> {
            val strength = when (look.mode) {
                ThemeMode.Light -> 0.10f
                ThemeMode.Dark -> 0.12f
                ThemeMode.Wallpaper -> 0.18f
            }
            drawPath(brushPath(size.width, size.height, fx, seed), look.effect.copy(alpha = strength))
        }
        CascadeStyle.Stone -> {
            drawRect(look.pressed.copy(alpha = look.pressed.alpha * fx))
            for (k in 0 until 3) {
                drawCircle(
                    look.effect.copy(alpha = 0.4f * fx),
                    radius = (14 + k * 10).dp.toPx() * (0.4f + 0.6f * fx),
                    center = touch,
                    style = Stroke(1.2.dp.toPx()),
                )
            }
        }
        CascadeStyle.Paper -> {
            // The corner folds over.
            val f = min(size.width, size.height) * 0.9f * fx
            val fold = Path().apply {
                moveTo(size.width - f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, f)
                close()
            }
            drawRect(look.pressed.copy(alpha = look.pressed.alpha * fx))
            drawPath(fold, look.text.copy(alpha = 0.10f * fx))
            drawLine(look.text.copy(alpha = 0.25f * fx), Offset(size.width - f, 0f), Offset(size.width, f), 1.dp.toPx())
        }
        CascadeStyle.Neon -> {
            drawRoundRect(look.accent.copy(alpha = 0.12f * fx), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))
        }
        CascadeStyle.Cosmos -> {
            val r = hypot(size.width, size.height) * 0.6f * fx
            drawCircle(
                Brush.radialGradient(listOf(look.effect.copy(alpha = 0.22f * fx), Color.Transparent), touch, r.coerceAtLeast(1f)),
                r.coerceAtLeast(1f),
                touch,
            )
            drawSparkle(touch, 7.dp.toPx() * fx, look.effect.copy(alpha = 0.8f * fx))
        }
    }
}
