package com.coucou.android.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import com.coucou.android.MainActivity
import com.coucou.android.R
import com.coucou.android.core.ApprovalSheetPlan
import com.coucou.android.core.Glance
import com.coucou.android.core.GlanceTone
import com.coucou.android.core.IslandSpec
import com.coucou.android.core.LiveProgress
import com.coucou.android.core.LiveUpdate
import com.coucou.android.core.LiveUpdatePlan
import com.coucou.android.core.LiveUpdateSupport
import com.coucou.android.core.OverlayPolicy
import com.coucou.android.core.SurfacePolicy
import com.coucou.android.core.SafeText
import com.coucou.android.core.ToolLabels
import com.coucou.android.link.ApprovalRequest
import com.coucou.android.mochi.BotState

/**
 * Notifications: one quiet ongoing notification for the link, and a loud one per approval.
 * Deny acts straight from the notification. Allow always opens the app for the biometric
 * check first, and the command is hidden on the lock screen. With the Now Bar chosen in
 * Settings, also the Live Update about the agent at work ([showLive]).
 */
class Notifications(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        nm.deleteNotificationChannel("approvals")
        nm.deleteNotificationChannel("approvals_v2")
        nm.createNotificationChannel(
            NotificationChannel(CH_LINK, "Connection", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows that Coucou is connected to your computer"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATES, "Updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "An agent finished, failed or has a question"
                setShowBadge(false)
            },
        )
        // The same request while the pill is on screen: in the shade only, no heads-up and no sound,
        // so the pill and the notification never overlap at the top of the screen.
        nm.createNotificationChannel(
            NotificationChannel(CH_APPROVAL_QUIET, "Approvals (quiet)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A waiting request, kept in the shade while the pill shows it"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_APPROVAL, "Approvals", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "An agent is waiting for your permission"
                // A short buzz, so a request is felt even when the notification volume is 0 (a channel cannot change
                // later, which is why this one has a new id).
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 150, 500)
                // Mochi's own approval sound, bundled in res/raw.
                setSound(
                    Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.approval}"),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
                )
            },
        )
        // The Live Update: silent, but above "low" so the lock screen and Samsung's Now Bar show it (a Live Update may never be on
        // a channel of minimum importance). Only used when the user picks the Now Bar in Settings.
        nm.createNotificationChannel(
            NotificationChannel(CH_LIVE, "Live Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "What your agents are doing, in the status bar, on the lock screen and in Samsung's Now Bar"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    private fun open(fp: String?, allow: Boolean, code: Int): PendingIntent {
        // A request goes through LaunchActivity (not exported); a plain open needs no extras and goes straight to the app.
        val i = Intent(context, if (fp != null) LaunchActivity::class.java else MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (fp != null) { putExtra(EXTRA_FP, fp); putExtra(EXTRA_ALLOW, allow) } }
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * The quiet notification that keeps the link alive: the headline of [Glance] as its title, the detail under it and,
     * when expanded, one line per agent ("Claude Code: Working"). Only states, never a command or a path. On a locked
     * screen it shows the plain "Connected" line, like the other notifications of the app.
     */
    fun ongoing(model: AppModel?): Notification = ongoing(model?.glance())

    private fun ongoing(g: Glance?): Notification {
        val title = g?.headline ?: context.getString(R.string.status_connecting)
        val detail = g?.detail.orEmpty()
        val b = Notification.Builder(context, CH_LINK)
            .setSmallIcon(R.drawable.ic_stat_mochi)
            .setContentTitle(title)
            .setContentText(detail)
            .setColor(g?.tone?.argb ?: GlanceTone.CALM.argb)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, CH_LINK).setSmallIcon(R.drawable.ic_stat_mochi)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(if (g?.connected == true) context.getString(R.string.status_connected) else context.getString(R.string.status_not_connected))
                    .build(),
            )
            .setContentIntent(open(null, false, 0))
        if (g != null && g.lines.isNotEmpty()) {
            b.setStyle(Notification.BigTextStyle().bigText(g.lines.joinToString("\n") { "${it.agent}: ${it.text}" }).setSummaryText(detail))
        }
        return b.build()
    }

    fun updateOngoing(model: AppModel, g: Glance = model.glance()) {
        if (model.mode == Mode.PAIRED) runCatching { nm.notify(ONGOING_ID, ongoing(g)) }
    }

    fun showApproval(r: ApprovalRequest, agentName: String, alert: OverlayPolicy.ApprovalAlert = OverlayPolicy.ApprovalAlert.HEADS_UP) {
        val channel = if (alert == OverlayPolicy.ApprovalAlert.QUIET) CH_APPROVAL_QUIET else CH_APPROVAL
        Log.d("CoucouOverlay", "approval notification: $alert")
        val id = idFor(r.fingerprint)
        val deny = PendingIntent.getBroadcast(
            context, id, Intent(context, ActionReceiver::class.java).setAction(ACTION_DENY).putExtra(EXTRA_FP, r.fingerprint),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_mochi).setContentTitle(context.getString(R.string.approval_title)).setContentText(context.getString(R.string.notif_unlock)).build()
        val n = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_mochi)
            .setContentTitle(agentName)
            .setSubText(context.getString(R.string.approval_title))
            // A label and at most the program's name: the exact command is on the approval sheet, behind a tap.
            .setContentText(SafeText.requestLabel(r.tool, r.command))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setTimeoutAfter(com.coucou.android.link.Protocol.APPROVAL_TTL_MS)
            .setContentIntent(open(r.fingerprint, false, id))
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_deny), deny).build())
            .apply {
                // A command the phone cannot show in full is answered on the computer: no Allow here.
                if (ApprovalSheetPlan.canAllowHere(r)) addAction(Notification.Action.Builder(null, context.getString(R.string.action_allow), open(r.fingerprint, true, id + 1)).build())
            }
            .build()
        runCatching { nm.notify(id, n) } // POST_NOTIFICATIONS may be denied: the in-app card still works
        if (alert == OverlayPolicy.ApprovalAlert.HEADS_UP) wakeScreen()
    }

    /**
     * A request on a sleeping phone lights the screen for a few seconds, so the lock screen shows the notification
     * ("Unlock to see the request"). The phone stays locked; nothing about the command is shown.
     */
    @Suppress("DEPRECATION")
    private fun wakeScreen() = runCatching {
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        if (pm.isInteractive) return@runCatching
        pm.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "coucou:approval")
            .acquire(WAKE_MS)
    }

    fun cancelApproval(fp: String) = nm.cancel(idFor(fp))

    /** An agent finished, failed, hit a limit or asks something. Quiet: Mochi's own sound and the pill are the alerts. */
    fun showStatus(pillId: String, agentName: String, state: BotState, statusText: String) {
        val what = context.getString(
            when (state) {
                BotState.ERROR -> R.string.notif_error
                BotState.QUESTION -> R.string.notif_question
                BotState.RATELIMIT -> R.string.notif_ratelimit
                else -> R.string.notif_finished
            },
        )
        val n = Notification.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_mochi)
            .setContentTitle(agentName)
            .setContentText(what)
            .setSubText(ToolLabels.label(statusText).takeIf { it.isNotBlank() })
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, CH_UPDATES).setSmallIcon(R.drawable.ic_stat_mochi)
                    .setContentTitle(context.getString(R.string.app_name)).setContentText(what).build(),
            )
            .setAutoCancel(true)
            .setContentIntent(open(null, false, 0))
            .build()
        runCatching { nm.notify(STATUS_TAG, statusId(pillId), n) }
    }

    fun cancelStatus(pillId: String) = runCatching { nm.cancel(STATUS_TAG, statusId(pillId)) }

    /** Coucou is on screen: the notices are redundant. */
    fun cancelAllStatus() {
        runCatching { nm.activeNotifications.filter { it.tag == STATUS_TAG }.forEach { nm.cancel(it.tag, it.id) } }
    }

    private fun statusId(pillId: String) = pillId.hashCode() and 0x3fffffff

    /** Opens the app on the biometric prompt for this request (the same as tapping Allow in the notification). */
    fun openAllow(fp: String) {
        Log.d("CoucouLaunch", "open the app on Allow (tap on the pill)")
        runCatching { open(fp, true, idFor(fp) + 1).send() }
    }

    fun openApp() {
        Log.d("CoucouLaunch", "open the app (tap on the pill)")
        runCatching { open(null, false, 0).send() }
    }

    /**
     * The Live Update (Settings > Display > Now Bar): one promoted ongoing notification about the agent the island would show, with
     * public, documented APIs only. It asks Android to promote it with the extra NotificationCompat.Builder#setRequestPromotedOngoing
     * writes (Notification.EXTRA_REQUEST_PROMOTED_ONGOING; the platform's own setter came with API 36.1), and keeps every rule of a
     * Live Update: ongoing, a title, the standard or the progress style, not colorized, no custom view, not a group summary, a channel
     * above minimum importance. Android, then the phone maker, decide where it shows (the status bar chip, the lock screen, the top of
     * the shade, Samsung's Now Bar): asking is all an app can do. Names and plain words only, as on the island; tapping it opens Coucou.
     *
     * Returns whether it has everything Android needs to promote it (Notification.hasPromotableCharacteristics), null before Android 16.
     */
    fun showLive(u: LiveUpdate): Boolean? {
        val dismissed = PendingIntent.getBroadcast(
            context, LIVE_ID, Intent(context, ActionReceiver::class.java).setAction(ACTION_LIVE_DISMISSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = Notification.Builder(context, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat_mochi)
            .setContentTitle(u.title)
            .setContentText(u.text)
            .setSubText(u.status)
            .setColor(u.tone.argb) // an accent only: a Live Update must not be colorized
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(if (u.kind == IslandSpec.Kind.WORKING) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, CH_LIVE).setSmallIcon(R.drawable.ic_stat_mochi)
                    .setContentTitle(u.title).setContentText(u.publicText).build(),
            )
            .setContentIntent(open(null, false, 0))
            // Swiped away: not posted again until what it was about has ended (Android's guidance for Live Updates).
            .setDeleteIntent(dismissed)
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            b.setShortCriticalText(u.chip.take(LiveUpdatePlan.CHIP_MAX))
            progressStyle(u)?.let { b.setStyle(it) }
        }
        val n = b.build()
        runCatching { nm.notify(LIVE_TAG, LIVE_ID, n) } // POST_NOTIFICATIONS may be denied: the island and the app still work
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return null
        val promotable = n.hasPromotableCharacteristics()
        Log.d("CoucouLive", "live update ${u.kind}, promotable=$promotable")
        return promotable
    }

    /** The bar: moving while the agent works, its steps when the computer counts them, full when it has finished. */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun progressStyle(u: LiveUpdate): Notification.ProgressStyle? = when (val p = u.progress) {
        LiveProgress.None -> null
        LiveProgress.Moving -> Notification.ProgressStyle().setProgressIndeterminate(true)
        is LiveProgress.Steps -> Notification.ProgressStyle().setStyledByProgress(true)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(p.total).setColor(u.tone.argb)))
            .setProgress(p.done)
        LiveProgress.Full -> Notification.ProgressStyle()
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(1).setColor(u.tone.argb)))
            .setProgress(1)
    }

    fun cancelLive() {
        runCatching { nm.cancel(LIVE_TAG, LIVE_ID) }
    }

    /** Whether Android shows Coucou's Live Updates now; the user can turn them off in Android's settings at any time. */
    fun liveSupport(): LiveUpdateSupport {
        val enabled = runCatching { nm.areNotificationsEnabled() }.getOrDefault(true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return SurfacePolicy.support(Build.VERSION.SDK_INT, enabled, canPostPromoted = false)
        val promoted = runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
        return SurfacePolicy.support(Build.VERSION.SDK_INT, enabled, promoted)
    }

    /**
     * Whether Android promoted the Live Update that is posted now (FLAG_PROMOTED_ONGOING, which the system sets and the posting app may
     * read). Null when none is posted, or before Android 16. It says nothing about the Now Bar: One UI may apply rules of its own.
     */
    fun livePromoted(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return null
        val posted = runCatching { nm.activeNotifications.firstOrNull { it.tag == LIVE_TAG && it.id == LIVE_ID } }.getOrNull() ?: return null
        return (posted.notification.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
    }

    companion object {
        const val CH_LINK = "link"
        private const val WAKE_MS = 6_000L
        const val CH_APPROVAL = "approvals_v3"
        const val CH_APPROVAL_QUIET = "approvals_quiet"
        const val CH_UPDATES = "updates"
        private const val STATUS_TAG = "status"
        const val ONGOING_ID = 1
        const val ACTION_DENY = "com.coucou.android.DENY"
        const val CH_LIVE = "live_updates"
        /** The Live Update has a tag of its own, so its id can never meet a request's. */
        const val LIVE_TAG = "live"
        const val LIVE_ID = 3
        const val ACTION_LIVE_DISMISSED = "com.coucou.android.LIVE_DISMISSED"
        /** The documented value of Notification.EXTRA_REQUEST_PROMOTED_ONGOING (public in API 36.1; NotificationCompat writes the same). */
        const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        const val EXTRA_FP = "fingerprint"
        const val EXTRA_ALLOW = "allow"
        /** Even ids for the request, +1 for its Allow action; never collides with ONGOING_ID. */
        fun idFor(fp: String) = (fp.hashCode() and 0x3fffffff) * 2 + 2
    }
}

/**
 * Handles the Deny button, and the Live Update being swiped away. Denying needs no biometric check: it can only make things safer.
 * Not exported: only our own notifications can reach it.
 */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val model = (context.applicationContext as CoucouApp).model
        if (intent.action == Notifications.ACTION_LIVE_DISMISSED) {
            model.liveDismissed()
            return
        }
        if (intent.action != Notifications.ACTION_DENY) return
        val fp = intent.getStringExtra(Notifications.EXTRA_FP) ?: return
        model.decide(fp, allow = false)
    }
}
