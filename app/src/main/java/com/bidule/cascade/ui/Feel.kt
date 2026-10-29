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

/**
 * How the launcher feels: the sound and vibration for each kind of moment, per style.
 * Water is soft and bubbly; Ink is crisp, like a brush tapping paper and a seal being stamped.
 */
class Feel(
    private val haptics: Haptics?,
    val kind: CascadeStyle,
    private val soundOn: Boolean,
    private val hapticsOn: Boolean,
) {
    private fun buzz(steps: List<Haptics.Step>, fallback: Haptics.Fallback) {
        if (hapticsOn) haptics?.play(steps, fallback)
    }

    private fun sound(res: Int, volume: Float, rate: Float = 1f) {
        if (soundOn) Sounds.play(res, volume, rate)
    }

    /** Opening an app. */
    fun launch() = when (kind) {
        CascadeStyle.Water -> {
            sound(R.raw.water_drop, 0.5f)
            buzz(listOf(s(LOW_TICK, 0.7f), s(TICK, 0.35f, 45)), Haptics.Fallback.Click)
        }
        CascadeStyle.Ink -> {
            sound(R.raw.ink_stamp, 0.55f)
            buzz(listOf(s(CLICK, 0.9f), s(THUD, 0.45f, 15)), Haptics.Fallback.Click)
        }
        CascadeStyle.Classic -> {
            sound(R.raw.classic_tick, 0.25f)
            buzz(listOf(s(CLICK, 0.6f)), Haptics.Fallback.Click)
        }
    }

    /** Sliding over a letter on the rail. Each letter has its own note, so the alphabet plays a scale. */
    fun letter(index: Int, count: Int) {
        val pos = if (count <= 1) 0f else index.toFloat() / (count - 1)
        when (kind) {
            CascadeStyle.Water -> {
                sound(R.raw.water_bubble, 0.32f, noteFor(pos, PENTATONIC))
                buzz(listOf(s(LOW_TICK, 0.55f)), Haptics.Fallback.Tick)
            }
            CascadeStyle.Ink -> {
                sound(R.raw.ink_pluck, 0.28f, noteFor(pos, HIRAJOSHI))
                buzz(listOf(s(TICK, 0.75f)), Haptics.Fallback.Tick)
            }
            CascadeStyle.Classic -> buzz(listOf(s(TICK, 0.5f)), Haptics.Fallback.Tick)
        }
    }

    /** A panel or folder opening. */
    fun open() = when (kind) {
        CascadeStyle.Water -> {
            sound(R.raw.water_soft, 0.4f)
            buzz(listOf(s(QUICK_RISE, 0.35f)), Haptics.Fallback.Tick)
        }
        CascadeStyle.Ink -> {
            sound(R.raw.ink_brush, 0.45f)
            buzz(listOf(s(QUICK_RISE, 0.3f), s(TICK, 0.5f, 90)), Haptics.Fallback.Tick)
        }
        CascadeStyle.Classic -> buzz(listOf(s(TICK, 0.4f)), Haptics.Fallback.Tick)
    }

    fun longPress() = when (kind) {
        CascadeStyle.Water -> {
            sound(R.raw.water_soft, 0.3f, 0.8f)
            buzz(listOf(s(LOW_TICK, 0.8f), s(LOW_TICK, 0.5f, 60)), Haptics.Fallback.Click)
        }
        CascadeStyle.Ink -> {
            sound(R.raw.ink_brush, 0.3f, 1.3f)
            buzz(listOf(s(CLICK, 0.7f)), Haptics.Fallback.Click)
        }
        CascadeStyle.Classic -> buzz(listOf(s(CLICK, 0.8f)), Haptics.Fallback.Click)
    }

    /** Reordering swaps, swipe thresholds: small confirmations. */
    fun swap() = when (kind) {
        CascadeStyle.Water -> {
            sound(R.raw.water_bubble, 0.25f, 1.5f)
            buzz(listOf(s(LOW_TICK, 0.6f)), Haptics.Fallback.Tick)
        }
        CascadeStyle.Ink -> {
            sound(R.raw.ink_tap, 0.3f)
            buzz(listOf(s(TICK, 0.9f)), Haptics.Fallback.Tick)
        }
        CascadeStyle.Classic -> buzz(listOf(s(TICK, 0.6f)), Haptics.Fallback.Tick)
    }

    /** Checkboxes, edit mode, small toggles. */
    fun toggle() = when (kind) {
        CascadeStyle.Water -> {
            sound(R.raw.water_bubble, 0.22f, 1.2f)
            buzz(listOf(s(TICK, 0.4f)), Haptics.Fallback.Tick)
        }
        CascadeStyle.Ink -> {
            sound(R.raw.ink_tap, 0.3f, 0.9f)
            buzz(listOf(s(TICK, 0.5f)), Haptics.Fallback.Tick)
        }
        CascadeStyle.Classic -> buzz(listOf(s(TICK, 0.5f)), Haptics.Fallback.Tick)
    }

    /** The big moment: switching style. Plays the style you're switching *to*. */
    fun styleSwitch(to: CascadeStyle) = when (to) {
        CascadeStyle.Water -> {
            sound(R.raw.water_splash, 0.6f)
            buzz(
                listOf(s(SLOW_RISE, 0.5f), s(QUICK_FALL, 0.6f), s(LOW_TICK, 0.8f, 80), s(LOW_TICK, 0.5f, 70)),
                Haptics.Fallback.Heavy,
            )
        }
        CascadeStyle.Ink -> {
            sound(R.raw.ink_switch, 0.6f)
            buzz(
                listOf(s(TICK, 0.3f), s(TICK, 0.5f, 40), s(TICK, 0.7f, 40), s(THUD, 0.9f, 220)),
                Haptics.Fallback.Heavy,
            )
        }
        CascadeStyle.Classic -> {
            sound(R.raw.classic_tick, 0.4f)
            buzz(listOf(s(CLICK, 0.8f)), Haptics.Fallback.Click)
        }
    }

    private companion object {
        // Pitch ratios, low to high. Top of the alphabet sounds high, bottom sounds low.
        val PENTATONIC = floatArrayOf(0.75f, 0.84375f, 0.9375f, 1.125f, 1.25f, 1.5f, 1.6875f, 1.875f)
        val HIRAJOSHI = floatArrayOf(0.75f, 0.842f, 0.892f, 1.124f, 1.191f, 1.5f, 1.684f, 1.784f)

        fun noteFor(pos: Float, scale: FloatArray): Float {
            val i = ((1f - pos) * (scale.size - 1)).toInt().coerceIn(0, scale.lastIndex)
            return scale[i]
        }
    }
}

val LocalFeel = staticCompositionLocalOf { Feel(null, CascadeStyle.Classic, soundOn = false, hapticsOn = false) }
