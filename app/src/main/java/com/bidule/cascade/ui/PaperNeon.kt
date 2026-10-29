package com.bidule.cascade.ui

import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bidule.cascade.AppEntry
import com.bidule.cascade.AppRepository
import com.bidule.cascade.NotifPreview
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Paper: favorites as folded cards on a creased sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PaperHome(
    favorites: List<AppEntry>,
    notifications: Map<String, List<NotifPreview>>,
    favoriteKeys: List<String>,
    bookmarkKeys: List<String>,
    resumeTick: Int,
    pull: NestedScrollConnection,
    header: @Composable () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onShortcuts: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalLook.current
    Column(modifier) {
        header()
        if (favorites.isEmpty()) {
            Text(
                "Pull up for all apps, then long-press one and choose “Pin to home” to fold a card for it.",
                style = look.style(14.sp, color = look.subtext),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(pull),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(favorites, key = { _, a -> a.key }) { i, app ->
                CascadeIn(i, resumeTick, Modifier.animateItem()) {
                    PaperCard(
                        app = app,
                        notifCount = notifications[app.packageName]?.size ?: 0,
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
}

@Composable
private fun PaperCard(
    app: AppEntry,
    notifCount: Int,
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
    val fold by animateFloatAsState(if (pressed || menuOpen) 1f else 0f, tween(220), label = "fold")
    val tilt = remember(app.key) { ((app.key.hashCode() and 0xFF) / 255f - 0.5f) * 3f }
    val paper = look.panel.copy(alpha = 1f)
    val ink = look.text

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .graphicsLayer {
                rotationZ = tilt * (1f - fold)
                rotationY = -fold * 16f
                cameraDistance = 14f * density
            }
            .drawBehind {
                val corner = 20.dp.toPx() * (1f + fold * 0.6f)
                val w = size.width
                val h = size.height
                // Shadow.
                drawRect(Color.Black.copy(alpha = 0.12f), Offset(3.dp.toPx(), 4.dp.toPx()), Size(w, h))
                // The card, minus its folded corner.
                val body = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w - corner, 0f)
                    lineTo(w, corner)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(body, paper)
                // A faint diagonal crease, as if the card was once folded in half.
                drawLine(ink.copy(alpha = 0.06f), Offset(0f, h), Offset(w - corner, 0f), 1.dp.toPx())
                // The folded-over corner.
                val flap = Path().apply {
                    moveTo(w - corner, 0f)
                    lineTo(w - corner, corner)
                    lineTo(w, corner)
                    close()
                }
                drawPath(flap, if (notifCount > 0) look.accent else ink.copy(alpha = 0.10f))
                drawPath(flap, ink.copy(alpha = 0.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
            Image(app.icon, contentDescription = app.label, colorFilter = look.iconFilter, modifier = Modifier.size(38.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                app.label,
                style = look.style(12.sp, color = ink).copy(shadow = null),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (notifCount > 0) {
            Text(
                if (notifCount > 9) "9+" else notifCount.toString(),
                style = look.style(11.sp, FontWeight.Bold, Color.White).copy(shadow = null),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 5.dp),
            )
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Neon: the time as a glowing sign that buzzes on
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NeonHeader(resumeTick: Int, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLook.current
    val now by produceState(com.bidule.cascade.AppClock.now(), resumeTick) {
        while (true) {
            value = com.bidule.cascade.AppClock.now()
            delay((60 - value.second) * 1000L)
        }
    }
    val locale = Locale.getDefault()
    val time = now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale))
    val date = now.format(DateTimeFormatter.ofPattern("EEE · d MMM", locale)).uppercase(locale)

    // Power-on stutter every time you come home.
    val power = remember { Animatable(0f) }
    LaunchedEffect(resumeTick) {
        for (a in floatArrayOf(0.1f, 0.8f, 0.05f, 0.6f, 0.2f, 1f)) {
            power.snapTo(a)
            delay(60)
        }
    }
    val night = look.mode != ThemeMode.Light
    val color = look.accent
    val tube = if (night) androidx.compose.ui.graphics.lerp(color, Color.White, 0.4f) else color

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
            )
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            time,
            style = look.display(76.sp, color = tube).copy(shadow = Shadow(color, Offset.Zero, if (night) 48f else 20f)),
            modifier = Modifier.graphicsLayer { alpha = power.value },
        )
        Text(
            date,
            style = look.style(14.sp, FontWeight.Bold, look.effect).copy(
                letterSpacing = 4.sp,
                shadow = Shadow(look.effect, Offset.Zero, if (night) 20f else 8f),
            ),
        )
    }
}
