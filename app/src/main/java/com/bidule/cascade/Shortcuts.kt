package com.bidule.cascade

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class ShortcutEntry(
    val id: String,
    val label: String,
    val icon: ImageBitmap?,
    val info: ShortcutInfo,
)

/** An app's quick actions ("New message", "Selfie"…), the ones you see when long-pressing in other launchers. */
object Shortcuts {

    sealed interface Result {
        data class Ok(val list: List<ShortcutEntry>) : Result

        /** Android only shares shortcuts with the current default home screen. */
        data object NotDefaultHome : Result
    }

    /** Slow (loads icons): call off the main thread. */
    fun load(context: Context, packageName: String): Result {
        val la = context.getSystemService(LauncherApps::class.java) ?: return Result.Ok(emptyList())
        if (!la.hasShortcutHostPermission()) return Result.NotDefaultHome

        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            )
        val found = try {
            la.getShortcuts(query, Process.myUserHandle()).orEmpty()
        } catch (e: SecurityException) {
            return Result.NotDefaultHome
        } catch (e: IllegalStateException) {
            emptyList()
        }

        val px = (28 * context.resources.displayMetrics.density).toInt()
        val dpi = context.resources.displayMetrics.densityDpi
        return Result.Ok(
            found
                .filter { it.isEnabled }
                .sortedWith(compareBy<ShortcutInfo>({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
                .take(5)
                .map { info ->
                    val icon = try {
                        la.getShortcutIconDrawable(info, dpi)?.toBitmap(px, px)?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                    ShortcutEntry(
                        id = info.id,
                        label = (info.shortLabel ?: info.longLabel ?: "").toString(),
                        icon = icon,
                        info = info,
                    )
                }
        )
    }

    fun start(context: Context, entry: ShortcutEntry) {
        val la = context.getSystemService(LauncherApps::class.java) ?: return
        try {
            la.startShortcut(entry.info, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open that shortcut", Toast.LENGTH_SHORT).show()
        }
    }
}
