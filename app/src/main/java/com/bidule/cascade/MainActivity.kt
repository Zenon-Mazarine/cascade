package com.bidule.cascade

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidule.cascade.ui.CascadeTheme
import com.bidule.cascade.ui.HomeScreen
import com.bidule.cascade.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    /** Bumped each time the Home button is pressed while Cascade is already showing. */
    private val homeTicks = mutableIntStateOf(0)

    /** Bumped on every resume, so screens can re-check settings the user may have changed. */
    private val resumeTicks = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val vm: LauncherViewModel = viewModel()
            CascadeTheme {
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

    when (screen) {
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
