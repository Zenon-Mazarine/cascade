package com.bidule.cascade

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
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

    private fun alias(context: Context) = ComponentName(context, "com.bidule.cascade.HomeAlias")

    /** Whether Cascade is currently offering itself as a home screen at all. */
    fun isHomeEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(alias(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setHomeEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            alias(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /**
     * The "Original" button. Cascade stops being a home screen, so Android falls straight back
     * to the phone's own launcher, with its icons, folders and widgets exactly as they were
     * (Cascade never touched them). Opening Cascade again later lets you switch back.
     */
    fun restoreOriginal(context: Context) {
        val previous = Prefs(context).previousHome
        setHomeEnabled(context, false)
        val home = homeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (previous != null && isInstalled(context, previous)) home.setPackage(previous)
        AppRepository.safeStart(context, home)
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
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
