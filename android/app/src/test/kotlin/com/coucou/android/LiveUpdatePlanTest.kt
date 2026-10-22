package com.coucou.android

import com.coucou.android.core.GlanceTone
import com.coucou.android.core.IslandPlan
import com.coucou.android.core.IslandSpec
import com.coucou.android.core.LiveProgress
import com.coucou.android.core.LiveUpdate
import com.coucou.android.core.LiveUpdatePlan
import com.coucou.android.core.PillCategory
import com.coucou.android.core.Pills
import com.coucou.android.core.ToolLabels
import com.coucou.android.core.UserSettings
import com.coucou.android.link.ApprovalRequest
import com.coucou.android.link.SessionInfo
import com.coucou.android.mochi.BotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Live Update (the Now Bar, the status bar chip, the lock screen) says, built from the island's own plan. */
class LiveUpdatePlanTest {
    private val name = { id: String -> Pills.byId(id)?.name ?: "An agent" }
    private val claude = "integration_claude"
    private val opencode = "agent_opencode"
    private val noon = 12 * 60

    private fun s(id: String, st: BotState, text: String = "", step: Int = 0, count: Int = 0) = SessionInfo(id, "", st, text, step, count, 0L)

    /** What goes on now, as AppModel asks for it: the island's plan, then the Live Update's words. */
    private fun live(sessions: List<SessionInfo>, approvals: List<ApprovalRequest> = emptyList()): LiveUpdate? =
        IslandPlan.active(sessions, approvals, allowed = true, name)?.let { LiveUpdatePlan.build(it, sessions) }

    /** How a task ended, as AppModel asks for it: the island's flash, then the Live Update's words. */
    private fun ended(state: BotState, others: List<SessionInfo> = emptyList()): LiveUpdate {
        val sessions = listOf(s(claude, state)) + others
        val spec = IslandPlan.flash(BotState.WORKING, sessions.first(), UserSettings(), noon, allowed = true, name)
        return LiveUpdatePlan.build(spec!!, sessions)
    }

    private fun LiveUpdate.words() = listOf(title, text, status, chip, publicText)

    @Test fun anAgentUsingAToolSaysWhichInPlainWordsAndNeverTheArguments() {
        val u = live(listOf(s(claude, BotState.WORKING, "Bash · npm test -- --token=abc123")))!!
        assertEquals("Claude Code", u.title)
        assertEquals(ToolLabels.label("Bash · npm test"), u.text)
        assertEquals("Running a command · npm", u.text)
        assertEquals("Using tools", u.status)
        assertEquals("Working", u.chip)
        assertEquals(GlanceTone.BUSY, u.tone)
        assertEquals("the lock screen gets the state word only", "Using tools", u.publicText)
        for (w in u.words()) assertFalse(w, w.contains("abc123") || w.contains("--"))
    }

    @Test fun workingThinkingAndSearchingEachHaveTheirWords() {
        assertEquals("Working" to "Working on your task", live(listOf(s(claude, BotState.WORKING)))!!.let { it.status to it.text })
        assertEquals("Thinking" to "Thinking about the next step", live(listOf(s(claude, BotState.THINKING)))!!.let { it.status to it.text })
        assertEquals("Searching" to "Looking things up", live(listOf(s(claude, BotState.SEARCHING)))!!.let { it.status to it.text })
        assertEquals("free text from the computer is never shown", "Working on your task", live(listOf(s(claude, BotState.WORKING, "Fixing the bug in /home/me/app.rs")))!!.text)
    }

    @Test fun aPermissionRequestComesFirstAndNamesTheActionNeverTheCommand() {
        val request = ApprovalRequest(claude, "f".repeat(64), "Bash", "rm -rf /home/me/secret", 0L)
        val u = live(listOf(s(claude, BotState.APPROVAL), s(opencode, BotState.WORKING)), listOf(request))!!
        assertEquals(IslandSpec.Kind.APPROVAL, u.kind)
        assertEquals("Claude Code", u.title)
        assertEquals("Wants to run a command", u.text)
        assertEquals("Waiting for your approval · +1 more", u.status)
        assertEquals("Approve", u.chip)
        assertEquals(GlanceTone.ATTENTION, u.tone)
        assertEquals(LiveProgress.None, u.progress)
        for (w in u.words()) assertFalse(w, w.contains("rm") || w.contains("secret") || w.contains("/"))
    }

    @Test fun aQuestionWaitsForTheUser() {
        val u = live(listOf(s(opencode, BotState.QUESTION, "ask_question")))!!
        assertEquals("OpenCode", u.title)
        assertEquals("Has a question", u.status)
        assertEquals("Answer", u.chip)
        assertEquals(GlanceTone.ATTENTION, u.tone)
    }

    @Test fun finishedFailedAndLimitedAreTheIslandsTenSecondResults() {
        assertEquals(listOf("Finished", "All done", "Done"), ended(BotState.FINISHED).let { listOf(it.status, it.text, it.chip) })
        assertEquals(LiveProgress.Full, ended(BotState.FINISHED).progress)
        assertEquals(GlanceTone.OK, ended(BotState.FINISHED).tone)
        assertEquals(listOf("Error", "Stopped on an error", "Error"), ended(BotState.ERROR).let { listOf(it.status, it.text, it.chip) })
        assertEquals(GlanceTone.TROUBLE, ended(BotState.ERROR).tone)
        assertEquals(listOf("Limit reached", "Hit a usage limit", "Limit"), ended(BotState.RATELIMIT).let { listOf(it.status, it.text, it.chip) })
    }

    @Test fun nothingGoingOnMeansNoLiveUpdate() {
        for (st in listOf(BotState.IDLE, BotState.SLEEPING, BotState.FINISHED, BotState.ERROR, BotState.DIZZY)) {
            assertNull("$st", live(listOf(s(claude, st))))
        }
        assertNull(live(emptyList()))
    }

    @Test fun theChipFitsTheLengthAndroidSuggests() {
        val all = listOf(
            live(listOf(s(claude, BotState.WORKING, "Bash")))!!, live(listOf(s(claude, BotState.THINKING)))!!, live(listOf(s(claude, BotState.SEARCHING)))!!,
            live(listOf(s(claude, BotState.QUESTION)))!!, live(listOf(s(claude, BotState.APPROVAL)), listOf(ApprovalRequest(claude, "a".repeat(64), "Edit", "x", 0L)))!!,
            ended(BotState.FINISHED), ended(BotState.ERROR), ended(BotState.RATELIMIT),
        )
        assertEquals(IslandSpec.Kind.entries.toSet(), all.map { it.kind }.toSet())
        for (u in all) assertTrue("${u.kind}: '${u.chip}'", u.chip.isNotBlank() && u.chip.length <= LiveUpdatePlan.CHIP_MAX)
    }

    @Test fun theBarFollowsTheComputersStepsElseItMoves() {
        assertEquals("stepIndex counts from 0", LiveProgress.Steps(3, 6), live(listOf(s(claude, BotState.WORKING, "Edit", step = 2, count = 6)))!!.progress)
        assertEquals(LiveProgress.Steps(6, 6), LiveUpdatePlan.progressOf(s(claude, BotState.WORKING, step = 9, count = 6)))
        assertEquals(LiveProgress.Moving, LiveUpdatePlan.progressOf(s(claude, BotState.WORKING)))
        assertEquals(LiveProgress.Moving, LiveUpdatePlan.progressOf(null))
    }

    @Test fun otherBusyAgentsAreCountedNotTheIdleOnes() {
        val u = live(listOf(s(claude, BotState.WORKING), s(opencode, BotState.THINKING), s("agent_codex", BotState.QUESTION), s("agent_gemini", BotState.IDLE), s("agent_amp", BotState.FINISHED)))!!
        assertEquals("the question comes first", "Codex", u.title)
        assertEquals("Has a question · +2 more", u.status)
        assertEquals("the lock screen line has no count", "Has a question", u.publicText)
    }

    @Test fun everyAgentOfTheCatalogShowsByItsName() {
        val agents = Pills.catalog.filter { it.category == PillCategory.AGENT || it.category == PillCategory.WORKSPACE }
        assertTrue(agents.any { it.id == opencode } && agents.any { it.id == claude })
        for (p in agents) {
            val u = live(listOf(s(p.id, BotState.WORKING)))
            assertNotNull(p.id, u)
            assertEquals(p.name, u!!.title)
        }
    }
}
