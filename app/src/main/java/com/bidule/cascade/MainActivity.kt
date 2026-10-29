package com.bidule.cascade

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidule.cascade.ui.CascadeStyle
import com.bidule.cascade.ui.Onboarding
import com.bidule.cascade.ui.Paywall
import com.bidule.cascade.ui.isPro
import com.bidule.cascade.ui.CascadeTheme
import com.bidule.cascade.ui.Feel
import com.bidule.cascade.ui.Haptics
import com.bidule.cascade.ui.LocalFeel
import com.bidule.cascade.ui.StyleBackdrop
import com.bidule.cascade.ui.StyleSwitch
import com.bidule.cascade.ui.StyleSwitchLayer
import com.bidule.cascade.ui.HomeScreen
import com.bidule.cascade.ui.LocalLook
import com.bidule.cascade.ui.SettingsScreen
import com.bidule.cascade.ui.lookFor

class MainActivity : ComponentActivity() {

    /** Bumped each time the Home button is pressed while Cascade is already showing. */
    private val homeTicks = mutableIntStateOf(0)

    /** Bumped on every resume, so screens can re-check settings and replay the entrance. */
    private val resumeTicks = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        applyDebugExtras(intent)
        Sounds.init(this)
        Pro.init(this)
        val haptics = Haptics(this)
        setContent {
            val vm: LauncherViewModel = viewModel()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val mono by vm.mono.collectAsStateWithLifecycle()
            val chosenStyle by vm.style.collectAsStateWithLifecycle()
            val proOwned by Pro.owned.collectAsStateWithLifecycle()
            val trialUntil by vm.trialUntil.collectAsStateWithLifecycle()
            // A Pro style only shows if it's owned or being previewed; otherwise the last free style.
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(trialUntil, resumeTicks.intValue) {
                while (true) {
                    now = System.currentTimeMillis()
                    if (trialUntil <= now) break
                    delay((trialUntil - now).coerceAtMost(1000L))
                }
            }
            val locked = chosenStyle.isPro && !proOwned && now >= trialUntil
            val style = if (locked) vm.lastFreeStyle else chosenStyle
            val soundOn by vm.sound.collectAsStateWithLifecycle()
            val hapticsOn by vm.haptics.collectAsStateWithLifecycle()
            val look = remember(mode, mono, style) { lookFor(mode, mono, style) }
            val feel = remember(style, soundOn, hapticsOn) { Feel(haptics, style, soundOn, hapticsOn) }

            // Dark status-bar icons on the light theme, light icons otherwise.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = look.isLight
                    isAppearanceLightNavigationBars = look.isLight
                }
            }

            CascadeTheme(look) {
                CompositionLocalProvider(LocalFeel provides feel) {
                    CascadeApp(vm, homeTicks.intValue, resumeTicks.intValue, proOwned, locked, chosenStyle, trialUntil)
                }
            }
        }
    }

    /**
     * Lets automated screenshot runs pick a style/theme from the command line, e.g.
     * adb shell am start -n com.bidule.cascade/.MainActivity --es cascade_style Ink --es cascade_theme Light
     */
    private fun applyDebugExtras(intent: Intent?) {
        if (!BuildConfig.SCREENSHOT_MODE) return
        val extras = intent?.extras ?: return
        val prefs = Prefs(this)
        extras.getString("cascade_style")?.let { prefs.style = it }
        extras.getString("cascade_theme")?.let { prefs.theme = it }
        DebugFlags.openStylePicker = extras.getBoolean("cascade_picker", false)
        DebugFlags.paywall = extras.getString("cascade_paywall")
        DebugFlags.clean = extras.getBoolean("cascade_clean", false)
        if (extras.containsKey("cascade_pro")) prefs.proOwned = extras.getBoolean("cascade_pro")
        prefs.onboarded = !extras.getBoolean("cascade_onboarding", false)
        if (extras.getBoolean("cascade_demo", false)) {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val keys = packageManager.queryIntentActivities(launcher, 0)
                .filter { it.activityInfo.packageName != packageName }
                .take(9)
                .map { android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name).flattenToString() }
            prefs.favorites = keys
            prefs.launchCounts = keys.mapIndexed { i, k -> k to (9 - i) * (9 - i) }.toMap()
        }
    }

    override fun onResume() {
        super.onResume()
        HomeUtils.rememberPreviousHome(this)
        resumeTicks.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeTicks.intValue++
    }
}

private enum class Screen { Home, Settings }

@Composable
private fun CascadeApp(
    vm: LauncherViewModel,
    homeTick: Int,
    resumeTick: Int,
    proOwned: Boolean,
    locked: Boolean,
    chosenStyle: CascadeStyle,
    trialUntil: Long,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    LaunchedEffect(homeTick) { if (homeTick > 0) screen = Screen.Home }

    val slide = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    val fade = spring<Float>(stiffness = Spring.StiffnessMediumLow)

    val look = LocalLook.current
    val feel = LocalFeel.current
    val scope = rememberCoroutineScope()
    var switching by remember { mutableStateOf<StyleSwitch?>(null) }
    var paywallFor by remember { mutableStateOf<CascadeStyle?>(null) }
    var previewEnded by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(!vm.onboarded) }
    LaunchedEffect(Unit) {
        DebugFlags.paywall?.let { name ->
            paywallFor = CascadeStyle.entries.firstOrNull { it.name == name }
            DebugFlags.paywall = null
        }
    }
    var lastOrigin by remember { mutableStateOf(Offset.Zero) }

    // A preview just ran out: go back to the free style and say so.
    LaunchedEffect(locked) {
        if (locked) {
            if (trialUntil > 0L) {
                previewEnded = true
                paywallFor = chosenStyle
                vm.endPreview()
            }
            vm.setStyle(vm.lastFreeStyle)
        }
    }

    // One tap: ripple or ink-bloom out from the button, swap style while the screen is covered,
    // then let it drain away to reveal the new look.
    fun animateTo(origin: Offset, target: CascadeStyle) {
        if (switching == null) {
            feel.styleSwitch(target)
            val sw = StyleSwitch(origin, target)
            switching = sw
            scope.launch {
                sw.progress.animateTo(0.5f, tween(if (target == CascadeStyle.Water) 440 else 520, easing = FastOutSlowInEasing))
                vm.setStyle(target)
                sw.progress.animateTo(1f, tween(560, easing = LinearOutSlowInEasing))
                switching = null
            }
        }
    }

    // Every style change goes through here, so Pro styles are checked in one place.
    val onSwitchStyle: (Offset, CascadeStyle) -> Unit = { origin, target ->
        lastOrigin = origin
        if (target.isPro && !proOwned && System.currentTimeMillis() >= trialUntil) {
            previewEnded = false
            paywallFor = target
        } else {
            animateTo(origin, target)
        }
    }

    // Footage capture only: the test asks for a switch on an exact frame.
    val switchRequest = DebugFlags.switchRequest.value
    LaunchedEffect(switchRequest) {
        if (switchRequest != null) {
            DebugFlags.switchRequest.value = null
            animateTo(switchRequest.second, switchRequest.first)
        }
    }

    // Bought while the paywall was open: close it and switch to the style they wanted.
    LaunchedEffect(proOwned) {
        val wanted = paywallFor
        if (proOwned && wanted != null) {
            paywallFor = null
            animateTo(lastOrigin, wanted)
        }
    }

    Box(Modifier.fillMaxSize().background(look.background)) {
        StyleBackdrop()
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState == Screen.Settings) {
                    (slideInHorizontally(slide) { it / 3 } + fadeIn(fade)) togetherWith
                        (scaleOut(fade, targetScale = 0.94f) + fadeOut(fade))
                } else {
                    (scaleIn(fade, initialScale = 0.94f) + fadeIn(fade)) togetherWith
                        (slideOutHorizontally(slide) { it / 3 } + fadeOut(fade))
                }
            },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.Home -> HomeScreen(
                    vm = vm,
                    homeTick = homeTick,
                    resumeTick = resumeTick,
                    onOpenSettings = { screen = Screen.Settings },
                    onSwitchStyle = onSwitchStyle,
                )
                Screen.Settings -> SettingsScreen(
                    vm = vm,
                    resumeTick = resumeTick,
                    proOwned = proOwned,
                    onPickStyle = { st -> onSwitchStyle(Offset.Zero, st) },
                    onShowPro = { paywallFor = CascadeStyle.Stone; previewEnded = false },
                    onBack = { screen = Screen.Home },
                )
            }
        }
        StyleSwitchLayer(switching)

        paywallFor?.let { st ->
            Paywall(
                style = st,
                previewEnded = previewEnded,
                canPreview = vm.canPreview(st),
                onPreview = {
                    paywallFor = null
                    vm.startPreview(st)
                    animateTo(lastOrigin, st)
                },
                onClose = { paywallFor = null },
            )
        }

        if (showOnboarding) {
            Onboarding(
                currentStyle = look.kind,
                proOwned = proOwned,
                onPickStyle = { vm.setStyle(it) },
                onDone = {
                    vm.finishOnboarding()
                    showOnboarding = false
                },
            )
        }
    }
}

/** Set from screenshot automation only. */
object DebugFlags {
    var openStylePicker = false
    var paywall: String? = null
    /** Hide the "Preview mode" pill for store screenshots. */
    var clean = false
    /** Footage capture: set by the test to play a style switch from a point. */
    val switchRequest = androidx.compose.runtime.mutableStateOf<Pair<CascadeStyle, Offset>?>(null)
}
