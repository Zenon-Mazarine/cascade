package com.bidule.cascade

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import com.bidule.cascade.ui.CascadeStyle
import com.bidule.cascade.ui.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _favorites = MutableStateFlow(prefs.favorites.orEmpty())
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    private val _bookmarks = MutableStateFlow(prefs.bookmarks)
    val bookmarks: StateFlow<List<String>> = _bookmarks.asStateFlow()

    fun toggleBookmark(key: String) {
        val current = _bookmarks.value
        val next = if (key in current) current - key else current + key
        _bookmarks.value = next
        prefs.bookmarks = next
    }

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.theme } ?: ThemeMode.Wallpaper
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _mono = MutableStateFlow(prefs.mono)
    val mono: StateFlow<Boolean> = _mono.asStateFlow()

    private val _style = MutableStateFlow(
        CascadeStyle.entries.firstOrNull { it.name == prefs.style } ?: CascadeStyle.Water
    )
    val style: StateFlow<CascadeStyle> = _style.asStateFlow()

    private val _sound = MutableStateFlow(prefs.sound)
    val sound: StateFlow<Boolean> = _sound.asStateFlow()

    private val _haptics = MutableStateFlow(prefs.haptics)
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    fun setStyle(style: CascadeStyle) {
        _style.value = style
        prefs.style = style.name
    }

    fun setSound(enabled: Boolean) {
        _sound.value = enabled
        prefs.sound = enabled
    }

    fun setHaptics(enabled: Boolean) {
        _haptics.value = enabled
        prefs.haptics = enabled
    }

    fun setTheme(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.theme = mode.name
    }

    fun setMono(enabled: Boolean) {
        _mono.value = enabled
        prefs.mono = enabled
    }

    /** Reloads the app list whenever something is installed, removed or updated. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reload()
        }
    }

    init {
        reload()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            application, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun reload() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val loaded = withContext(Dispatchers.IO) { AppRepository.load(ctx) }
            _apps.value = loaded
            if (prefs.favorites == null) setFavorites(defaultFavorites(ctx, loaded))
        }
    }

    fun toggleFavorite(key: String) {
        val current = _favorites.value
        setFavorites(if (key in current) current - key else current + key)
    }

    fun moveFavorite(key: String, delta: Int) {
        val list = _favorites.value.toMutableList()
        val from = list.indexOf(key)
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        setFavorites(list)
    }

    /** Drag-to-reorder: puts [key] at [index] in the favorites list. */
    fun moveFavoriteTo(key: String, index: Int) {
        val list = _favorites.value.toMutableList()
        val from = list.indexOf(key)
        if (from < 0 || index !in list.indices || from == index) return
        list.add(index, list.removeAt(from))
        setFavorites(list)
    }

    /** Favorites, bookmarks and look, as a small JSON file. */
    fun exportBackup(): String = JSONObject().apply {
        put("app", "cascade")
        put("version", 1)
        put("favorites", JSONArray(_favorites.value))
        put("bookmarks", JSONArray(_bookmarks.value))
        put("theme", _themeMode.value.name)
        put("mono", _mono.value)
        put("style", _style.value.name)
        put("sound", _sound.value)
        put("haptics", _haptics.value)
    }.toString(2)

    /** Returns false if [text] isn't a Cascade backup. */
    fun importBackup(text: String): Boolean {
        val o = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return false
        }
        if (o.optString("app") != "cascade") return false
        o.optJSONArray("favorites")?.let { setFavorites(it.toStringList()) }
        o.optJSONArray("bookmarks")?.let {
            val list = it.toStringList()
            _bookmarks.value = list
            prefs.bookmarks = list
        }
        ThemeMode.entries.firstOrNull { it.name == o.optString("theme") }?.let(::setTheme)
        if (o.has("mono")) setMono(o.optBoolean("mono"))
        CascadeStyle.entries.firstOrNull { it.name == o.optString("style") }?.let(::setStyle)
        if (o.has("sound")) setSound(o.optBoolean("sound"))
        if (o.has("haptics")) setHaptics(o.optBoolean("haptics"))
        return true
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

    private fun setFavorites(list: List<String>) {
        _favorites.value = list
        prefs.favorites = list
    }

    /** First run: pin the default phone, messages, camera and browser apps. */
    private fun defaultFavorites(ctx: Context, apps: List<AppEntry>): List<String> {
        val pm = ctx.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
        )
        return intents
            .mapNotNull {
                pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }
            .filter { it != "android" }
            .distinct()
            .mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg }?.key }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }
}
