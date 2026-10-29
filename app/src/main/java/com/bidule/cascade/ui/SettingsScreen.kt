package com.bidule.cascade.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bidule.cascade.AppRepository
import com.bidule.cascade.HomeUtils
import com.bidule.cascade.LauncherViewModel
import com.bidule.cascade.Prefs

@Composable
fun SettingsScreen(vm: LauncherViewModel, resumeTick: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    var roleTick by remember { mutableIntStateOf(0) }
    val isDefaultHome = remember(resumeTick, roleTick) { HomeUtils.isDefaultHome(context) }
    val previousHome = remember(resumeTick) { Prefs(context).previousHome }
    val otherHomes = remember(resumeTick) { HomeUtils.otherHomeApps(context) }
    val hasNotifAccess = remember(resumeTick) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    val apps by vm.apps.collectAsStateWithLifecycle()
    val favoriteKeys by vm.favorites.collectAsStateWithLifecycle()
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val favorites = remember(appsByKey, favoriteKeys) { favoriteKeys.mapNotNull { appsByKey[it] } }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleTick++
    }
    fun becomeHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        HomeUtils.openHomeSettings(context)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Cascade settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── The way back ─────────────────────────────────────────────
            item {
                Section(
                    title = "Go back to your old home screen",
                    highlight = true,
                ) {
                    Text(
                        "Cascade never touches your previous launcher. Its icons, folders and widgets " +
                            "are still there exactly as you left them, and switching back brings them all back instantly.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (!isDefaultHome) {
                        Text(
                            "Cascade isn't your home screen right now, so your old one is already active.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Accent,
                        )
                    } else if (otherHomes.isEmpty()) {
                        Text("No other home screen app was found on this phone.")
                    }
                    otherHomes.forEach { home ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(home.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(home.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                if (home.packageName == previousHome) {
                                    Text("Your previous home screen", fontSize = 12.sp, color = Accent)
                                }
                            }
                            if (isDefaultHome) {
                                Button(onClick = {
                                    HomeUtils.openHomeSettings(context)
                                    Toast.makeText(
                                        context,
                                        "Pick “${home.label}” to switch back",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }) { Text("Switch back") }
                            } else {
                                TextButton(onClick = { HomeUtils.peekHome(context, home.packageName) }) { Text("Open") }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { AppRepository.uninstall(context, context.packageName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Uninstall Cascade completely") }
                }
            }

            // ── Become the home screen ───────────────────────────────────
            if (!isDefaultHome) {
                item {
                    Section(title = "Use Cascade as your home screen") {
                        Text(
                            "You're in preview mode. When you're ready, make Cascade the app your Home button opens. " +
                                "You can always come back to this screen to undo it.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { becomeHome() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Make Cascade my home screen")
                        }
                    }
                }
            }

            // ── Notifications ────────────────────────────────────────────
            item {
                Section(title = "Notification previews") {
                    if (hasNotifAccess) {
                        Text(
                            "On. New notifications appear under the app in your favorites.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            "Let Cascade read notifications so they show up under your favorite apps. " +
                                "Nothing leaves your phone.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                AppRepository.safeStart(
                                    context,
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Allow notification access") }
                    }
                }
            }

            // ── Favorites order ──────────────────────────────────────────
            item {
                Section(title = "Favorites") {
                    if (favorites.isEmpty()) {
                        Text(
                            "No favorites yet. On the home screen, long-press any app and choose “Pin to home”.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    favorites.forEachIndexed { index, app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(app.icon, contentDescription = null, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { vm.moveFavorite(app.key, -1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(onClick = { vm.moveFavorite(app.key, 1) }, enabled = index < favorites.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                            }
                            IconButton(onClick = { vm.toggleFavorite(app.key) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Unpin")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Tip: long-press the clock to open these settings. Tap it to open your alarms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    highlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) Color(0xFF1E2A3A) else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
