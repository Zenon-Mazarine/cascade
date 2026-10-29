package com.bidule.cascade.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf
import com.bidule.cascade.R
import com.bidule.cascade.Sounds
import java.util.concurrent.ConcurrentHashMap

/** Vibration "textures" built from Android's haptic primitives, with fallbacks for older phones. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val supported = ConcurrentHashMap<Int, Boolean>()

    enum class Fallback { Tick, Click, Heavy }

    /** One primitive in a composition: what, how strong (0–1), and how long after the previous one. */
    class Step(val primitive: Int, val scale: Float, val delayMs: Int = 0)

    private fun isSupported(primitive: Int): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val v = vibrator ?: return false
        return supported.getOrPut(primitive) { v.areAllPrimitivesSupported(primitive) }
    }

    fun play(steps: List<Step>, fallback: Fallback) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = composed(steps) ?: fallbackEffect(fallback)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(
                    effect,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build(),
                )
            }
        } catch (e: Exception) {
            // Never let a buzz crash the home screen.
        }
    }

    private fun composed(steps: List<Step>): VibrationEffect? {
        if (Build.VERSION.SDK_INT < 30) return null
        val usable = steps.filter { isSupported(it.primitive) }
        if (usable.isEmpty()) return null
        val c = VibrationEffect.startComposition()
        usable.forEach { c.addPrimitive(it.primitive, it.scale.coerceIn(0f, 1f), it.delayMs) }
        return c.compose()
    }

    private fun fallbackEffect(f: Fallback): VibrationEffect =
        if (Build.VERSION.SDK_INT >= 29) {
            VibrationEffect.createPredefined(
                when (f) {
                    Fallback.Tick -> VibrationEffect.EFFECT_TICK
                    Fallback.Click -> VibrationEffect.EFFECT_CLICK
                    Fallback.Heavy -> VibrationEffect.EFFECT_HEAVY_CLICK
                }
            )
        } else {
            VibrationEffect.createOneShot(
                when (f) {
                    Fallback.Tick -> 8L
                    Fallback.Click -> 16L
                    Fallback.Heavy -> 30L
                },
                VibrationEffect.DEFAULT_AMPLITUDE,
            )
        }
}

// Primitive ids (VibrationEffect.Composition). Written out so they compile on any SDK level;
// unsupported ones are skipped at runtime.
private const val CLICK = 1
private const val THUD = 2
private const val SPIN = 3
private const val QUICK_RISE = 4
private const val SLOW_RISE = 5
private const val QUICK_FALL = 6
private const val TICK = 7
private const val LOW_TICK = 8

private fun s(p: Int, scale: Float, delay: Int = 0) = Haptics.Step(p, scale, delay)

/** One moment's feedback: a sound (optional) and a vibration texture. */
private class Cue(
    val sound: Int? = null,
    val volume: Float = 0.4f,
    val rate: Float = 1f,
    val steps: List<Haptics.Step> = emptyList(),
    val fallback: Haptics.Fallback = Haptics.Fallback.Tick,
)

/** The full set of cues that gives a style its feel. */
private class FeelSet(
    val launch: Cue,
    val letterSound: Int?,
    val letterVolume: Float,
    val letterScale: FloatArray,
    val letterSteps: List<Haptics.Step>,
    val open: Cue,
    val longPress: Cue,
    val swap: Cue,
    val toggle: Cue,
    val switchTo: Cue,
)

// Pitch ratios, low to high. Top of the alphabet sounds high, bottom sounds low.
private val PENTATONIC = floatArrayOf(0.75f, 0.84375f, 0.9375f, 1.125f, 1.25f, 1.5f, 1.6875f, 1.875f)
private val HIRAJOSHI = floatArrayOf(0.75f, 0.842f, 0.892f, 1.124f, 1.191f, 1.5f, 1.684f, 1.784f)
private val MINOR_PENTA = floatArrayOf(0.75f, 0.891f, 1.0f, 1.122f, 1.335f, 1.5f, 1.782f, 2.0f)
private val LYDIAN = floatArrayOf(0.75f, 0.842f, 0.945f, 1.06f, 1.122f, 1.26f, 1.414f, 1.5f)
private val WOOD = floatArrayOf(0.7f, 0.8f, 0.9f, 1.0f, 1.12f, 1.26f, 1.4f, 1.6f)

private fun feelFor(kind: CascadeStyle): FeelSet = when (kind) {
    CascadeStyle.Classic -> FeelSet(
        launch = Cue(R.raw.classic_tick, 0.25f, steps = listOf(s(CLICK, 0.6f)), fallback = Haptics.Fallback.Click),
        letterSound = null, letterVolume = 0f, letterScale = PENTATONIC, letterSteps = listOf(s(TICK, 0.5f)),
        open = Cue(steps = listOf(s(TICK, 0.4f))),
        longPress = Cue(steps = listOf(s(CLICK, 0.8f)), fallback = Haptics.Fallback.Click),
        swap = Cue(steps = listOf(s(TICK, 0.6f))),
        toggle = Cue(steps = listOf(s(TICK, 0.5f))),
        switchTo = Cue(R.raw.classic_tick, 0.4f, steps = listOf(s(CLICK, 0.8f)), fallback = Haptics.Fallback.Click),
    )
    CascadeStyle.Water -> FeelSet(
        launch = Cue(R.raw.water_drop, 0.5f, steps = listOf(s(LOW_TICK, 0.7f), s(TICK, 0.35f, 45)), fallback = Haptics.Fallback.Click),
        letterSound = R.raw.water_bubble, letterVolume = 0.32f, letterScale = PENTATONIC, letterSteps = listOf(s(LOW_TICK, 0.55f)),
        open = Cue(R.raw.water_soft, 0.4f, steps = listOf(s(QUICK_RISE, 0.35f))),
        longPress = Cue(R.raw.water_soft, 0.3f, 0.8f, listOf(s(LOW_TICK, 0.8f), s(LOW_TICK, 0.5f, 60)), Haptics.Fallback.Click),
        swap = Cue(R.raw.water_bubble, 0.25f, 1.5f, listOf(s(LOW_TICK, 0.6f))),
        toggle = Cue(R.raw.water_bubble, 0.22f, 1.2f, listOf(s(TICK, 0.4f))),
        switchTo = Cue(
            R.raw.water_splash, 0.6f,
            steps = listOf(s(SLOW_RISE, 0.5f), s(QUICK_FALL, 0.6f), s(LOW_TICK, 0.8f, 80), s(LOW_TICK, 0.5f, 70)),
            fallback = Haptics.Fallback.Heavy,
        ),
    )
    CascadeStyle.Ink -> FeelSet(
        launch = Cue(R.raw.ink_stamp, 0.55f, steps = listOf(s(CLICK, 0.9f), s(THUD, 0.45f, 15)), fallback = Haptics.Fallback.Click),
        letterSound = R.raw.ink_pluck, letterVolume = 0.28f, letterScale = HIRAJOSHI, letterSteps = listOf(s(TICK, 0.75f)),
        open = Cue(R.raw.ink_brush, 0.45f, steps = listOf(s(QUICK_RISE, 0.3f), s(TICK, 0.5f, 90))),
        longPress = Cue(R.raw.ink_brush, 0.3f, 1.3f, listOf(s(CLICK, 0.7f)), Haptics.Fallback.Click),
        swap = Cue(R.raw.ink_tap, 0.3f, steps = listOf(s(TICK, 0.9f))),
        toggle = Cue(R.raw.ink_tap, 0.3f, 0.9f, listOf(s(TICK, 0.5f))),
        switchTo = Cue(
            R.raw.ink_switch, 0.6f,
            steps = listOf(s(TICK, 0.3f), s(TICK, 0.5f, 40), s(TICK, 0.7f, 40), s(THUD, 0.9f, 220)),
            fallback = Haptics.Fallback.Heavy,
        ),
    )
    CascadeStyle.Stone -> FeelSet(
        launch = Cue(R.raw.stone_click, 0.55f, steps = listOf(s(THUD, 0.9f)), fallback = Haptics.Fallback.Heavy),
        letterSound = R.raw.stone_tap, letterVolume = 0.3f, letterScale = WOOD, letterSteps = listOf(s(LOW_TICK, 0.8f)),
        open = Cue(R.raw.stone_rake, 0.45f, steps = listOf(s(QUICK_FALL, 0.5f), s(THUD, 0.4f, 40))),
        longPress = Cue(R.raw.stone_tap, 0.35f, 0.7f, listOf(s(THUD, 0.7f)), Haptics.Fallback.Click),
        swap = Cue(R.raw.stone_tap, 0.3f, 1.2f, listOf(s(LOW_TICK, 0.9f))),
        toggle = Cue(R.raw.stone_tap, 0.28f, steps = listOf(s(TICK, 0.6f))),
        switchTo = Cue(
            R.raw.stone_switch, 0.6f,
            steps = listOf(s(THUD, 0.5f), s(THUD, 0.9f, 250), s(LOW_TICK, 0.4f, 120), s(LOW_TICK, 0.3f, 80)),
            fallback = Haptics.Fallback.Heavy,
        ),
    )
    CascadeStyle.Paper -> FeelSet(
        launch = Cue(R.raw.paper_tap, 0.5f, steps = listOf(s(CLICK, 0.5f), s(TICK, 0.8f, 30)), fallback = Haptics.Fallback.Click),
        letterSound = R.raw.paper_flick, letterVolume = 0.3f, letterScale = PENTATONIC, letterSteps = listOf(s(TICK, 0.4f)),
        open = Cue(R.raw.paper_fold, 0.45f, steps = listOf(s(TICK, 0.3f), s(TICK, 0.5f, 35), s(TICK, 0.4f, 35))),
        longPress = Cue(R.raw.paper_fold, 0.3f, 1.2f, listOf(s(QUICK_RISE, 0.4f), s(CLICK, 0.5f)), Haptics.Fallback.Click),
        swap = Cue(R.raw.paper_flick, 0.3f, steps = listOf(s(TICK, 0.6f))),
        toggle = Cue(R.raw.paper_flick, 0.3f, 0.8f, listOf(s(TICK, 0.5f))),
        switchTo = Cue(R.raw.paper_switch, 0.6f, steps = listOf(s(QUICK_RISE, 0.6f), s(TICK, 0.8f, 300)), fallback = Haptics.Fallback.Heavy),
    )
    CascadeStyle.Neon -> FeelSet(
        launch = Cue(R.raw.neon_blip, 0.45f, steps = listOf(s(CLICK, 1f)), fallback = Haptics.Fallback.Click),
        letterSound = R.raw.neon_blip, letterVolume = 0.22f, letterScale = MINOR_PENTA, letterSteps = listOf(s(TICK, 1f)),
        open = Cue(R.raw.neon_buzz, 0.35f, steps = listOf(s(TICK, 0.5f), s(TICK, 0.5f, 25), s(TICK, 0.7f, 25))),
        longPress = Cue(R.raw.neon_buzz, 0.3f, 1.3f, listOf(s(QUICK_RISE, 0.7f), s(CLICK, 0.8f)), Haptics.Fallback.Click),
        swap = Cue(R.raw.neon_blip, 0.25f, 1.5f, listOf(s(TICK, 0.9f))),
        toggle = Cue(R.raw.neon_blip, 0.22f, 1.2f, listOf(s(CLICK, 0.5f))),
        switchTo = Cue(
            R.raw.neon_zap, 0.55f,
            steps = listOf(s(TICK, 0.4f), s(TICK, 0.9f, 60), s(TICK, 0.3f, 40), s(TICK, 1f, 90), s(QUICK_FALL, 0.7f, 60)),
            fallback = Haptics.Fallback.Heavy,
        ),
    )
    CascadeStyle.Cosmos -> FeelSet(
        launch = Cue(R.raw.cosmos_chime, 0.4f, steps = listOf(s(SLOW_RISE, 0.4f), s(TICK, 0.3f, 20))),
        letterSound = R.raw.cosmos_chime, letterVolume = 0.18f, letterScale = LYDIAN, letterSteps = listOf(s(LOW_TICK, 0.4f)),
        open = Cue(R.raw.cosmos_sparkle, 0.4f, steps = listOf(s(SLOW_RISE, 0.3f))),
        longPress = Cue(R.raw.cosmos_sparkle, 0.3f, 0.8f, listOf(s(QUICK_RISE, 0.5f)), Haptics.Fallback.Click),
        swap = Cue(R.raw.cosmos_chime, 0.18f, 1.5f, listOf(s(LOW_TICK, 0.5f))),
        toggle = Cue(R.raw.cosmos_sparkle, 0.25f, 1.2f, listOf(s(TICK, 0.3f))),
        switchTo = Cue(
            R.raw.cosmos_switch, 0.55f,
            steps = listOf(s(SLOW_RISE, 0.7f), s(QUICK_FALL, 0.5f, 100), s(TICK, 0.3f, 150)),
            fallback = Haptics.Fallback.Heavy,
        ),
    )
}

/**
 * How the launcher feels: the sound and vibration for each kind of moment, per style.
 */
class Feel(
    private val haptics: Haptics?,
    val kind: CascadeStyle,
    private val soundOn: Boolean,
    private val hapticsOn: Boolean,
) {
    private val set = feelFor(kind)

    private fun play(c: Cue) {
        if (soundOn && c.sound != null) Sounds.play(c.sound, c.volume, c.rate)
        if (hapticsOn && c.steps.isNotEmpty()) haptics?.play(c.steps, c.fallback)
    }

    fun launch() = play(set.launch)
    fun open() = play(set.open)
    fun longPress() = play(set.longPress)
    fun swap() = play(set.swap)
    fun toggle() = play(set.toggle)

    /** Each letter / tab has its own note, so sliding plays a scale. */
    fun letter(index: Int, count: Int) {
        val pos = if (count <= 1) 0f else index.toFloat() / (count - 1)
        val scale = set.letterScale
        val rate = scale[((1f - pos) * (scale.size - 1)).toInt().coerceIn(0, scale.lastIndex)]
        if (soundOn && set.letterSound != null) Sounds.play(set.letterSound, set.letterVolume, rate)
        if (hapticsOn) haptics?.play(set.letterSteps, Haptics.Fallback.Tick)
    }

    /** The big moment: switching style. Plays the style you're switching *to*. */
    fun styleSwitch(to: CascadeStyle) = play(feelFor(to).switchTo)
}

val LocalFeel = staticCompositionLocalOf { Feel(null, CascadeStyle.Classic, soundOn = false, hapticsOn = false) }
