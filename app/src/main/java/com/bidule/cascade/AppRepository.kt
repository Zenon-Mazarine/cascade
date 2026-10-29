package com.bidule.cascade

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Normalizer

data class AppEntry(
    /** Stable id: flattened component name. */
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: ImageBitmap,
    /** A–Z, or '#' for anything else. Accents are stripped, so "Éditeur" files under E. */
    val letter: Char,
    /** Lowercase, accent-free label used for sorting and search. */
    val searchKey: String,
)

object AppRepository {

    private val accents = Regex("\\p{Mn}+")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(accents, "").lowercase()

    private fun letterOf(searchKey: String): Char {
        val c = searchKey.firstOrNull()?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }

    /** All launchable apps except Cascade itself. Slow (loads icons): call off the main thread. */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val iconPx = (48 * context.resources.displayMetrics.density).toInt()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                val info = ri.activityInfo
                val label = ri.loadLabel(pm).toString().trim()
                val component = ComponentName(info.packageName, info.name)
                val key = normalize(label)
                AppEntry(
                    key = component.flattenToString(),
                    label = label,
                    packageName = info.packageName,
                    component = component,
                    icon = ri.loadIcon(pm).toBitmap(iconPx, iconPx).asImageBitmap(),
                    letter = letterOf(key),
                    searchKey = key,
                )
            }
            // A–Z first, then '#' at the end, like the rail.
            .sortedWith(compareBy<AppEntry> { it.letter == '#' }.thenBy { it.searchKey })
    }

    fun launch(context: Context, app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        safeStart(context, intent)
    }

    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(context, intent)
    }

    fun uninstall(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(context, intent)
    }

    fun safeStart(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Couldn't open that", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Couldn't open that", Toast.LENGTH_SHORT).show()
        }
    }
}
