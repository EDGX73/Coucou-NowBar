package com.coucou.android

import com.coucou.android.core.LiveUpdateLine
import com.coucou.android.core.LiveUpdateSupport
import com.coucou.android.core.SurfaceMode
import com.coucou.android.core.SurfacePlan
import com.coucou.android.core.SurfacePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings > Display: Now Bar, the island, both, or notifications only, and what each phone can really show. */
class SurfacesTest {
    @Test fun theSavedKeysAreStableContractValues() {
        // Saved in the preferences: renaming one would silently reset the user's choice.
        assertEquals(listOf("now_bar", "island", "both", "notifications"), SurfaceMode.entries.map { it.key })
        for (m in SurfaceMode.entries) assertEquals(m, SurfaceMode.parse(m.key, islandSwitch = false))
    }

    @Test fun theOldIslandSwitchBecomesTheChoice() {
        assertEquals("the switch was on", SurfaceMode.ISLAND, SurfaceMode.parse("", islandSwitch = true))
        assertEquals("it was off, the default", SurfaceMode.NOTIFICATIONS, SurfaceMode.parse("", islandSwitch = false))
        assertEquals("an unknown key (a newer version) keeps what the user had", SurfaceMode.ISLAND, SurfaceMode.parse("hologram", islandSwitch = true))
    }

    @Test fun eachModeWantsItsOwnSurfaces() {
        assertEquals(listOf(true, false, true, false), SurfaceMode.entries.map { it.wantsLiveUpdate })
        assertEquals(listOf(false, true, true, false), SurfaceMode.entries.map { it.wantsIsland })
    }

    @Test fun liveUpdatesNeedAndroid16NotificationsAndTheUsersYes() {
        assertEquals(LiveUpdateSupport.OLD_ANDROID, SurfacePolicy.support(35, notificationsEnabled = true, canPostPromoted = true))
        assertEquals(LiveUpdateSupport.OLD_ANDROID, SurfacePolicy.support(30, notificationsEnabled = true, canPostPromoted = false))
        assertEquals(LiveUpdateSupport.NOTIFICATIONS_OFF, SurfacePolicy.support(36, notificationsEnabled = false, canPostPromoted = true))
        assertEquals(LiveUpdateSupport.TURNED_OFF, SurfacePolicy.support(36, notificationsEnabled = true, canPostPromoted = false))
        assertEquals(LiveUpdateSupport.READY, SurfacePolicy.support(36, notificationsEnabled = true, canPostPromoted = true))
        assertEquals("Android 17", LiveUpdateSupport.READY, SurfacePolicy.support(37, notificationsEnabled = true, canPostPromoted = true))
    }

    @Test fun nowBarFallsBackToTheIslandWhereALiveUpdateCannotShow() {
        assertEquals(SurfacePlan(liveUpdate = true, island = false, islandStandsIn = false), SurfacePolicy.plan(SurfaceMode.NOW_BAR, LiveUpdateSupport.READY))
        for (s in listOf(LiveUpdateSupport.OLD_ANDROID, LiveUpdateSupport.NOTIFICATIONS_OFF, LiveUpdateSupport.TURNED_OFF)) {
            assertEquals("$s", SurfacePlan(liveUpdate = false, island = true, islandStandsIn = true), SurfacePolicy.plan(SurfaceMode.NOW_BAR, s))
        }
    }

    @Test fun bothKeepsTheIslandTheIslandIsAloneAndNotificationsShowNeither() {
        assertEquals(SurfacePlan(liveUpdate = true, island = true, islandStandsIn = false), SurfacePolicy.plan(SurfaceMode.BOTH, LiveUpdateSupport.READY))
        assertEquals(SurfacePlan(liveUpdate = false, island = true, islandStandsIn = false), SurfacePolicy.plan(SurfaceMode.BOTH, LiveUpdateSupport.OLD_ANDROID))
        for (s in LiveUpdateSupport.entries) {
            assertEquals(SurfacePlan(liveUpdate = false, island = true, islandStandsIn = false), SurfacePolicy.plan(SurfaceMode.ISLAND, s))
            assertEquals(SurfacePlan(liveUpdate = false, island = false, islandStandsIn = false), SurfacePolicy.plan(SurfaceMode.NOTIFICATIONS, s))
        }
    }

    @Test fun settingsSaysWhatThisPhoneDoesWithTheLiveUpdate() {
        for (m in listOf(SurfaceMode.ISLAND, SurfaceMode.NOTIFICATIONS)) assertEquals(LiveUpdateLine.NONE, SurfacePolicy.line(m, LiveUpdateSupport.READY, true))
        for (m in listOf(SurfaceMode.NOW_BAR, SurfaceMode.BOTH)) {
            assertEquals(LiveUpdateLine.OLD_ANDROID, SurfacePolicy.line(m, LiveUpdateSupport.OLD_ANDROID, null))
            assertEquals(LiveUpdateLine.NOTIFICATIONS_OFF, SurfacePolicy.line(m, LiveUpdateSupport.NOTIFICATIONS_OFF, null))
            assertEquals(LiveUpdateLine.TURNED_OFF, SurfacePolicy.line(m, LiveUpdateSupport.TURNED_OFF, true))
            assertEquals("nothing posted yet", LiveUpdateLine.READY, SurfacePolicy.line(m, LiveUpdateSupport.READY, null))
            assertEquals(LiveUpdateLine.SHOWN_PROMOTED, SurfacePolicy.line(m, LiveUpdateSupport.READY, true))
            assertEquals("Android kept it an ordinary notification", LiveUpdateLine.SHOWN_PLAIN, SurfacePolicy.line(m, LiveUpdateSupport.READY, false))
        }
    }

    @Test fun androidsOwnSwitchIsOfferedOnlyWhereItCanHelp() {
        assertFalse(LiveUpdateLine.NONE.offersSettings)
        assertFalse("no switch can bring Live Updates to an older Android", LiveUpdateLine.OLD_ANDROID.offersSettings)
        for (l in listOf(LiveUpdateLine.READY, LiveUpdateLine.SHOWN_PROMOTED, LiveUpdateLine.SHOWN_PLAIN, LiveUpdateLine.NOTIFICATIONS_OFF, LiveUpdateLine.TURNED_OFF)) {
            assertTrue("$l", l.offersSettings)
        }
    }
}
