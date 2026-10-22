package com.coucou.android.core

import com.coucou.android.link.SessionInfo
import com.coucou.android.mochi.BotState

/** The bar of a Live Update (Notification.ProgressStyle, Android 16 and later). */
sealed interface LiveProgress {
    /** At work with no known number of steps: a moving bar. */
    data object Moving : LiveProgress
    /** Step [done] of [total], the computer's own count. */
    data class Steps(val done: Int, val total: Int) : LiveProgress
    /** Finished: a full bar. */
    data object Full : LiveProgress
    /** A request, a question, an error or a limit: no bar. */
    data object None : LiveProgress
}

/**
 * What the Live Update says. The agent's name, a state word and what it does in plain words ([ToolLabels]), never a command,
 * a path or an answer: Android shows a Live Update on the lock screen, the always-on display and, on Samsung phones, the Now Bar.
 */
data class LiveUpdate(
    val kind: IslandSpec.Kind,
    val pillId: String,
    /** The agent's name. */
    val title: String,
    /** What it does or waits for ("Running a command", "Wants to edit files"). */
    val text: String,
    /** The state in a few words ("Using tools", "Waiting for your approval"), and "+1 more" when other agents are busy too. */
    val status: String,
    /** The status bar chip (setShortCriticalText): at most [LiveUpdatePlan.CHIP_MAX] characters, as Android suggests. */
    val chip: String,
    val tone: GlanceTone,
    val progress: LiveProgress,
    /** For a locked screen that hides notification content: the state word only, under the agent's name. */
    val publicText: String,
)

/**
 * From what the island shows ([IslandSpec], built by [IslandPlan] from the computer's sessions and requests) to a Live Update,
 * so the Now Bar, the status bar chip and the island always say the same thing. Pure: every rule is unit-tested.
 */
object LiveUpdatePlan {
    /** Android's suggested maximum for the chip's text (Notification.Builder.setShortCriticalText). */
    const val CHIP_MAX = 7

    private val ACTIVE = setOf(BotState.WORKING, BotState.THINKING, BotState.SEARCHING, BotState.APPROVAL, BotState.QUESTION)

    fun build(spec: IslandSpec, sessions: List<SessionInfo>): LiveUpdate {
        val session = sessions.firstOrNull { it.pillId == spec.pillId }
        val others = sessions.count { it.pillId != spec.pillId && it.state in ACTIVE }
        val more = if (others > 0) " · +$others more" else ""
        val task = spec.text.trim()
        return when (spec.kind) {
            IslandSpec.Kind.APPROVAL -> LiveUpdate(
                spec.kind, spec.pillId, spec.agent, "Wants to ${task.ifEmpty { "do something" }}", "Waiting for your approval$more",
                "Approve", GlanceTone.ATTENTION, LiveProgress.None, "Waiting for your approval",
            )
            IslandSpec.Kind.QUESTION -> LiveUpdate(
                spec.kind, spec.pillId, spec.agent, "Answer in Coucou or on your computer", "Has a question$more",
                "Answer", GlanceTone.ATTENTION, LiveProgress.None, "Has a question",
            )
            IslandSpec.Kind.WORKING -> {
                val (status, idle) = when (spec.state) {
                    BotState.THINKING -> "Thinking" to "Thinking about the next step"
                    BotState.SEARCHING -> "Searching" to "Looking things up"
                    // A step the computer named as a tool ("Running a command"): the agent is using its tools.
                    else -> (if (task.isNotEmpty()) "Using tools" else "Working") to "Working on your task"
                }
                LiveUpdate(
                    spec.kind, spec.pillId, spec.agent, task.ifEmpty { idle }, status + more,
                    "Working", GlanceTone.BUSY, progressOf(session), status,
                )
            }
            IslandSpec.Kind.FINISHED -> LiveUpdate(
                spec.kind, spec.pillId, spec.agent, "All done", "Finished$more", "Done", GlanceTone.OK, LiveProgress.Full, "Finished",
            )
            IslandSpec.Kind.ERROR -> LiveUpdate(
                spec.kind, spec.pillId, spec.agent, "Stopped on an error", "Error$more", "Error", GlanceTone.TROUBLE, LiveProgress.None, "Stopped on an error",
            )
            IslandSpec.Kind.RATELIMIT -> LiveUpdate(
                spec.kind, spec.pillId, spec.agent, "Hit a usage limit", "Limit reached$more", "Limit", GlanceTone.TROUBLE, LiveProgress.None, "Hit a usage limit",
            )
        }
    }

    /** The computer's step count when it sends one (stepIndex counts from 0), else a moving bar. */
    fun progressOf(s: SessionInfo?): LiveProgress {
        if (s == null || s.stepCount <= 0) return LiveProgress.Moving
        return LiveProgress.Steps((s.stepIndex + 1).coerceIn(0, s.stepCount), s.stepCount)
    }
}
