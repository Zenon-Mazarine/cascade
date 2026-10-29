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
        Sounds.init(this)
        val haptics = Haptics(this)
        setContent {
            val vm: LauncherViewModel = viewModel()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val mono by vm.mono.collectAsStateWithLifecycle()
            val style by vm.style.collectAsStateWithLifecycle()
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
                    CascadeApp(vm, homeTicks.intValue, resumeTicks.intValue)
                }
            }
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
private fun CascadeApp(vm: LauncherViewModel, homeTick: Int, resumeTick: Int) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    LaunchedEffect(homeTick) { if (homeTick > 0) screen = Screen.Home }

    val slide = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    val fade = spring<Float>(stiffness = Spring.StiffnessMediumLow)

    val look = LocalLook.current
    val feel = LocalFeel.current
    val scope = rememberCoroutineScope()
    var switching by remember { mutableStateOf<StyleSwitch?>(null) }

    // One tap: ripple or ink-bloom out from the button, swap style while the screen is covered,
    // then let it drain away to reveal the new look.
    val onSwitchStyle: (Offset) -> Unit = { origin ->
        if (switching == null) {
            val target = if (look.kind == CascadeStyle.Water) CascadeStyle.Ink else CascadeStyle.Water
            feel.styleSwitch(target)
            val sw = StyleSwitch(origin, target)
            switching = sw
            scope.launch {
                sw.progress.animateTo(0.5f, tween(if (target == CascadeStyle.Ink) 520 else 440, easing = FastOutSlowInEasing))
                vm.setStyle(target)
                sw.progress.animateTo(1f, tween(560, easing = LinearOutSlowInEasing))
                switching = null
            }
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
                    onBack = { screen = Screen.Home },
                )
            }
        }
        StyleSwitchLayer(switching)
    }
}
