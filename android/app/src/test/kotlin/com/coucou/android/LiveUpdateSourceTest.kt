package com.coucou.android

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for the Live Update (Settings > Display > Now Bar). A notification cannot be posted in a JVM test, so these read the
 * sources for the rules Android sets for promotion, the privacy rules of every surface seen without unlocking, and the rule
 * of this app: public, documented APIs only.
 */
class LiveUpdateSourceTest {
    private fun src(path: String) = File("src/main/kotlin/com/coucou/android/$path").readText()
    private val notifications get() = src("app/Notifications.kt")
    private val live get() = notifications.substringAfter("fun showLive(").substringBefore("fun cancelLive(")
    private val manifest get() = File("src/main/AndroidManifest.xml").readText()

    @Test fun thePermissionAndroidAsksForIsDeclared() {
        assertTrue(manifest.contains("""<uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />"""))
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
    }

    @Test fun theLiveUpdateKeepsEveryRuleOfPromotion() {
        assertTrue("ongoing", live.contains(".setOngoing(true)"))
        assertTrue("a title", live.contains(".setContentTitle(u.title)"))
        assertTrue("asked for", live.contains("putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)"))
        assertTrue("the documented value", notifications.contains("""EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing""""))
        for (bad in listOf("setColorized", "setCustomContentView", "setCustomBigContentView", "setCustomHeadsUpContentView", "RemoteViews", "setGroupSummary", "setGroup(")) {
            assertFalse("a Live Update may not use $bad", live.contains(bad))
        }
        for (style in listOf("MessagingStyle", "InboxStyle", "BigPictureStyle", "MediaStyle", "DecoratedCustomViewStyle")) assertFalse(style, live.contains(style))
        assertTrue("its own channel, never of minimum importance", notifications.contains("NotificationChannel(CH_LIVE, \"Live Updates\", NotificationManager.IMPORTANCE_DEFAULT)"))
        assertFalse(notifications.contains("IMPORTANCE_MIN"))
    }

    @Test fun itIsPrivateOnTheLockScreenAndSaysNoCommandPathOrAnswer() {
        assertTrue(live.contains("VISIBILITY_PRIVATE") && live.contains("setPublicVersion"))
        assertTrue("the lock screen gets the state word", live.contains(".setContentText(u.publicText)"))
        for ((name, text) in listOf("showLive" to live, "LiveUpdatePlan" to src("core/LiveUpdatePlan.kt"), "LiveUpdates" to src("app/LiveUpdates.kt"))) {
            for (word in listOf(".command", "statusText", "finalLine", ".steps", ".project", ".files")) assertFalse("$name uses $word", text.contains(word))
        }
        assertTrue("a tap only opens the app, with no request in it", live.contains(".setContentIntent(open(null, false, 0))"))
    }

    @Test fun onlyPublicApisAndNoPhoneMakersPrivateExtras() {
        val all = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        // One UI 7's own live notifications were read from undocumented extras: Coucou does not use them.
        for (bad in listOf("ongoingActivityNoti", "com.samsung", "semNotification", "SemNotification", "android.substName")) {
            assertFalse("private API: $bad", Regex("""[".]$bad""").containsMatchIn(all))
        }
        assertTrue("newer calls are behind a version check", live.contains("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {"))
        assertTrue(notifications.contains("@RequiresApi(Build.VERSION_CODES.BAKLAVA)\n    private fun progressStyle("))
        assertTrue(Regex("""compileSdk = 3[6-9]""").containsMatchIn(File("build.gradle.kts").readText()))
    }

    @Test fun aSwipedLiveUpdateIsNotPostedAgainUntilItsActivityEnds() {
        assertTrue(live.contains(".setDeleteIntent(dismissed)"))
        assertTrue(notifications.substringAfter("class ActionReceiver").contains("Notifications.ACTION_LIVE_DISMISSED -> model.liveDismissed()") ||
            notifications.substringAfter("class ActionReceiver").contains("intent.action == Notifications.ACTION_LIVE_DISMISSED"))
        val controller = src("app/LiveUpdates.kt")
        assertTrue(controller.contains("if (!dismissed)"))
        assertTrue("the same content is never posted twice", controller.contains("if (next != posted)"))
        assertTrue("it follows the island's tested timeline", controller.contains("IslandTimeline(clock)"))
        assertTrue("the receiver stays private", manifest.contains("""<receiver android:name=".app.ActionReceiver" android:exported="false" />"""))
    }

    @Test fun theLiveUpdateSaysWhatTheIslandWouldFromTheSameData() {
        val model = src("app/AppModel.kt")
        val refresh = model.substringAfter("private fun refreshIsland()").substringBefore("private fun refreshLive()")
        assertTrue(refresh.contains("refreshLive()"))
        val refreshLive = model.substringAfter("private fun refreshLive()").substringBefore("/**")
        assertTrue(refreshLive.contains("IslandPlan.active(sessions, approvals, allowed = true, this::agentName), sessions"))
        assertTrue("results flash on both", model.contains("IslandPlan.flash(prev, s, settings, minute, liveAllowed(), this::agentName)?.let { live.flash(it, sessions) }"))
        assertTrue("never while disconnected", model.substringAfter("private fun liveAllowed()").substringBefore("/**").contains("Mode.PAIRED -> linkState == LinkState.CONNECTED"))
    }

    @Test fun settingsOffersTheFourChoicesTheStatusAndTheIslandsSize() {
        val settings = src("ui/SettingsScreens.kt")
        for (s in listOf(
            "R.string.surface_now_bar", "R.string.overlay_title", "R.string.surface_both", "R.string.surface_notifications",
            "R.string.island_width", "R.string.island_height", "R.string.island_text", "R.string.island_preview", "R.string.island_reset",
            "SurfacePolicy.line(", "Role.RadioButton",
        )) assertTrue("Settings must carry $s", settings.contains(s))
        val main = src("MainActivity.kt")
        assertTrue("Android's own screen is opened, never changed by the app", main.contains("Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS"))
        assertTrue(main.contains("model.refreshLiveSupport()"))
    }
}
