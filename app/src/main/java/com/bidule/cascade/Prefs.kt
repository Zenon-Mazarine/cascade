package com.bidule.cascade

import android.content.Context

/** Small wrapper around SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cascade", Context.MODE_PRIVATE)

    /** Pinned apps as flattened component names, in display order. Null until first set. */
    var favorites: List<String>?
        get() = sp.getString(KEY_FAVORITES, null)?.split("|")?.filter { it.isNotBlank() }
        set(value) {
            sp.edit().putString(KEY_FAVORITES, value.orEmpty().joinToString("|")).apply()
        }

    /** Apps in the bookmarks folder, as flattened component names, in display order. */
    var bookmarks: List<String>
        get() = sp.getString(KEY_BOOKMARKS, null)?.split("|")?.filter { it.isNotBlank() }.orEmpty()
        set(value) {
            sp.edit().putString(KEY_BOOKMARKS, value.joinToString("|")).apply()
        }

    /** Package of the home screen that was active before Cascade took over. */
    var previousHome: String?
        get() = sp.getString(KEY_PREVIOUS_HOME, null)
        set(value) {
            sp.edit().putString(KEY_PREVIOUS_HOME, value).apply()
        }

    var theme: String
        get() = sp.getString(KEY_THEME, "Wallpaper") ?: "Wallpaper"
        set(value) {
            sp.edit().putString(KEY_THEME, value).apply()
        }

    var mono: Boolean
        get() = sp.getBoolean(KEY_MONO, false)
        set(value) {
            sp.edit().putBoolean(KEY_MONO, value).apply()
        }

    /** How often each app was opened from Cascade: sizes the bubbles in the Pool. */
    var launchCounts: Map<String, Int>
        get() = sp.getString(KEY_COUNTS, null)
            ?.split("|")
            ?.mapNotNull { e ->
                val i = e.lastIndexOf('=')
                if (i <= 0) null else e.substring(0, i) to (e.substring(i + 1).toIntOrNull() ?: 0)
            }
            ?.toMap()
            .orEmpty()
        set(value) {
            sp.edit().putString(KEY_COUNTS, value.entries.joinToString("|") { "${it.key}=${it.value}" }).apply()
        }

    var proOwned: Boolean
        get() = sp.getBoolean("pro_owned", false)
        set(value) {
            sp.edit().putBoolean("pro_owned", value).apply()
        }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(value) {
            sp.edit().putBoolean("onboarded", value).apply()
        }

    /** End of the current Pro style preview (epoch millis), 0 when none. */
    var trialUntil: Long
        get() = sp.getLong("trial_until", 0L)
        set(value) {
            sp.edit().putLong("trial_until", value).apply()
        }

    /** The free style to fall back to when a preview ends. */
    var lastFreeStyle: String
        get() = sp.getString("last_free_style", "Water") ?: "Water"
        set(value) {
            sp.edit().putString("last_free_style", value).apply()
        }

    /** When each Pro style was last previewed, so previews are once a day per style. */
    fun lastTrialOf(style: String): Long = sp.getLong("trial_$style", 0L)
    fun markTrial(style: String, at: Long) {
        sp.edit().putLong("trial_$style", at).apply()
    }

    var style: String
        get() = sp.getString(KEY_STYLE, "Water") ?: "Water"
        set(value) {
            sp.edit().putString(KEY_STYLE, value).apply()
        }

    var sound: Boolean
        get() = sp.getBoolean(KEY_SOUND, true)
        set(value) {
            sp.edit().putBoolean(KEY_SOUND, value).apply()
        }

    var haptics: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(value) {
            sp.edit().putBoolean(KEY_HAPTICS, value).apply()
        }

    private companion object {
        const val KEY_STYLE = "style"
        const val KEY_COUNTS = "launch_counts"
        const val KEY_SOUND = "sound"
        const val KEY_HAPTICS = "haptics"
        const val KEY_THEME = "theme"
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_MONO = "mono"
        const val KEY_FAVORITES = "favorites"
        const val KEY_PREVIOUS_HOME = "previous_home"
    }
}
