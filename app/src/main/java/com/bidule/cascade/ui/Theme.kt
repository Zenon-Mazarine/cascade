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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

enum class ThemeMode(val label: String) {
    Wallpaper("Wallpaper"),
    Dark("Dark"),
    Light("Light"),
}

/** The whole personality of the launcher: visuals, motion, haptics and sound. */
enum class CascadeStyle(val label: String, val blurb: String) {
    Classic("Classic", "Clean and quiet"),
    Water("Water", "Ripples, droplets and liquid motion"),
    Ink("Ink", "Brushstrokes on paper, with a red seal"),
    Stone("Stone", "A zen garden: pebbles on raked sand"),
    Paper("Paper", "Folded origami cards that crease and unfold"),
    Neon("Neon", "Glowing signs on a rainy night street"),
    Cosmos("Cosmos", "Your apps as a drifting constellation"),
}

/** Which home layout a style uses. */
val CascadeStyle.isField: Boolean
    get() = this == CascadeStyle.Water || this == CascadeStyle.Stone || this == CascadeStyle.Cosmos

/** Neon sign colors, cycled down the street. */
val NeonColors = listOf(Color(0xFFFF3EA5), Color(0xFF00E5FF), Color(0xFFFFB000), Color(0xFF39FF88), Color(0xFFB26BFF))

val RailWidth = 40.dp

/** Springs shared by every animation, so the whole app moves with one feel. */
object Motion {
    val soft = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    val bouncy = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
    val gentle = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)
    val liquid = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
    val brush = spring<Float>(dampingRatio = 0.92f, stiffness = Spring.StiffnessMedium)
}

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Everything that changes with the chosen theme and style. */
@Immutable
data class Look(
    val mode: ThemeMode,
    val mono: Boolean,
    val kind: CascadeStyle,
    /** Transparent in Wallpaper mode so the wallpaper shows through. */
    val background: Color,
    val text: Color,
    val subtext: Color,
    val accent: Color,
    val panel: Color,
    val pressed: Color,
    val notifBg: Color,
    val shadow: Shadow?,
    /** Color of the style's effects: water light or ink. */
    val effect: Color,
    val labelFont: FontFamily,
    val labelWeight: FontWeight,
    val displayFont: FontFamily,
    val displayWeight: FontWeight,
) {
    val isLight: Boolean get() = mode == ThemeMode.Light
    val iconFilter: ColorFilter? get() = if (mono) grayscale else null
    val overlay: Color
        get() = if (background == Color.Transparent) panel.copy(alpha = 0.96f) else background

    fun style(size: TextUnit, weight: FontWeight = labelWeight, color: Color = text) =
        TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = labelFont, shadow = shadow)

    fun display(size: TextUnit, color: Color = text) =
        TextStyle(color = color, fontSize = size, fontWeight = displayWeight, fontFamily = displayFont, shadow = shadow)
}

private val wallpaperShadow = Shadow(Color(0x99000000), Offset(0f, 2f), 8f)

private data class Palette(
    val background: Color,
    val text: Color,
    val subtext: Color,
    val accent: Color,
    val panel: Color,
    val effect: Color,
)

private fun palette(kind: CascadeStyle, mode: ThemeMode): Palette = when (kind) {
    CascadeStyle.Classic -> when (mode) {
        ThemeMode.Wallpaper -> Palette(Color.Transparent, Color.White, Color.White.copy(alpha = 0.78f), Color(0xFF9ECBFF), Color(0xF0181A1F), Color(0xFF9ECBFF))
        ThemeMode.Dark -> Palette(Color.Black, Color(0xFFF2F2F2), Color(0xFF9A9A9A), Color(0xFF9ECBFF), Color(0xFF141414), Color(0xFF9ECBFF))
        ThemeMode.Light -> Palette(Color(0xFFF4F4F1), Color(0xFF111111), Color(0xFF6B6B6B), Color(0xFF2F5BD3), Color.White, Color(0xFF2F5BD3))
    }
    CascadeStyle.Water -> when (mode) {
        ThemeMode.Wallpaper -> Palette(Color.Transparent, Color.White, Color.White.copy(alpha = 0.8f), Color(0xFF7FE3F2), Color(0xF00A2530), Color(0xFFBFF3FF))
        ThemeMode.Dark -> Palette(Color(0xFF03141C), Color(0xFFDDF3FA), Color(0xFF7FA6B3), Color(0xFF4FD1E8), Color(0xFF0A1F29), Color(0xFF4FD1E8))
        ThemeMode.Light -> Palette(Color(0xFFE8F4F7), Color(0xFF0C2A35), Color(0xFF4F7381), Color(0xFF1A8CB0), Color(0xFFF7FCFD), Color(0xFF1A8CB0))
    }
    CascadeStyle.Ink -> when (mode) {
        ThemeMode.Wallpaper -> Palette(Color.Transparent, Color.White, Color.White.copy(alpha = 0.8f), Color(0xFFE25A4E), Color(0xF21A1714), Color.White)
        ThemeMode.Dark -> Palette(Color(0xFF0F0E0D), Color(0xFFEDE6D8), Color(0xFF948B7E), Color(0xFFD2453A), Color(0xFF1A1816), Color(0xFFEDE6D8))
        ThemeMode.Light -> Palette(Color(0xFFF3EEE3), Color(0xFF1A1714), Color(0xFF6E665C), Color(0xFFB8322A), Color(0xFFFBF7EE), Color(0xFF1A1714))
    }
    CascadeStyle.Stone -> when (mode) {
        ThemeMode.Wallpaper -> Palette(Color.Transparent, Color.White, Color.White.copy(alpha = 0.8f), Color(0xFFA8C686), Color(0xF0242019), Color(0xFFE8DCC4))
        ThemeMode.Dark -> Palette(Color(0xFF1C1A17), Color(0xFFE9E2D4), Color(0xFF9C9384), Color(0xFF8FB06A), Color(0xFF262320), Color(0xFFCFC2A8))
        ThemeMode.Light -> Palette(Color(0xFFE9E1D1), Color(0xFF2E2A25), Color(0xFF7A7266), Color(0xFF5E7F3A), Color(0xFFF3EDE1), Color(0xFF8C7B62))
    }
    CascadeStyle.Paper -> when (mode) {
        ThemeMode.Wallpaper -> Palette(Color.Transparent, Color.White, Color.White.copy(alpha = 0.8f), Color(0xFFFFB37A), Color(0xF02B2622), Color.White)
        ThemeMode.Dark -> Palette(Color(0xFF22201D), Color(0xFFF1EADF), Color(0xFFA49A8B), Color(0xFFE8875B), Color(0xFF2E2B27), Color(0xFFF1EADF))
        ThemeMode.Light -> Palette(Color(0xFFF7F6F2), Color(0xFF1F2328), Color(0xFF6A7079), Color(0xFFE4572E), Color.White, Color(0xFF1F2328))
    }
    CascadeStyle.Neon -> when (mode) {
        ThemeMode.Wallpaper -> Palette(Color.Transparent, Color.White, Color.White.copy(alpha = 0.8f), Color(0xFFFF3EA5), Color(0xF00B0716), Color(0xFF00E5FF))
        ThemeMode.Dark -> Palette(Color(0xFF07060D), Color(0xFFF5F0FF), Color(0xFF8E86A8), Color(0xFFFF3EA5), Color(0xFF120F1F), Color(0xFF00E5FF))
        ThemeMode.Light -> Palette(Color(0xFFE9E6F0), Color(0xFF1A1430), Color(0xFF6B6385), Color(0xFFE0007A), Color(0xFFF7F5FB), Color(0xFF0098B3))
    }
    CascadeStyle.Cosmos -> when (mode) {
        ThemeMode.Wallpaper -> Palette(Color.Transparent, Color.White, Color.White.copy(alpha = 0.8f), Color(0xFFB9A6FF), Color(0xF00A0B1E), Color(0xFFDCE6FF))
        ThemeMode.Dark -> Palette(Color(0xFF04050F), Color(0xFFE6EAFF), Color(0xFF8790B8), Color(0xFF9F8CFF), Color(0xFF0D1026), Color(0xFFCFE0FF))
        ThemeMode.Light -> Palette(Color(0xFFE9ECF7), Color(0xFF141833), Color(0xFF626A8C), Color(0xFF5B4BD6), Color(0xFFF8F9FE), Color(0xFF5B4BD6))
    }
}

fun lookFor(mode: ThemeMode, mono: Boolean, kind: CascadeStyle): Look {
    val p = palette(kind, mode)
    val light = mode == ThemeMode.Light
    val (labelFont, labelWeight, displayFont, displayWeight) = when (kind) {
        CascadeStyle.Classic -> Fonts(FontFamily.Default, FontWeight.Normal, FontFamily.Default, FontWeight.Light)
        CascadeStyle.Water -> Fonts(FontFamily.SansSerif, FontWeight.Normal, FontFamily.SansSerif, FontWeight.Thin)
        CascadeStyle.Ink -> Fonts(FontFamily.Serif, FontWeight.Normal, FontFamily.Serif, FontWeight.Light)
        CascadeStyle.Stone -> Fonts(FontFamily.Serif, FontWeight.Normal, FontFamily.Serif, FontWeight.ExtraLight)
        CascadeStyle.Paper -> Fonts(FontFamily.Monospace, FontWeight.Normal, FontFamily.Monospace, FontWeight.Normal)
        CascadeStyle.Neon -> Fonts(FontFamily.SansSerif, FontWeight.Medium, FontFamily.Cursive, FontWeight.Bold)
        CascadeStyle.Cosmos -> Fonts(FontFamily.SansSerif, FontWeight.Light, FontFamily.SansSerif, FontWeight.Thin)
    }
    val base = Look(
        mode = mode,
        mono = mono,
        kind = kind,
        background = p.background,
        text = p.text,
        subtext = p.subtext,
        accent = p.accent,
        panel = p.panel,
        pressed = if (light) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = if (mode == ThemeMode.Wallpaper) 0.12f else 0.08f),
        notifBg = when (mode) {
            ThemeMode.Wallpaper -> Color(0x40000000)
            ThemeMode.Dark -> p.text.copy(alpha = 0.06f)
            ThemeMode.Light -> Color.Black.copy(alpha = 0.05f)
        },
        shadow = if (mode == ThemeMode.Wallpaper) wallpaperShadow else null,
        effect = p.effect,
        labelFont = labelFont,
        labelWeight = labelWeight,
        displayFont = displayFont,
        displayWeight = displayWeight,
    )
    // Black & white: no color anywhere, the accent and effects become the text color.
    return if (mono) base.copy(accent = base.text, effect = base.text) else base
}

private data class Fonts(val label: FontFamily, val labelWeight: FontWeight, val display: FontFamily, val displayWeight: FontWeight)

val LocalLook = staticCompositionLocalOf { lookFor(ThemeMode.Wallpaper, false, CascadeStyle.Classic) }

@Composable
fun CascadeTheme(look: Look, content: @Composable () -> Unit) {
    val onAccent = if (look.accent.luminance() > 0.45f) Color(0xFF0B0B0B) else Color.White
    val solidBg = if (look.background == Color.Transparent) {
        look.panel.copy(alpha = 1f)
    } else {
        look.background
    }
    val surface = look.panel.copy(alpha = 1f)
    val surfaceVariant = look.text.copy(alpha = 0.06f).compositeOver(surface)
    val scheme = if (look.isLight) {
        lightColorScheme(
            primary = look.accent,
            onPrimary = onAccent,
            background = solidBg,
            onBackground = look.text,
            surface = surface,
            onSurface = look.text,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = look.subtext,
        )
    } else {
        darkColorScheme(
            primary = look.accent,
            onPrimary = onAccent,
            background = solidBg,
            onBackground = if (look.mode == ThemeMode.Wallpaper) Color.White else look.text,
            surface = surface,
            onSurface = if (look.mode == ThemeMode.Wallpaper) Color.White else look.text,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = look.subtext,
        )
    }
    CompositionLocalProvider(LocalLook provides look) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
