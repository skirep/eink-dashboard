package com.eink.launcher.fold

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class MailNotice(val key: String, val provider: String, val title: String,
    val preview: String, val posted: Long, val intent: PendingIntent?)
internal data class MailSnapshot(val connected: Boolean = false,
    val messages: List<MailNotice> = emptyList(), val total: Int = messages.size)

/** Only active Gmail/Outlook notices, in memory. Not an inbox or unread-mail count. */
internal object MailNotifications {
    val packages = setOf("com.google.android.gm", "com.microsoft.office.outlook")
    private val mutable = MutableStateFlow(MailSnapshot())
    val state = mutable.asStateFlow()
    fun publish(value: MailSnapshot) { mutable.value = value }
    fun notice(sbn: StatusBarNotification): MailNotice? {
        if (sbn.packageName !in packages || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val extras = sbn.notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(160).orEmpty()
        if (title.isBlank()) return null
        val preview = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.take(240).orEmpty()
        return MailNotice(sbn.key, if (sbn.packageName == "com.google.android.gm") "Gmail" else "Outlook",
            title, preview, sbn.postTime, sbn.notification.contentIntent)
    }
}

class MailNotificationService : NotificationListenerService() {
    private fun refresh() {
        try {
            val all = activeNotifications.orEmpty().mapNotNull(MailNotifications::notice).sortedByDescending { it.posted }
            MailNotifications.publish(MailSnapshot(true, all.take(20), all.size))
        } catch (_: SecurityException) { MailNotifications.publish(MailSnapshot()) }
    }
    override fun onListenerConnected() { refresh() }
    override fun onNotificationPosted(sbn: StatusBarNotification) { if (sbn.packageName in MailNotifications.packages) refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { if (sbn.packageName in MailNotifications.packages) refresh() }
    override fun onListenerDisconnected() { MailNotifications.publish(MailSnapshot()) }
    override fun onDestroy() { MailNotifications.publish(MailSnapshot()); super.onDestroy() }
}
