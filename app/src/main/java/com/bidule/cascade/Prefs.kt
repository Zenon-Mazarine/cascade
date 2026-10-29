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

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_MONO = "mono"
        const val KEY_FAVORITES = "favorites"
        const val KEY_PREVIOUS_HOME = "previous_home"
    }
}
