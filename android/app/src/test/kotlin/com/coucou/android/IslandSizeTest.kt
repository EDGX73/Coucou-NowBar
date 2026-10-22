package com.coucou.android

import com.coucou.android.core.IslandBox
import com.coucou.android.core.IslandGeometry
import com.coucou.android.core.IslandSize
import com.coucou.android.core.PxRect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The island's width, height and text size (Settings > Display). The phones are assumptions, not measurements: a Galaxy A12s
 * class (720 px, a waterdrop), a Galaxy S24 Ultra at its default resolution (1080 px, about 2.81 density, a centred hole), a
 * narrow 360 dp phone and one with no cut-out. The real inset is read from the display at run time.
 */
class IslandSizeTest {
    private class Phone(val w: Int, val density: Float, val hole: PxRect?, val bar: Int)

    private val phones = listOf(
        Phone(720, 1.75f, PxRect(332, 0, 388, 66), 70),
        Phone(1080, 2.8125f, PxRect(508, 28, 572, 92), 92),
        Phone(1080, 3.0f, PxRect(504, 0, 576, 90), 90),
        Phone(1080, 3.0f, PxRect(80, 0, 150, 60), 60),
        Phone(1440, 3.5f, null, 96),
    )

    private fun every(): List<IslandSize> {
        val out = ArrayList<IslandSize>()
        for (w in IslandSize.WIDTH step IslandSize.STEP) for (h in IslandSize.HEIGHT step IslandSize.STEP) for (t in IslandSize.TEXT step IslandSize.STEP) out.add(IslandSize(w, h, t))
        return out
    }

    /** IslandGeometry.place as it shipped, before the size existed, line for line. */
    private fun shipped(screenW: Int, density: Float, cutoutTop: PxRect?, statusBarH: Int): IslandBox {
        fun dp(v: Int) = (v * density).toInt()
        val maxW = min(screenW - 2 * dp(IslandGeometry.MARGIN_DP) - 2 * dp(IslandGeometry.EAR_DP), dp(IslandGeometry.MAX_W_DP))
        val cut = cutoutTop?.takeIf { it.width > 0 && it.bottom > 0 }
        val top = max(cut?.bottom ?: statusBarH, 0)
        val rawOffset = cut?.let { it.centerX - screenW / 2 } ?: 0
        val room = max(screenW / 2 - maxW / 2, 0)
        val offset = rawOffset.coerceIn(-room, room)
        val notch = min(max((cut?.width ?: 0) + dp(IslandGeometry.HOLE_PAD_DP), dp(IslandGeometry.MIN_NOTCH_DP)), maxW)
        return IslandBox(
            centerOffsetX = offset, topInset = top, notchWidth = notch,
            workingW = min(dp(IslandGeometry.WORKING_W_DP), maxW), workingH = top + dp(52),
            cardW = maxW, cardH = top + dp(96),
            approvalW = maxW, approvalH = top + dp(168),
            cornerSmall = dp(14), cornerLarge = dp(22),
        )
    }

    @Test fun atOneHundredPercentTheIslandIsExactlyTheOneThatShipped() {
        for (p in phones) assertEquals(shipped(p.w, p.density, p.hole, p.bar), IslandGeometry.place(p.w, p.density, p.hole, p.bar, IslandSize.DEFAULT))
        // and on every width from a tiny screen to a tablet, at the common densities
        for (density in listOf(1.5f, 1.75f, 2f, 2.625f, 2.8125f, 3f, 3.5f, 4f)) for (w in 480..2200 step 40) {
            assertEquals("$w px at $density", shipped(w, density, null, 80), IslandGeometry.place(w, density, null, 80))
        }
        assertEquals(2, IslandGeometry.workingLines(IslandSize.DEFAULT))
        assertEquals(2, IslandGeometry.resultLines(IslandSize.DEFAULT))
    }

    @Test fun everySizeKeepsTheWholeIslandAndItsFlaresOnScreen() {
        for (p in phones) for (z in every()) {
            val b = IslandGeometry.place(p.w, p.density, p.hole, p.bar, z)
            val margin = (IslandGeometry.MARGIN_DP * p.density).toInt()
            val ear = (IslandGeometry.EAR_DP * p.density).toInt()
            for (w in listOf(b.workingW, b.cardW, b.approvalW)) {
                assertTrue("$z on ${p.w}: $w wide", w + 2 * ear <= p.w - 2 * margin)
                assertTrue("$z on ${p.w}: off screen", abs(b.centerOffsetX) + w / 2 <= p.w / 2)
            }
            assertTrue("$z: the hidden island is never wider than the strip", b.notchWidth <= b.workingW)
            assertTrue(b.workingH > b.topInset && b.cardH > b.topInset)
        }
    }

    @Test fun theTextAlwaysFitsAndMochiWithIt() {
        for (z in every()) {
            val s = z.clamped()
            val strip = IslandGeometry.workingContentDp(s) - 12f * s.height
            assertTrue("$z: one line of the strip", 13f * s.text * IslandGeometry.LINE <= strip + 0.01f)
            if (IslandGeometry.workingLines(z) == 2) assertTrue("$z: two lines", 25f * s.text * IslandGeometry.LINE <= strip + 0.01f)
            assertTrue("$z: Mochi in the strip", 34f * s.height <= strip + 0.01f)
            val card = IslandGeometry.cardContentDp(s) - 16f * s.height
            val lines = IslandGeometry.resultLines(z)
            assertTrue("$z: the card's $lines lines", (14f + lines * 13f) * s.text * IslandGeometry.LINE + 2f <= card + 0.01f)
            assertTrue("$z: Mochi in the card", 52f * s.height <= card + 0.01f)
        }
    }

    @Test fun aLowerHeightMakesACompactIslandOnOneLine() {
        val phone = phones[1]
        val normal = IslandGeometry.place(phone.w, phone.density, phone.hole, phone.bar)
        val compact = IslandGeometry.place(phone.w, phone.density, phone.hole, phone.bar, IslandSize(widthPct = 70, heightPct = 70))
        assertTrue(compact.workingW < normal.workingW && compact.cardW < normal.cardW)
        assertTrue(compact.workingH - compact.topInset < normal.workingH - normal.topInset)
        assertTrue(compact.cardH < normal.cardH)
        assertEquals(1, IslandGeometry.workingLines(IslandSize(heightPct = 70)))
        assertEquals("bigger text no longer fits two lines in the strip", 1, IslandGeometry.workingLines(IslandSize(textPct = 140)))
        assertEquals(2, IslandGeometry.workingLines(IslandSize(heightPct = 130, textPct = 120)))
    }

    @Test fun aRequestNeverGetsShorterOrNarrowerThanItsButtonsNeed() {
        for (p in phones) {
            val shipped = IslandGeometry.place(p.w, p.density, p.hole, p.bar)
            val minW = min(p.w - 2 * (IslandGeometry.MARGIN_DP * p.density).toInt() - 2 * (IslandGeometry.EAR_DP * p.density).toInt(), (IslandGeometry.MIN_REQUEST_W_DP * p.density).toInt())
            for (z in every()) {
                val b = IslandGeometry.place(p.w, p.density, p.hole, p.bar, z)
                assertTrue("$z: request height", b.approvalH >= shipped.approvalH)
                assertTrue("$z: request width", b.approvalW >= minW)
            }
            val bigText = IslandGeometry.place(p.w, p.density, p.hole, p.bar, IslandSize(textPct = 140))
            assertTrue("it grows with the text", bigText.approvalH > shipped.approvalH)
        }
    }

    @Test fun valuesAreKeptInRangeAndOnTheSlidersSteps() {
        assertEquals(IslandSize(70, 130, 140), IslandSize(3, 999, 141).clamped())
        assertEquals(IslandSize(105, 100, 80), IslandSize(103, 102, -5).clamped())
        assertEquals(IslandSize.DEFAULT, IslandSize.DEFAULT.clamped())
        for (z in every()) assertEquals(z, z.clamped())
    }
}
