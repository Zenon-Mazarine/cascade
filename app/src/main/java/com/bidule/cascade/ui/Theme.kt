package com.bidule.cascade.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Accent = Color(0xFF9ECBFF)
val PanelColor = Color(0xF21A1D23)
val RailWidth = 36.dp

private val wallpaperShadow = Shadow(Color(0x99000000), Offset(0f, 2f), 8f)

/** White text with a soft shadow so it stays readable on any wallpaper. */
object OnWallpaper {
    val clock = TextStyle(color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light, shadow = wallpaperShadow)
    val date = TextStyle(color = Color.White, fontSize = 17.sp, shadow = wallpaperShadow)
    val label = TextStyle(color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Medium, shadow = wallpaperShadow)
    val letter = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium, shadow = wallpaperShadow)
    val notifTitle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = wallpaperShadow)
    val notifText = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, shadow = wallpaperShadow)
    val hint = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, shadow = wallpaperShadow)
}

@Composable
fun CascadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF0E1A2A),
            background = Color(0xFF111317),
            surface = Color(0xFF1A1D23),
            surfaceVariant = Color(0xFF232731),
        ),
        content = content,
    )
}
