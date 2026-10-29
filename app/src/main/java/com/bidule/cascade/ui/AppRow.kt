package com.bidule.cascade.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bidule.cascade.AppEntry
import com.bidule.cascade.AppRepository
import com.bidule.cascade.NotifPreview
import com.bidule.cascade.NotificationService

/**
 * One app in a list. Tap to open, long-press for pin / reorder / info / uninstall.
 * In the favorites list ([big] = true) its latest notification is shown inline underneath.
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
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { onLaunch(app) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(horizontal = 6.dp, vertical = if (big) 9.dp else 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(if (big) 40.dp else 34.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    app.label,
                    style = if (big) OnWallpaper.label else OnWallpaper.label.copy(fontSize = 17.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (notifications.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(7.dp).background(Accent, CircleShape))
                }
            }
            if (big && notifications.isNotEmpty()) {
                NotificationLine(
                    preview = notifications.first(),
                    more = notifications.size - 1,
                    onOpen = { NotificationService.open(context, notifications.first(), app) },
                    onDismiss = { NotificationService.dismiss(notifications.first()) },
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Unpin from home" else "Pin to home") },
                onClick = { onToggleFavorite(app.key); menuOpen = false },
            )
            if (isFavorite && onMove != null) {
                DropdownMenuItem(text = { Text("Move up") }, onClick = { onMove(app.key, -1); menuOpen = false })
                DropdownMenuItem(text = { Text("Move down") }, onClick = { onMove(app.key, 1); menuOpen = false })
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

@Composable
private fun NotificationLine(
    preview: NotifPreview,
    more: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33000000))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (preview.title.isNotBlank()) {
                Text(preview.title, style = OnWallpaper.notifTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (preview.text.isNotBlank()) {
                Text(preview.text, style = OnWallpaper.notifText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (more > 0) {
                Text("+$more more", style = OnWallpaper.notifText.copy(color = Accent, fontSize = 12.sp))
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.7f))
        }
    }
}
