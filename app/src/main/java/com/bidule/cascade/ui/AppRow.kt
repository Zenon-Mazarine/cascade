package com.bidule.cascade.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bidule.cascade.AppEntry
import com.bidule.cascade.AppRepository
import com.bidule.cascade.NotifPreview
import com.bidule.cascade.NotificationService
import kotlinx.coroutines.delay

/**
 * One app in a list. Tap to open, long-press for pin / reorder / info / uninstall.
 * Squishes slightly under your finger. In the favorites list ([big] = true) its latest
 * notification unfolds underneath.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppEntry,
    isFavorite: Boolean,
    notifications: List<NotifPreview>,
    big: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMove: ((String, Int) -> Unit)? = null,
    isBookmarked: Boolean = false,
    onToggleBookmark: ((String) -> Unit)? = null,
) {
    val look = LocalLook.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed || menuOpen) 0.95f else 1f, Motion.bouncy, label = "press")
    val pressBg by animateColorAsState(
        if (pressed || menuOpen) look.pressed else Color.Transparent,
        label = "pressBg",
    )

    // Keep the last notification on screen while it folds away.
    var lastNotif by remember { mutableStateOf<NotifPreview?>(null) }
    notifications.firstOrNull()?.let { lastNotif = it }
    val showNotif = big && notifications.isNotEmpty()

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(20.dp))
                .background(pressBg)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { onLaunch(app) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
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
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Unpin from home" else "Pin to home") },
                onClick = { onToggleFavorite(app.key); menuOpen = false },
            )
            if (isFavorite && onMove != null) {
                DropdownMenuItem(text = { Text("Move up") }, onClick = { onMove(app.key, -1); menuOpen = false })
                DropdownMenuItem(text = { Text("Move down") }, onClick = { onMove(app.key, 1); menuOpen = false })
            }
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
