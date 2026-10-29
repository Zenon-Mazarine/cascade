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
import androidx.compose.runtime.Composable
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
import com.bidule.cascade.ui.CascadeTheme
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
        setContent {
            val vm: LauncherViewModel = viewModel()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val mono by vm.mono.collectAsStateWithLifecycle()
            val look = remember(mode, mono) { lookFor(mode, mono) }

            // Dark status-bar icons on the light theme, light icons otherwise.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = look.isLight
                    isAppearanceLightNavigationBars = look.isLight
                }
            }

            CascadeTheme(look) {
                CascadeApp(vm, homeTicks.intValue, resumeTicks.intValue)
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

    Box(Modifier.fillMaxSize().background(LocalLook.current.background)) {
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
                )
                Screen.Settings -> SettingsScreen(
                    vm = vm,
                    resumeTick = resumeTick,
                    onBack = { screen = Screen.Home },
                )
            }
        }
    }
}
