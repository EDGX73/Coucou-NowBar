package com.coucou.android.core

/**
 * Where an agent's activity shows outside the app: the choice in Settings > Display.
 *
 * - [NOW_BAR]: an Android Live Update, a promoted ongoing notification (Android 16 and later). Android shows it at the top of the
 *   shade, on the lock screen and as a chip in the status bar; Samsung's One UI shows Live Updates in its Now Bar. Coucou can only
 *   ask for it: whether a surface shows it is up to Android and the phone maker (see [SurfacePolicy.support]).
 * - [ISLAND]: the black island over other apps, as before (it needs "display over other apps").
 * - [BOTH]: the two together.
 * - [NOTIFICATIONS]: neither of them, the ordinary notifications only.
 *
 * Every mode keeps the ordinary notifications (the link, the requests with Deny and Allow, finished or failed agents): they are how
 * a request reaches a locked phone. [key] is what is saved: never rename one.
 */
enum class SurfaceMode(val key: String) {
    NOW_BAR("now_bar"),
    ISLAND("island"),
    BOTH("both"),
    NOTIFICATIONS("notifications");

    val wantsLiveUpdate: Boolean get() = this == NOW_BAR || this == BOTH
    val wantsIsland: Boolean get() = this == ISLAND || this == BOTH

    companion object {
        /** A saved key. With none (the app before this choice existed) it is the island switch it had: on is [ISLAND], off is [NOTIFICATIONS]. */
        fun parse(key: String, islandSwitch: Boolean): SurfaceMode =
            entries.firstOrNull { it.key == key } ?: if (islandSwitch) ISLAND else NOTIFICATIONS
    }
}

/** Whether this phone shows Coucou's Live Update right now, and if not, why. */
enum class LiveUpdateSupport {
    READY,
    /** Android 15 or older: there are no Live Updates. */
    OLD_ANDROID,
    /** Notifications are off for Coucou: nothing can show. */
    NOTIFICATIONS_OFF,
    /** The user turned Coucou's Live Updates off in Android's settings (NotificationManager.canPostPromotedNotifications is false). */
    TURNED_OFF,
}

/** What shows: the Live Update, the island, and whether the island only stands in for a Live Update this phone cannot show. */
data class SurfacePlan(val liveUpdate: Boolean, val island: Boolean, val islandStandsIn: Boolean)

object SurfacePolicy {
    /** Android 16 (API 36): FLAG_PROMOTED_ONGOING, canPostPromotedNotifications, ProgressStyle and the status bar chip's text. */
    const val LIVE_UPDATES_SDK = 36

    fun support(sdkInt: Int, notificationsEnabled: Boolean, canPostPromoted: Boolean): LiveUpdateSupport = when {
        sdkInt < LIVE_UPDATES_SDK -> LiveUpdateSupport.OLD_ANDROID
        !notificationsEnabled -> LiveUpdateSupport.NOTIFICATIONS_OFF
        !canPostPromoted -> LiveUpdateSupport.TURNED_OFF
        else -> LiveUpdateSupport.READY
    }

    /**
     * Now Bar on a phone that cannot show a Live Update falls back to the island, so other phones keep what they had. Both keeps
     * the island either way. The island still needs its system permission and Coucou off screen ([OverlayPolicy.blocker]).
     */
    fun plan(mode: SurfaceMode, support: LiveUpdateSupport): SurfacePlan {
        val live = mode.wantsLiveUpdate && support == LiveUpdateSupport.READY
        val standsIn = mode == SurfaceMode.NOW_BAR && !live
        return SurfacePlan(liveUpdate = live, island = mode.wantsIsland || standsIn, islandStandsIn = standsIn)
    }

    /**
     * The sentence under the choice in Settings: what this phone does with Coucou's Live Update. [promoted] is what Android did with
     * the one posted now (FLAG_PROMOTED_ONGOING), null when none is posted.
     */
    fun line(mode: SurfaceMode, support: LiveUpdateSupport, promoted: Boolean?): LiveUpdateLine = when {
        !mode.wantsLiveUpdate -> LiveUpdateLine.NONE
        support == LiveUpdateSupport.OLD_ANDROID -> LiveUpdateLine.OLD_ANDROID
        support == LiveUpdateSupport.NOTIFICATIONS_OFF -> LiveUpdateLine.NOTIFICATIONS_OFF
        support == LiveUpdateSupport.TURNED_OFF -> LiveUpdateLine.TURNED_OFF
        promoted == true -> LiveUpdateLine.SHOWN_PROMOTED
        promoted == false -> LiveUpdateLine.SHOWN_PLAIN
        else -> LiveUpdateLine.READY
    }
}

/** What Settings says about the Live Update. Android's own switch is offered with every line but [NONE] and [OLD_ANDROID]. */
enum class LiveUpdateLine {
    NONE, READY, SHOWN_PROMOTED, SHOWN_PLAIN, OLD_ANDROID, NOTIFICATIONS_OFF, TURNED_OFF;

    val offersSettings: Boolean get() = this != NONE && this != OLD_ANDROID
}
