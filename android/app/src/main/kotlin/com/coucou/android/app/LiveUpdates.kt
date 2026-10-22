package com.coucou.android.app

import android.os.Handler
import android.os.Looper
import com.coucou.android.core.IslandSpec
import com.coucou.android.core.IslandTimeline
import com.coucou.android.core.LiveUpdate
import com.coucou.android.core.LiveUpdatePlan
import com.coucou.android.link.SessionInfo

/**
 * When the Live Update is posted (Settings > Display > Now Bar): by the island's own rules ([IslandTimeline], unit-tested), so
 * both always agree. It shows while an agent works, asks for permission or has a question, shows how it ended for 10 s, then goes.
 * What it says comes from [LiveUpdatePlan]; how it is posted from [Notifications.showLive]. The same unchanged content is never
 * posted twice, and nothing runs while it is hidden: one timer only, for the end of a result's 10 s.
 */
class LiveUpdates(private val notifier: Notifications, private val clock: () -> Double) {
    private val main = Handler(Looper.getMainLooper())
    private val timeline = IslandTimeline(clock)
    private var sessions: List<SessionInfo> = emptyList()
    private var posted: LiveUpdate? = null
    /** The user swiped it away: it stays away until what it was about has ended (Android asks apps not to post it again). */
    private var dismissed = false
    private val deadline = Runnable { timeline.tick(); sync() }

    /** Whether the last one posted has everything Android needs to promote it (null before Android 16 or before any). */
    var promotable: Boolean? = null
        private set

    init {
        notifier.cancelLive() // one left by an earlier run of the app that ended without removing it
    }

    /** What goes on now (the island's [com.coucou.android.core.IslandPlan.active]), with the sessions for their step counts. */
    fun setActive(spec: IslandSpec?, sessions: List<SessionInfo>) {
        this.sessions = sessions
        timeline.setActive(spec)
        sync()
    }

    /** An agent finished, failed or hit a limit (the island's [com.coucou.android.core.IslandPlan.flash]): shown for 10 s. */
    fun flash(spec: IslandSpec, sessions: List<SessionInfo>) {
        this.sessions = sessions
        timeline.flash(spec)
        sync()
    }

    /** Removed at once: the Now Bar is no longer chosen, the link dropped, or the app left demo mode. */
    fun hide() {
        timeline.dismiss()
        sync()
    }

    fun onDismissed() {
        dismissed = true
        posted = null
    }

    private fun sync() {
        main.removeCallbacks(deadline)
        // There is no animation to wait for: going up is going away.
        if (timeline.phase == IslandTimeline.Phase.RETRACTING) timeline.retractFinished()
        val current = timeline.current
        if (timeline.phase != IslandTimeline.Phase.OPEN || current == null) {
            dismissed = false // what it was about has ended: the next activity may show again
            if (posted != null) notifier.cancelLive()
            posted = null
            return
        }
        if (!dismissed) {
            val next = LiveUpdatePlan.build(current, sessions)
            if (next != posted) {
                promotable = notifier.showLive(next)
                posted = next
            }
        }
        timeline.nextDeadline()?.let { main.postDelayed(deadline, (it - clock()).toLong().coerceAtLeast(0)) }
    }
}
