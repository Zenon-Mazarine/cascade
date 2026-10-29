package com.bidule.cascade

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Everything about "which app is the home screen", including the way back to the old one.
 *
 * Android never lets an app pick the default home screen on the user's behalf, so "going back"
 * means opening the system's Home app setting with the old launcher ready to tap. The old
 * launcher's layout (icons, folders, widgets) lives in its own storage, and Cascade never touches it.
 */
object HomeUtils {

    /** "android" is the system chooser; the others are setup/fallback screens, not real launchers. */
    private val NOT_REAL_LAUNCHERS = setOf("android", "com.android.settings", "com.android.provision")

    data class HomeApp(val packageName: String, val label: String, val icon: ImageBitmap)

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun defaultHomePackage(context: Context): String? =
        context.packageManager
            .resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

    fun isDefaultHome(context: Context): Boolean = defaultHomePackage(context) == context.packageName

    /** Called on every resume: while Cascade isn't the default, remember whichever launcher is. */
    fun rememberPreviousHome(context: Context) {
        val pkg = defaultHomePackage(context) ?: return
        if (pkg == context.packageName || pkg in NOT_REAL_LAUNCHERS) return
        Prefs(context).previousHome = pkg
    }

    /** Other installed home screens, the remembered one first. */
    fun otherHomeApps(context: Context): List<HomeApp> {
        val pm = context.packageManager
        val px = (40 * context.resources.displayMetrics.density).toInt()
        val previous = Prefs(context).previousHome
        return pm.queryIntentActivities(homeIntent(), 0)
            .filter {
                val pkg = it.activityInfo.packageName
                pkg != context.packageName && pkg !in NOT_REAL_LAUNCHERS
            }
            .distinctBy { it.activityInfo.packageName }
            .map {
                HomeApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    icon = it.loadIcon(pm).toBitmap(px, px).asImageBitmap(),
                )
            }
            .sortedByDescending { it.packageName == previous }
    }

    /** Opens Settings › Default apps › Home app. */
    fun openHomeSettings(context: Context) {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            AppRepository.safeStart(
                context,
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Shows the old home screen right now, without changing the default. */
    fun peekHome(context: Context, packageName: String) {
        AppRepository.safeStart(
            context,
            homeIntent().setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Pulls down the notification shade, like swiping down on most home screens. */
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        try {
            val statusBar = context.getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(statusBar)
        } catch (e: Throwable) {
            // Some phones block this; swiping down just does nothing there.
        }
    }
}
