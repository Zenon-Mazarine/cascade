package com.bidule.cascade.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
) {
    val look = LocalLook.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val swipeX = remember { Animatable(0f) }

    val currentLaunch by rememberUpdatedState(onLaunch)
    val currentShortcuts by rememberUpdatedState(onToggleShortcuts)
    val currentDrag by rememberUpdatedState(drag)

    val swipeTriggerPx = with(density) { 56.dp.toPx() }
    val swipeMaxPx = with(density) { 90.dp.toPx() }

    val scale by animateFloatAsState(if (pressed || menuOpen) 0.95f else 1f, Motion.bouncy, label = "press")
    val pressBg by animateColorAsState(
        if (pressed || menuOpen || shortcutsOpen) look.pressed else Color.Transparent,
        label = "pressBg",
    )

    // Keep the last notification on screen while it folds away.
    var lastNotif by remember { mutableStateOf<NotifPreview?>(null) }
    notifications.firstOrNull()?.let { lastNotif = it }
    val showNotif = big && notifications.isNotEmpty()

    val gestures = Modifier.pointerInput(app.key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
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
                            if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            armed = nowArmed
                        }
                        if (armed) currentShortcuts?.invoke()
                        scope.launch { swipeX.animateTo(0f, Motion.bouncy) }
                    }

                    Gesture.Cancel -> Unit

                    // Timed out while still holding: a long press.
                    null -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                .background(pressBg)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                ShortcutList(app, big, onUsed = { onToggleShortcuts?.invoke() })
            }
            AnimatedVisibility(
                visible = showNotif,
                enter = fadeIn(Motion.soft) + expandVertically(),
                exit = fadeOut(Motion.soft) + shrinkVertically(),
            ) {
                lastNotif?.let { n ->
                    NotificationLine(
                        preview = n,
                        more = (notifications.size - 1).coerceAtLeast(0),
                        onOpen = { NotificationService.open(context, n, app) },
                        onDismiss = { NotificationService.dismiss(n) },
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(18.dp),
        ) {
            if (onToggleShortcuts != null) {
                DropdownMenuItem(
                    text = { Text(if (shortcutsOpen) "Hide shortcuts" else "Shortcuts") },
                    onClick = { onToggleShortcuts(); menuOpen = false },
                )
            }
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Unpin from home" else "Pin to home") },
                onClick = { onToggleFavorite(app.key); menuOpen = false },
            )
            if (onToggleBookmark != null) {
                DropdownMenuItem(
                    text = { Text(if (isBookmarked) "Remove from bookmarks" else "Add to bookmarks") },
                    onClick = { onToggleBookmark(app.key); menuOpen = false },
                )
            }
            DropdownMenuItem(
                text = { Text("App info") },
                onClick = { AppRepository.openAppInfo(context, app.packageName); menuOpen = false },
            )
            DropdownMenuItem(
                text = { Text("Uninstall") },
                onClick = { AppRepository.uninstall(context, app.packageName); menuOpen = false },
            )
        }
    }
}

/** The app's quick actions, unfolded under the row. */
@Composable
private fun ShortcutList(app: AppEntry, big: Boolean, onUsed: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val result by produceState<Shortcuts.Result?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { Shortcuts.load(context, app.packageName) }
    }
    val hint = look.style(13.sp, color = look.subtext)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (big) 56.dp else 48.dp, top = 8.dp)
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
 * Wraps a list item so it floats up into place, each one a beat after the one before:
 * the "cascade" you see every time you come home.
 */
@Composable
fun CascadeIn(index: Int, trigger: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        delay(index * 32L)
        progress.animateTo(1f, Motion.gentle)
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 48.dp.toPx()
        }
    ) { content() }
}
