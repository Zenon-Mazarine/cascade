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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.lazy.LazyItemScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
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
    onSwitchStyle: (Offset, CascadeStyle) -> Unit,
) {
    val context = LocalContext.current
    val feel = LocalFeel.current
    val look = LocalLook.current

    val apps by vm.apps.collectAsStateWithLifecycle()
    val favoriteKeys by vm.favorites.collectAsStateWithLifecycle()
    val bookmarkKeys by vm.bookmarks.collectAsStateWithLifecycle()
    val notifications by NotificationStore.state.collectAsStateWithLifecycle()
    val launchCounts by vm.launchCounts.collectAsStateWithLifecycle()

    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val favorites = remember(appsByKey, favoriteKeys) { favoriteKeys.mapNotNull { appsByKey[it] } }
    val bookmarked = remember(appsByKey, bookmarkKeys) { bookmarkKeys.mapNotNull { appsByKey[it] } }
    val letters = remember(apps) { apps.map { it.letter }.distinct() }
    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }
    val groups = remember(letters) { indexGroups(letters) }
    val isDefaultHome = remember(resumeTick) { com.bidule.cascade.DebugFlags.clean || HomeUtils.isDefaultHome(context) }

    var panelLetter by remember { mutableStateOf<Char?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var folderOpen by remember { mutableStateOf(false) }
    var folderEditing by remember { mutableStateOf(false) }
    var folderOpens by remember { mutableIntStateOf(0) }
    var pickerOpen by remember { mutableStateOf(false) }
    /** Which row has its shortcuts unfolded: "fav:<key>" or "list:<key>". */
    var shortcutsFor by remember { mutableStateOf<String?>(null) }
    /** Water: the all-apps sheet. Ink: which thumb-index page is open. Both: the shortcut card. */
    var allAppsOpen by remember { mutableStateOf(false) }
    var indexGroup by remember { mutableStateOf<Int?>(null) }
    var shortcutCardFor by remember { mutableStateOf<AppEntry?>(null) }
    var stylePickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (com.bidule.cascade.DebugFlags.openStylePicker) {
            com.bidule.cascade.DebugFlags.openStylePicker = false
            stylePickerOpen = true
        }
    }

    // Drag-to-reorder state for the favorites list.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragActive by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val latestFavoriteKeys by rememberUpdatedState(favoriteKeys)

    fun closeAll() {
        panelLetter = null
        searchOpen = false
        folderOpen = false
        folderEditing = false
        pickerOpen = false
        shortcutsFor = null
        allAppsOpen = false
        indexGroup = null
        shortcutCardFor = null
        stylePickerOpen = false
    }

    fun dragCallbacks(key: String) = DragCallbacks(
        onStart = {
            shortcutsFor = null
            draggingKey = key
            dragActive = true
            dragOffset = 0f
        },
        onDrag = onDrag@{ dy ->
            if (draggingKey != key) return@onDrag
            dragOffset += dy
            val items = listState.layoutInfo.visibleItemsInfo
            val current = items.firstOrNull { it.key == key } ?: return@onDrag
            val center = current.offset + dragOffset + current.size / 2f
            val target = items.firstOrNull {
                it.key != key && it.key in latestFavoriteKeys &&
                    center > it.offset && center < it.offset + it.size
            } ?: return@onDrag
            val toIndex = latestFavoriteKeys.indexOf(target.key as String)
            vm.moveFavoriteTo(key, toIndex)
            // Keep the dragged row under the finger now that its slot moved.
            dragOffset += current.offset - target.offset
            feel.swap()
        },
        onEnd = {
            dragActive = false
            scope.launch {
                animate(dragOffset, 0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                    dragOffset = v
                }
                if (!dragActive) draggingKey = null
            }
        },
    )

    // Pressing Home while already home closes whatever is open.
    LaunchedEffect(homeTick) { if (homeTick > 0) closeAll() }
    // A home screen has nowhere to go back to: swallow Back, or use it to close overlays.
    BackHandler {
        when {
            pickerOpen -> pickerOpen = false
            folderEditing -> folderEditing = false
            else -> closeAll()
        }
    }

    val launch: (AppEntry) -> Unit = { app ->
        feel.launch()
        vm.recordLaunch(app.key)
        closeAll()
        AppRepository.launch(context, app)
    }
    val rowFor: @Composable (AppEntry, Boolean) -> Unit = { app, big ->
        val shortcutId = (if (big) "fav:" else "list:") + app.key
        AppRow(
            app = app,
            isFavorite = app.key in favoriteKeys,
            notifications = if (big) notifications[app.packageName].orEmpty() else emptyList(),
            big = big,
            onLaunch = launch,
            onToggleFavorite = vm::toggleFavorite,
            shortcutsOpen = shortcutsFor == shortcutId,
            onToggleShortcuts = { shortcutsFor = if (shortcutsFor == shortcutId) null else shortcutId },
            drag = if (big) dragCallbacks(app.key) else null,
            word = big && look.kind == CascadeStyle.Ink,
            neon = if (big && look.kind == CascadeStyle.Neon) favorites.indexOf(app) else -1,
            isBookmarked = app.key in bookmarkKeys,
            onToggleBookmark = vm::toggleBookmark,
        )
    }

    // Swipe down on the home screen to open the notification shade.
    val pullThreshold = with(LocalDensity.current) { 90.dp.toPx() }
    val pullDown = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            var lifted = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f) {
                    pulled += available.y
                    if (pulled > pullThreshold) {
                        pulled = Float.NEGATIVE_INFINITY // once per gesture
                        HomeUtils.expandNotifications(context)
                    }
                } else if (available.y < 0f) {
                    // Pushing past the bottom of the list: bring up every app.
                    lifted -= available.y
                    if (lifted > pullThreshold) {
                        lifted = Float.NEGATIVE_INFINITY
                        feel.open()
                        allAppsOpen = true
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                lifted = 0f
                return Velocity.Zero
            }
        }
    }

    val panelOpen = panelLetter != null
    val anyOverlay = panelOpen || searchOpen || folderOpen || pickerOpen ||
        allAppsOpen || indexGroup != null || shortcutCardFor != null || stylePickerOpen
    val listAlpha by animateFloatAsState(if (anyOverlay) 0.25f else 1f, Motion.soft, label = "dim")
    val listBlur by animateDpAsState(
        if (anyOverlay) 14.dp else 0.dp,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "blur",
    )

    val touchMarks = remember(scope) { TouchMarks(scope) }

    val openStylePicker: (Offset) -> Unit = {
        closeAll()
        feel.open()
        stylePickerOpen = true
    }

    val toggleFolder: () -> Unit = {
        panelLetter = null
        indexGroup = null
        if (folderOpen) {
            folderOpen = false
            folderEditing = false
        } else {
            folderOpens++
            folderOpen = true
            feel.open()
        }
    }

    // One favorite in a list (Classic rows or Ink words), with drag-to-reorder lift.
    val favoriteItem: @Composable LazyItemScope.(Int, AppEntry) -> Unit = { index, app ->
        val isDragged = app.key == draggingKey
        val lift by animateFloatAsState(if (isDragged && dragActive) 1f else 0f, Motion.bouncy, label = "lift")
        val itemModifier = if (isDragged) {
            Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationY = dragOffset
                    val s = 1f + 0.04f * lift
                    scaleX = s
                    scaleY = s
                }
                .background(look.panel.copy(alpha = 0.9f * lift), RoundedCornerShape(20.dp))
        } else {
            Modifier.animateItem()
        }
        CascadeIn(index + 1, resumeTick, itemModifier) { rowFor(app, true) }
    }
    val bodyModifier = Modifier.fillMaxSize().alpha(listAlpha).blur(listBlur)

    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .observeTouches(touchMarks, look.kind)
    ) {

        when (look.kind) {
            CascadeStyle.Classic -> {
            // Favorites, stacked from the bottom so they sit under your thumb.
            LazyColumn(
                state = listState,
                modifier = bodyModifier
                    .padding(end = RailWidth)
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
                    CascadeIn(0, resumeTick) { ClockHeader(resumeTick, onLongPress = onOpenSettings, onSwitchStyle = openStylePicker) }
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
                itemsIndexed(favorites, key = { _, app -> app.key }) { index, app -> favoriteItem(index, app) }
            }
            }

            // ── Water: the Pool. Stone: the garden. Cosmos: the constellation. ──
            CascadeStyle.Water, CascadeStyle.Stone, CascadeStyle.Cosmos -> PoolHome(
                favorites = favorites,
                appsByPackage = appsByPackage,
                notifications = notifications,
                launchCounts = launchCounts,
                favoriteKeys = favoriteKeys,
                bookmarkKeys = bookmarkKeys,
                resumeTick = resumeTick,
                header = {
                    Column {
                        TopBar(onSearch = { searchOpen = true }, onBookmarks = toggleFolder, onSettings = onOpenSettings)
                        if (!isDefaultHome) Box(Modifier.padding(horizontal = 20.dp)) { SetupPill(onOpenSettings) }
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            CascadeIn(0, resumeTick) {
                                ClockHeader(resumeTick, onLongPress = onOpenSettings, onSwitchStyle = openStylePicker)
                            }
                        }
                    }
                },
                onLaunch = launch,
                onToggleFavorite = vm::toggleFavorite,
                onToggleBookmark = vm::toggleBookmark,
                onShortcuts = { shortcutCardFor = it },
                onPullUp = {
                    feel.open()
                    allAppsOpen = true
                },
                onPullDown = { HomeUtils.expandNotifications(context) },
                modifier = bodyModifier,
            )

            // ── Paper: folded cards ──
            CascadeStyle.Paper -> PaperHome(
                favorites = favorites,
                notifications = notifications,
                favoriteKeys = favoriteKeys,
                bookmarkKeys = bookmarkKeys,
                resumeTick = resumeTick,
                pull = pullDown,
                header = {
                    Column {
                        TopBar(onSearch = { searchOpen = true }, onBookmarks = toggleFolder, onSettings = onOpenSettings)
                        if (!isDefaultHome) Box(Modifier.padding(horizontal = 20.dp)) { SetupPill(onOpenSettings) }
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            CascadeIn(0, resumeTick) {
                                ClockHeader(resumeTick, onLongPress = onOpenSettings, onSwitchStyle = openStylePicker)
                            }
                        }
                    }
                },
                onLaunch = launch,
                onToggleFavorite = vm::toggleFavorite,
                onToggleBookmark = vm::toggleBookmark,
                onShortcuts = { shortcutCardFor = it },
                modifier = bodyModifier,
            )

            // ── Neon: a street of glowing signs ──
            CascadeStyle.Neon -> Column(bodyModifier) {
                TopBar(
                    onSearch = { searchOpen = true },
                    onBookmarks = toggleFolder,
                    onSettings = onOpenSettings,
                    trailing = { StyleOrb(onSwitch = openStylePicker, size = 40.dp) },
                )
                if (!isDefaultHome) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SetupPill(onOpenSettings) }
                }
                NeonHeader(resumeTick, onLongPress = onOpenSettings)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .nestedScroll(pullDown),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                ) {
                    if (favorites.isEmpty() && apps.isNotEmpty()) {
                        item(key = "hint") {
                            Text(
                                "Pull up for all apps, then long-press one and choose “Pin to home” to light up a sign.",
                                style = look.style(15.sp, color = look.subtext),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                    itemsIndexed(favorites, key = { _, app -> app.key }) { index, app -> favoriteItem(index, app) }
                }
            }

            // ── Ink: the Scroll ──
            CascadeStyle.Ink -> Column(bodyModifier) {
                TopBar(
                    onSearch = { searchOpen = true },
                    onBookmarks = toggleFolder,
                    onSettings = onOpenSettings,
                    trailing = { StyleOrb(onSwitch = openStylePicker, size = 40.dp) },
                )
                if (!isDefaultHome) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SetupPill(onOpenSettings) }
                }
                CascadeIn(0, resumeTick) { InkHeader(resumeTick, onLongPress = onOpenSettings) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = TabWidth, end = TabWidth)
                        .nestedScroll(pullDown),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
                ) {
                    if (favorites.isEmpty() && apps.isNotEmpty()) {
                        item(key = "hint") {
                            Text(
                                "Open a tab on the right edge, then long-press an app to write it onto this page.",
                                style = look.style(15.sp, color = look.subtext).copy(fontStyle = FontStyle.Italic),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                    itemsIndexed(favorites, key = { _, app -> app.key }) { index, app -> favoriteItem(index, app) }
                }
            }
        }

        // Ripples / ink drops where you touch.
        TouchMarksLayer(touchMarks)

        // Tap anywhere outside the panel to close it.
        if (panelOpen || folderOpen || indexGroup != null || shortcutCardFor != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        panelLetter = null
                        folderOpen = false
                        folderEditing = false
                        indexGroup = null
                        shortcutCardFor = null
                    }
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

        AnimatedVisibility(
            visible = folderOpen,
            enter = fadeIn(Motion.soft) +
                scaleIn(Motion.bouncy, initialScale = 0.8f, transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut(tween(160)) +
                scaleOut(tween(180), targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 1f)),
            modifier = Modifier
                .align(if (look.kind == CascadeStyle.Classic) Alignment.BottomEnd else Alignment.TopStart)
                .padding(
                    start = if (look.kind == CascadeStyle.Classic) 0.dp else 12.dp,
                    top = if (look.kind == CascadeStyle.Classic) 0.dp else 52.dp,
                    end = if (look.kind == CascadeStyle.Classic) RailWidth + 10.dp else 0.dp,
                    bottom = if (look.kind == CascadeStyle.Classic) 28.dp else 0.dp,
                ),
        ) {
            BookmarksPanel(
                apps = bookmarked,
                editing = folderEditing,
                openCount = folderOpens,
                onToggleEdit = {
                    feel.toggle()
                    folderEditing = !folderEditing
                },
                onLaunch = launch,
                onRemove = vm::toggleBookmark,
                onAdd = { pickerOpen = true },
            )
        }

        if (look.kind == CascadeStyle.Classic) {
            AlphabetRail(
                letters = letters,
                active = panelLetter,
                onLetter = { c ->
                    folderOpen = false
                    folderEditing = false
                    if (c != panelLetter) {
                        if (panelLetter == null) feel.open()
                        panelLetter = c
                        feel.letter(letters.indexOf(c), letters.size)
                    }
                },
                onSearch = { searchOpen = true },
                onSettings = onOpenSettings,
                onBookmarks = toggleFolder,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(RailWidth)
                    .fillMaxHeight(0.82f),
            )
        }

        // ── Ink: thumb index tabs and their pages ──
        if (look.kind == CascadeStyle.Ink) {
            AnimatedVisibility(
                visible = indexGroup != null,
                enter = fadeIn(tween(160)) + slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it / 3 },
                exit = fadeOut(tween(160)) + slideOutHorizontally(tween(200)) { it / 4 },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = TabWidth - 2.dp),
            ) {
                var shownGroup by remember { mutableIntStateOf(0) }
                indexGroup?.let { shownGroup = it }
                IndexPage(groupIndex = shownGroup, groups = groups, apps = apps, row = { rowFor(it, false) })
            }
            ThumbIndex(
                groups = groups,
                active = indexGroup,
                onGroup = { g ->
                    folderOpen = false
                    folderEditing = false
                    if (g != indexGroup) {
                        if (indexGroup == null) feel.open()
                        indexGroup = g
                        feel.letter(g, groups.size)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.72f),
            )
        }

        // ── Water: every app, rising from the bottom ──
        AnimatedVisibility(
            visible = allAppsOpen,
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(260)) { it / 2 },
        ) {
            AllAppsSheet(apps = apps, row = { rowFor(it, false) }, onClose = { allAppsOpen = false })
        }

        // The style picker, from the style button.
        AnimatedVisibility(
            visible = stylePickerOpen,
            enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 3 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 3 },
        ) {
            StylePickerSheet(
                onPick = { origin, st ->
                    stylePickerOpen = false
                    if (st != look.kind) onSwitchStyle(origin, st)
                },
                onOriginal = {
                    stylePickerOpen = false
                    HomeUtils.restoreOriginal(context)
                },
                onClose = { stylePickerOpen = false },
            )
        }

        // Shortcuts for a bubble or card as a floating card.
        AnimatedVisibility(
            visible = shortcutCardFor != null,
            enter = fadeIn(Motion.soft) + scaleIn(Motion.bouncy, initialScale = 0.85f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.92f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            var shownApp by remember { mutableStateOf<AppEntry?>(null) }
            shortcutCardFor?.let { shownApp = it }
            shownApp?.let { ShortcutCard(it, onDone = { shortcutCardFor = null }) }
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(Motion.soft) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 6 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 8 },
        ) {
            SearchOverlay(apps = apps, onLaunch = launch, onClose = { searchOpen = false }, row = { rowFor(it, false) })
        }

        AnimatedVisibility(
            visible = pickerOpen,
            enter = fadeIn(Motion.soft) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 6 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 8 },
        ) {
            BookmarkPicker(
                apps = apps,
                selected = bookmarkKeys,
                onToggle = {
                    feel.toggle()
                    vm.toggleBookmark(it)
                },
                onDone = {
                    pickerOpen = false
                    folderOpens++
                },
            )
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
                LetterBadge(l, Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
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
private fun ClockHeader(resumeTick: Int, onLongPress: () -> Unit, onSwitchStyle: (Offset) -> Unit) {
    val context = LocalContext.current
    val look = LocalLook.current
    // Re-keyed on resume because coroutine delays pause while the phone sleeps.
    val now by produceState(com.bidule.cascade.AppClock.now(), resumeTick) {
        while (true) {
            value = com.bidule.cascade.AppClock.now()
            delay((60 - value.second) * 1000L)
        }
    }
    val locale = Locale.getDefault()
    val time = now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
        .replaceFirstChar { it.titlecase(locale) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
    Column(
        Modifier
            .weight(1f)
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
            Text(t, style = look.display(72.sp))
        }
        Text(date, style = look.style(17.sp, color = look.subtext))
    }
    // One tap to change the whole personality of the launcher.
    StyleOrb(onSwitch = onSwitchStyle, modifier = Modifier.padding(bottom = 6.dp, end = 4.dp))
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
