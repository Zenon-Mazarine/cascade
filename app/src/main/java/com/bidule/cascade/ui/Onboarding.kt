package com.bidule.cascade.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bidule.cascade.AppRepository
import com.bidule.cascade.HomeUtils

/**
 * First run: what Cascade is, pick a style, make it the home screen (with the promise that the
 * original is safe), and optionally turn on notification previews.
 */
@Composable
fun Onboarding(
    currentStyle: CascadeStyle,
    proOwned: Boolean,
    onPickStyle: (CascadeStyle) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { step = 3 }

    fun makeHome() {
        HomeUtils.setHomeEnabled(context, true)
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        HomeUtils.openHomeSettings(context)
        step = 3
    }

    BackHandler(enabled = step > 0) { step-- }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(320)) { w -> if (forward) w / 4 else -w / 4 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { w -> if (forward) -w / 4 else w / 4 } + fadeOut(tween(200)))
            },
            label = "onboarding",
            modifier = Modifier.systemBarsPadding(),
        ) { s ->
            when (s) {
                0 -> Page(
                    title = "Cascade",
                    body = "A home screen with personality. Pick a style and everything changes with it: " +
                        "the layout, the motion, the sounds and the way it feels in your hand.",
                    primary = "Get started",
                    onPrimary = { step = 1 },
                ) { WelcomeOrbs() }

                1 -> Page(
                    title = "Choose a style",
                    body = "You can switch any time with the style button next to the clock.",
                    primary = "Continue",
                    onPrimary = { step = 2 },
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(330.dp),
                    ) {
                        items(CascadeStyle.entries.toList()) { st ->
                            MiniStyleCard(st, selected = st == currentStyle, locked = st.isPro && !proOwned) {
                                if (!(st.isPro && !proOwned)) onPickStyle(st)
                            }
                        }
                    }
                    Text(
                        "Styles marked PRO can be previewed for free later from the style button.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                2 -> Page(
                    title = "Make it your home screen",
                    body = "Your phone's current home screen isn't changed or deleted. Its icons, folders and widgets " +
                        "stay exactly as they are, and the Original button brings it back in one tap.",
                    primary = "Make Cascade my home screen",
                    onPrimary = { makeHome() },
                    secondary = "Not now",
                    onSecondary = { step = 3 },
                ) { SafeIllustration() }

                else -> Page(
                    title = "Notifications, in place",
                    body = "Optional: let Cascade show new notifications right next to the app they belong to. " +
                        "Everything stays on your phone; nothing is sent anywhere.",
                    primary = "Allow notification previews",
                    onPrimary = {
                        AppRepository.safeStart(
                            context,
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        onDone()
                    },
                    secondary = "Skip",
                    onSecondary = onDone,
                ) { }
            }
        }
    }
}

@Composable
private fun Page(
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Spacer(Modifier.weight(0.5f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
        Spacer(Modifier.weight(1f))
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text(primary, fontWeight = FontWeight.SemiBold)
        }
        if (secondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondary) }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun WelcomeOrbs() {
    val look = LocalLook.current
    val t by rememberInfiniteTransition(label = "welcome").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "welcomeT",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CascadeStyle.entries.filter { it != CascadeStyle.Classic }.forEachIndexed { i, st ->
            val preview = lookFor(look.mode, look.mono, st)
            Canvas(
                Modifier
                    .size(44.dp)
                    .graphicsLayer { translationY = (if (i % 2 == 0) t else 1f - t) * 14f }
            ) { drawOrb(preview) }
        }
    }
}

@Composable
private fun SafeIllustration() {
    val look = LocalLook.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PhoneSketch("Your home screen", MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f))
        Text("⇄", fontSize = 28.sp, color = look.accent)
        PhoneSketch("Cascade", look.accent.copy(alpha = 0.35f))
    }
}

@Composable
private fun PhoneSketch(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 70.dp, height = 130.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
                .background(color)
        )
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MiniStyleCard(st: CascadeStyle, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    val preview = lookFor(look.mode, look.mono, st)
    val bg = if (preview.background == Color.Transparent) preview.panel.copy(alpha = 1f) else preview.background
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                if (selected) BorderStroke(2.5.dp, preview.accent) else BorderStroke(1.dp, preview.text.copy(alpha = 0.12f)),
                RoundedCornerShape(16.dp),
            )
            .background(bg)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.2f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(46.dp)) { drawOrb(preview) }
            if (locked) ProBadge(Modifier.align(Alignment.TopEnd))
        }
        Text(st.label, color = preview.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFFFC94D))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text("PRO", color = Color(0xFF2A1E00), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
