package com.bidule.cascade

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny interface sounds (synthesized by tools/make_sounds.py). They play through the system
 * "touch sounds" channel and stay silent when the phone is on vibrate or silent.
 */
object Sounds {
    private val ALL = intArrayOf(
        R.raw.water_drop, R.raw.water_bubble, R.raw.water_soft, R.raw.water_splash,
        R.raw.ink_pluck, R.raw.ink_tap, R.raw.ink_brush, R.raw.ink_stamp, R.raw.ink_switch,
        R.raw.classic_tick,
        R.raw.stone_click, R.raw.stone_tap, R.raw.stone_rake, R.raw.stone_switch,
        R.raw.paper_tap, R.raw.paper_fold, R.raw.paper_flick, R.raw.paper_switch,
        R.raw.neon_blip, R.raw.neon_buzz, R.raw.neon_zap,
        R.raw.cosmos_chime, R.raw.cosmos_sparkle, R.raw.cosmos_switch,
    )

    private var pool: SoundPool? = null
    private var audio: AudioManager? = null
    private val ids = ConcurrentHashMap<Int, Int>()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()

    fun init(context: Context) {
        if (pool != null) return
        val app = context.applicationContext
        audio = app.getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build().also { p ->
            p.setOnLoadCompleteListener { _, soundId, status -> if (status == 0) loaded.add(soundId) }
            ALL.forEach { res -> ids[res] = p.load(app, res, 1) }
        }
    }

    /** [rate] shifts the pitch (0.5 = an octave down, 2 = an octave up). */
    fun play(res: Int, volume: Float, rate: Float = 1f) {
        val p = pool ?: return
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = ids[res] ?: return
        if (id !in loaded) return
        p.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }
}
