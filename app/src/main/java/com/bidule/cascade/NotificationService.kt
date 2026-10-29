package com.bidule.cascade

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotifPreview(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
)

/** Latest notifications per package, newest first. Empty until notification access is granted. */
object NotificationStore {
    private val _state = MutableStateFlow<Map<String, List<NotifPreview>>>(emptyMap())
    val state: StateFlow<Map<String, List<NotifPreview>>> = _state.asStateFlow()

    internal fun set(value: Map<String, List<NotifPreview>>) {
        _state.value = value
    }
}

class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        NotificationStore.set(emptyMap())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        val active = try {
            activeNotifications?.toList().orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
        NotificationStore.set(
            active
                .asSequence()
                .filter { !it.isOngoing && it.packageName != packageName }
                .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .mapNotNull { it.toPreview() }
                .groupBy { it.packageName }
                .mapValues { (_, list) -> list.sortedByDescending { it.postTime } }
        )
    }

    private fun StatusBarNotification.toPreview(): NotifPreview? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return NotifPreview(
            key = key,
            packageName = packageName,
            title = title,
            text = text,
            postTime = postTime,
            contentIntent = notification.contentIntent,
            autoCancel = notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
        )
    }

    companion object {
        @Volatile
        var instance: NotificationService? = null
            private set

        fun dismiss(preview: NotifPreview) {
            instance?.cancelNotification(preview.key)
        }

        /** Opens what the notification points to, or the app itself as a fallback. */
        fun open(context: Context, preview: NotifPreview, app: AppEntry) {
            val pi = preview.contentIntent
            val sent = if (pi != null) {
                try {
                    val options = if (Build.VERSION.SDK_INT >= 34) {
                        ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            )
                            .toBundle()
                    } else null
                    pi.send(context, 0, null, null, null, null, options)
                    true
                } catch (e: PendingIntent.CanceledException) {
                    false
                }
            } else false

            if (!sent) AppRepository.launch(context, app)
            // The system only auto-cancels when *it* handles the tap, so we do it ourselves.
            if (preview.autoCancel) dismiss(preview)
        }
    }
}
