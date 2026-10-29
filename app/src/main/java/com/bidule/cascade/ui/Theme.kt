package com.bidule.cascade.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode(val label: String) {
    Wallpaper("Wallpaper"),
    Dark("Dark"),
    Light("Light"),
}

val RailWidth = 40.dp

/** Springs shared by every animation, so the whole app moves with one feel. */
object Motion {
    val soft = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    val bouncy = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
    val gentle = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)
}

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Everything that changes with the chosen theme. */
@Immutable
data class Look(
    val mode: ThemeMode,
    val mono: Boolean,
    /** Transparent in Wallpaper mode so the wallpaper shows through. */
    val background: Color,
    val text: Color,
    val subtext: Color,
    val accent: Color,
    val panel: Color,
    val pressed: Color,
    val notifBg: Color,
    val shadow: Shadow?,
) {
    val isLight: Boolean get() = mode == ThemeMode.Light
    val iconFilter: ColorFilter? get() = if (mono) grayscale else null
    val overlay: Color get() = if (background == Color.Transparent) Color(0xF20E0F12) else background

    fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = text) =
        TextStyle(color = color, fontSize = size, fontWeight = weight, shadow = shadow)
}

fun lookFor(mode: ThemeMode, mono: Boolean): Look {
    val base = when (mode) {
        ThemeMode.Wallpaper -> Look(
            mode = mode, mono = mono,
            background = Color.Transparent,
            text = Color.White,
            subtext = Color.White.copy(alpha = 0.78f),
            accent = Color(0xFF9ECBFF),
            panel = Color(0xF0181A1F),
            pressed = Color.White.copy(alpha = 0.12f),
            notifBg = Color(0x40000000),
            shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 8f),
        )
        ThemeMode.Dark -> Look(
            mode = mode, mono = mono,
            background = Color.Black,
            text = Color(0xFFF2F2F2),
            subtext = Color(0xFF9A9A9A),
            accent = Color(0xFF9ECBFF),
            panel = Color(0xFF141414),
            pressed = Color.White.copy(alpha = 0.08f),
            notifBg = Color(0xFF151515),
            shadow = null,
        )
        ThemeMode.Light -> Look(
            mode = mode, mono = mono,
            background = Color(0xFFF4F4F1),
            text = Color(0xFF111111),
            subtext = Color(0xFF6B6B6B),
            accent = Color(0xFF2F5BD3),
            panel = Color.White,
            pressed = Color.Black.copy(alpha = 0.06f),
            notifBg = Color.Black.copy(alpha = 0.05f),
            shadow = null,
        )
    }
    // Black & white: no color anywhere, the accent becomes the text color.
    return if (mono) base.copy(accent = base.text) else base
}

val LocalLook = staticCompositionLocalOf { lookFor(ThemeMode.Wallpaper, false) }

@Composable
fun CascadeTheme(look: Look, content: @Composable () -> Unit) {
    val scheme = if (look.isLight) {
        lightColorScheme(
            primary = look.accent,
            onPrimary = Color.White,
            background = Color(0xFFF4F4F1),
            surface = Color.White,
            surfaceVariant = Color(0xFFEAEAE6),
        )
    } else {
        darkColorScheme(
            primary = look.accent,
            onPrimary = Color(0xFF0B0B0B),
            background = if (look.mode == ThemeMode.Dark) Color.Black else Color(0xFF0E0F12),
            surface = Color(0xFF16181C),
            surfaceVariant = Color(0xFF1E2128),
        )
    }
    CompositionLocalProvider(LocalLook provides look) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
