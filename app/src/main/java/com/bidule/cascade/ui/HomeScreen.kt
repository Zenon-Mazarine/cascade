package com.bidule.cascade.ui

import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
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
    val look = LocalLook.current

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

    // Swipe down on the home screen to open the notification shade.
    val pullThreshold = with(LocalDensity.current) { 90.dp.toPx() }
    val pullDown = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > pullThreshold) {
                        pulled = Float.NEGATIVE_INFINITY // once per gesture
                        HomeUtils.expandNotifications(context)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    val panelOpen = panelLetter != null
    val listAlpha by animateFloatAsState(if (panelOpen || searchOpen) 0.25f else 1f, Motion.soft, label = "dim")
    val listBlur by animateDpAsState(
        if (panelOpen || searchOpen) 14.dp else 0.dp,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "blur",
    )

    Box(Modifier.fillMaxSize().systemBarsPadding()) {

        // Favorites, stacked from the bottom so they sit under your thumb.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = RailWidth)
                .alpha(listAlpha)
                .blur(listBlur)
                .nestedScroll(pullDown),
            contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
        ) {
            if (!isDefaultHome) {
                item(key = "setup") {
                    CascadeIn(0, resumeTick) { SetupPill(onOpenSettings) }
                }
            }
            item(key = "clock") {
                CascadeIn(0, resumeTick) { ClockHeader(resumeTick, onLongPress = onOpenSettings) }
            }
            item(key = "gap") { Spacer(Modifier.height(22.dp)) }
            if (favorites.isEmpty() && apps.isNotEmpty()) {
                item(key = "hint") {
                    Text(
                        "Slide along the letters on the right, then long-press an app to pin it here.",
                        style = look.style(15.sp, color = look.subtext),
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            itemsIndexed(favorites, key = { _, app -> app.key }) { index, app ->
                CascadeIn(index + 1, resumeTick, Modifier.animateItem()) { rowFor(app, true) }
            }
        }

        // Tap anywhere outside the panel to close it.
        if (panelOpen) {
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
            visible = panelOpen,
            enter = fadeIn(Motion.soft) +
                scaleIn(Motion.bouncy, initialScale = 0.82f, transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut(tween(160)) +
                scaleOut(tween(180), targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 1f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = RailWidth + 10.dp, bottom = 28.dp),
        ) {
            // Keep showing the last letter while the panel animates out.
            var shown by remember { mutableStateOf('A') }
            panelLetter?.let { shown = it }
            LetterPanel(letter = shown, apps = apps, row = { rowFor(it, false) })
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

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(Motion.soft) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 6 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 8 },
        ) {
            SearchOverlay(apps = apps, onLaunch = launch, onClose = { searchOpen = false }, row = { rowFor(it, false) })
        }
    }
}

@Composable
private fun LetterPanel(letter: Char, apps: List<AppEntry>, row: @Composable (AppEntry) -> Unit) {
    val look = LocalLook.current
    Surface(
        modifier = Modifier.width(300.dp).heightIn(max = 470.dp),
        shape = RoundedCornerShape(28.dp),
        color = look.panel,
        shadowElevation = 16.dp,
    ) {
        // Sliding between letters: forward slides up, backward slides down; the panel resizes smoothly.
        AnimatedContent(
            targetState = letter,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)) { h -> if (forward) h / 6 else -h / 6 } +
                    fadeIn(tween(180))) togetherWith
                    (slideOutVertically(tween(160)) { h -> if (forward) -h / 8 else h / 8 } + fadeOut(tween(120))) using
                    SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow) }
            },
            label = "letter",
        ) { l ->
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    l.toString(),
                    style = look.style(30.sp, FontWeight.Bold, look.accent).copy(shadow = null),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
                )
                LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp)) {
                    itemsIndexed(apps.filter { it.letter == l }, key = { _, a -> a.key }) { i, app ->
                        CascadeIn(i, l.code) { row(app) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockHeader(resumeTick: Int, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val look = LocalLook.current
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
            .padding(horizontal = 8.dp)
    ) {
        AnimatedContent(
            targetState = time,
            transitionSpec = {
                (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn()) togetherWith
                    (slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeOut())
            },
            label = "clock",
        ) { t ->
            Text(t, style = look.style(70.sp, FontWeight.Light))
        }
        Text(date, style = look.style(17.sp, color = look.subtext))
    }
}

@Composable
private fun SetupPill(onClick: () -> Unit) {
    val look = LocalLook.current
    Row(
        Modifier
            .padding(bottom = 14.dp)
            .background(look.notifBg, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Preview mode · ", style = look.style(13.sp, color = look.subtext))
        Text("Set up", style = look.style(13.sp, FontWeight.SemiBold, look.accent))
    }
}

@Composable
private fun SearchOverlay(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onClose: () -> Unit,
    row: @Composable (AppEntry) -> Unit,
) {
    val look = LocalLook.current
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
            .background(look.overlay)
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(onLaunch) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                TextButton(onClick = onClose) { Text("Close") }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.key }) { app ->
                    Box(Modifier.animateItem()) { row(app) }
                }
            }
        }
    }
}
