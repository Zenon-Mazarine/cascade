package com.bidule.cascade.ui

import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bidule.cascade.AppEntry
import com.bidule.cascade.AppRepository
import com.bidule.cascade.HomeUtils
import com.bidule.cascade.LauncherViewModel
import com.bidule.cascade.NotificationStore
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    homeTick: Int,
    resumeTick: Int,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val apps by vm.apps.collectAsStateWithLifecycle()
    val favoriteKeys by vm.favorites.collectAsStateWithLifecycle()
    val notifications by NotificationStore.state.collectAsStateWithLifecycle()

    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val favorites = remember(appsByKey, favoriteKeys) { favoriteKeys.mapNotNull { appsByKey[it] } }
    val letters = remember(apps) { apps.map { it.letter }.distinct() }
    val isDefaultHome = remember(resumeTick) { HomeUtils.isDefaultHome(context) }

    var panelLetter by remember { mutableStateOf<Char?>(null) }
    var searchOpen by remember { mutableStateOf(false) }

    // Pressing Home while already home closes whatever is open.
    LaunchedEffect(homeTick) {
        panelLetter = null
        searchOpen = false
    }
    // A home screen has nowhere to go back to: swallow Back, or use it to close overlays.
    BackHandler {
        panelLetter = null
        searchOpen = false
    }

    val launch: (AppEntry) -> Unit = { app ->
        panelLetter = null
        searchOpen = false
        AppRepository.launch(context, app)
    }
    val rowFor: @Composable (AppEntry, Boolean) -> Unit = { app, big ->
        AppRow(
            app = app,
            isFavorite = app.key in favoriteKeys,
            notifications = if (big) notifications[app.packageName].orEmpty() else emptyList(),
            big = big,
            onLaunch = launch,
            onToggleFavorite = vm::toggleFavorite,
            onMove = vm::moveFavorite,
        )
    }

    val listAlpha by animateFloatAsState(if (panelLetter != null) 0.2f else 1f, label = "dim")

    Box(Modifier.fillMaxSize().systemBarsPadding()) {

        // Favorites, stacked from the bottom so they sit under your thumb.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(end = RailWidth).alpha(listAlpha),
            contentPadding = PaddingValues(start = 22.dp, end = 8.dp, top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
        ) {
            if (!isDefaultHome) item(key = "setup") { SetupPill(onOpenSettings) }
            item(key = "clock") { ClockHeader(resumeTick, onLongPress = onOpenSettings) }
            item(key = "gap") { Spacer(Modifier.height(18.dp)) }
            if (favorites.isEmpty() && apps.isNotEmpty()) {
                item(key = "hint") {
                    Text(
                        "Slide along the letters on the right, then long-press an app to pin it here.",
                        style = OnWallpaper.hint,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
            items(favorites, key = { it.key }) { app -> rowFor(app, true) }
        }

        // Tap anywhere outside the panel to close it.
        if (panelLetter != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { panelLetter = null }
            )
        }

        AnimatedVisibility(
            visible = panelLetter != null,
            enter = fadeIn() + slideInHorizontally { it / 4 },
            exit = fadeOut() + slideOutHorizontally { it / 4 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = RailWidth + 6.dp, bottom = 28.dp),
        ) {
            // Keep showing the last letter while the panel animates out.
            var shown by remember { mutableStateOf('A') }
            panelLetter?.let { shown = it }
            LetterPanel(
                letter = shown,
                apps = apps.filter { it.letter == shown },
                row = { rowFor(it, false) },
            )
        }

        AlphabetRail(
            letters = letters,
            active = panelLetter,
            onLetter = { c ->
                if (c != panelLetter) {
                    panelLetter = c
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onSearch = { searchOpen = true },
            onSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(RailWidth)
                .fillMaxHeight(0.82f),
        )

        if (searchOpen) {
            SearchOverlay(apps = apps, onLaunch = launch, onClose = { searchOpen = false }, row = { rowFor(it, false) })
        }
    }
}

@Composable
private fun LetterPanel(letter: Char, apps: List<AppEntry>, row: @Composable (AppEntry) -> Unit) {
    Surface(
        modifier = Modifier.width(300.dp).heightIn(max = 460.dp),
        shape = RoundedCornerShape(26.dp),
        color = PanelColor,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Text(
                letter.toString(),
                color = Accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp)) {
                items(apps, key = { it.key }) { row(it) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockHeader(resumeTick: Int, onLongPress: () -> Unit) {
    val context = LocalContext.current
    // Re-keyed on resume because coroutine delays pause while the phone sleeps.
    val now by produceState(LocalDateTime.now(), resumeTick) {
        while (true) {
            value = LocalDateTime.now()
            delay((60 - value.second) * 1000L)
        }
    }
    val locale = Locale.getDefault()
    val time = now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
        .replaceFirstChar { it.titlecase(locale) }

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
            .padding(horizontal = 6.dp)
    ) {
        Text(time, style = OnWallpaper.clock)
        Text(date, style = OnWallpaper.date)
    }
}

@Composable
private fun SetupPill(onClick: () -> Unit) {
    Row(
        Modifier
            .padding(bottom = 12.dp)
            .background(Color(0x99000000), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Preview mode · ", style = OnWallpaper.notifText)
        Text("Set up", style = OnWallpaper.notifTitle.copy(color = Accent))
    }
}

@Composable
private fun SearchOverlay(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onClose: () -> Unit,
    row: @Composable (AppEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(query, apps) {
        val q = AppRepository.normalize(query.trim())
        if (q.isEmpty()) apps
        else apps.filter { it.searchKey.contains(q) }.sortedBy { if (it.searchKey.startsWith(q)) 0 else 1 }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(onLaunch) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                TextButton(onClick = onClose) { Text("Close") }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.key }) { row(it) }
            }
        }
    }
}
