package com.bidule.cascade

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Renders real Cascade footage for the ads, frame by frame: Compose's clock is paused and
 * advanced exactly 1/30 s between captures, so the result is a smooth 30 fps however slow the
 * device is. Frames land in the app's files dir (frames/<clip>/0000.jpg).
 * Needs a build with -PscreenshotMode=true.
 */
@RunWith(AndroidJUnit4::class)
class FootageTest {

    private companion object {
        val ORB = mapOf(
            "Classic" to (881f to 627f), "Water" to (972f to 457f), "Ink" to (871f to 189f), "Stone" to (967f to 457f),
            "Paper" to (969f to 450f), "Neon" to (864f to 185f), "Cosmos" to (963f to 454f),
        )
    }

    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val dm = ctx.resources.displayMetrics
    private val w = dm.widthPixels.toFloat()
    private val h = dm.heightPixels.toFloat()
    private fun dp(v: Float) = v * dm.density

    private var elapsedMs = 0L

    private fun intent(style: String, theme: String, extra: Intent.() -> Unit = {}): Intent =
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("cascade_style", style)
            .putExtra("cascade_theme", theme)
            .putExtra("cascade_pro", true)
            .putExtra("cascade_clean", true)
            .putExtra("cascade_demo", true)
            .apply(extra)

    private var frameNo = 0L

    /** Advances the clock by one 30 fps frame (33 or 34 ms, so frame n lands exactly on n/30 s). */
    private fun step() {
        frameNo++
        val target = frameNo * 1000 / 30
        val ms = target - elapsedMs
        rule.mainClock.advanceTimeBy(ms)
        elapsedMs = target
    }

    private fun capture(dir: File, index: Int) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, "%04d.jpg".format(index))).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    /**
     * Launches Cascade, lets [warmupMs] pass, then captures [frames] frames.
     * [onFrame] runs before each capture (for touches).
     */
    private fun clip(name: String, intent: Intent, warmupMs: Long, frames: Int, onFrame: (Int) -> Unit = {}) {
        val dir = File(ctx.getExternalFilesDir(null), "frames/$name").apply { mkdirs() }
        rule.mainClock.autoAdvance = false
        elapsedMs = 0
        frameNo = 0
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        try {
            var waited = 0L
            while (waited < warmupMs) {
                rule.mainClock.advanceTimeBy(100)
                waited += 100
            }
            for (i in 0 until frames) {
                onFrame(i)
                step()
                capture(dir, i)
            }
        } finally {
            scenario.close()
        }
    }

    private fun switchAt(to: String, from: String) {
        val (x, y) = ORB.getValue(from)
        val target = com.bidule.cascade.ui.CascadeStyle.valueOf(to)
        rule.runOnUiThread {
            DebugFlags.switchRequest.value = target to Offset(x * w / 1080f, y * w / 1080f)
        }
    }

    @Test
    fun footage() {
        // Style switches: settle for 2.5 s, then the switch starts on frame 18 (0.6 s in).
        val pairs = listOf(
            Triple("Classic", "Dark", "Water"),
            Triple("Water", "Dark", "Ink"),
            Triple("Ink", "Dark", "Stone"),
            Triple("Stone", "Dark", "Paper"),
            Triple("Paper", "Dark", "Neon"),
            Triple("Neon", "Dark", "Cosmos"),
            Triple("Cosmos", "Dark", "Water"),
        )
        for ((from, theme, to) in pairs) {
            clip("switch-${from.lowercase()}-${to.lowercase()}", intent(from, theme), 2500, 90) { i ->
                if (i == 18) switchAt(to, from)
            }
        }

        // Idle loops: favorites cascading in, bubbles bobbing, signs flickering, stars drifting.
        clip("idle-water", intent("Water", "Dark"), 0, 105)
        clip("idle-neon", intent("Neon", "Dark"), 0, 75)
        clip("idle-cosmos", intent("Cosmos", "Dark"), 0, 75)

        // Ink: a thumb sliding down the dictionary tabs.
        val tabX = w - dp(14f)
        val y0 = h * 0.30f
        val y1 = h * 0.74f
        clip("scrub-ink", intent("Ink", "Light"), 1500, 110) { i ->
            val t = (i - 10) / 70f
            rule.onRoot().performTouchInput {
                when {
                    i == 10 -> down(Offset(tabX, y0))
                    i in 11..80 -> moveTo(Offset(tabX, y0 + (y1 - y0) * t))
                    i == 81 -> up()
                }
            }
        }

        // Water: pulling the water up for every app.
        val cx = w / 2
        clip("pull-water", intent("Water", "Dark"), 2500, 90) { i ->
            rule.onRoot().performTouchInput {
                when {
                    i == 15 -> down(Offset(cx, h * 0.88f))
                    i in 16..33 -> moveTo(Offset(cx, h * 0.88f - (h * 0.42f) * (i - 15) / 18f))
                    i == 34 -> up()
                }
            }
        }
    }
}
