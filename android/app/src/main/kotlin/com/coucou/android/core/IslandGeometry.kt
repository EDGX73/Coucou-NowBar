package com.coucou.android.core

import kotlin.math.max
import kotlin.math.min

/** A rectangle in pixels (the display cutout's bounding box at the top of the screen). */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val width: Int get() = right - left
}

/** Where the island sits and how big it is, in pixels. */
data class IslandBox(
    /** Horizontal offset of the island's centre from the screen's centre (negative = left). */
    val centerOffsetX: Int,
    /** Space at the top that the camera cut-out or status bar takes: content starts below it. */
    val topInset: Int,
    /** Width of the island when it is hidden in the notch. */
    val notchWidth: Int,
    val workingW: Int, val workingH: Int,
    val cardW: Int, val cardH: Int,
    val approvalW: Int, val approvalH: Int,
    val cornerSmall: Int, val cornerLarge: Int,
    /** The user's size (Settings > Display), for what is drawn inside: Mochi, the paddings and the text. */
    val size: IslandSize = IslandSize.DEFAULT,
)

/**
 * The user's own size for the island (Settings > Display), in percent of the PC's sizes: 100, 100, 100 is the island as it
 * shipped. A lower height makes it compact: the working strip keeps one line and Mochi shrinks with it. A permission request is
 * never made shorter, so Deny and Allow stay 48 dp tall; it only grows with the text. Everything stays inside the screen.
 */
data class IslandSize(val widthPct: Int = 100, val heightPct: Int = 100, val textPct: Int = 100) {
    val width: Float get() = widthPct / 100f
    val height: Float get() = heightPct / 100f
    val text: Float get() = textPct / 100f

    /** Each value inside its range and on a [STEP], as the sliders show it. */
    fun clamped() = IslandSize(snap(widthPct, WIDTH), snap(heightPct, HEIGHT), snap(textPct, TEXT))

    companion object {
        val WIDTH = 70..115
        val HEIGHT = 70..130
        val TEXT = 80..140
        const val STEP = 5
        val DEFAULT = IslandSize()

        fun snap(pct: Int, range: IntRange): Int = (Math.round(pct / STEP.toFloat()) * STEP).coerceIn(range)
    }
}

/**
 * The island is centred on the camera cut-out (a centred waterdrop on the Galaxy A12s), its black
 * merges with the hole, and its content starts under it. With no cut-out it hangs from the
 * status bar, centred on the screen. Sizes follow the PC (working strip, card, request card).
 */
object IslandGeometry {
    const val MARGIN_DP = 12
    /** The concave flare on each side where the island meets the top edge of the screen (the PC's `#island::before/after`). */
    const val EAR_DP = 14
    const val MAX_W_DP = 340
    const val WORKING_W_DP = 240 // narrow: it should look like it grows out of the camera hole
    const val MIN_NOTCH_DP = 44
    const val HOLE_PAD_DP = 10

    /** A request card is never narrower than this (two 48 dp buttons side by side), unless the screen is. */
    const val MIN_REQUEST_W_DP = 260
    const val WORKING_H_DP = 52
    const val CARD_H_DP = 96
    const val REQUEST_H_DP = 168
    /** Line height of the island's text, for the room it needs: a little above the system's own, so it never gets cut. */
    const val LINE = 1.3f

    fun place(screenW: Int, density: Float, cutoutTop: PxRect?, statusBarH: Int, size: IslandSize = IslandSize.DEFAULT): IslandBox {
        fun dp(v: Int) = (v * density).toInt()
        fun dp(v: Float) = (v * density).toInt()
        val s = size.clamped()
        // The flares sit outside the island's width, so they come off the room it may use.
        val room = screenW - 2 * dp(MARGIN_DP) - 2 * dp(EAR_DP)
        val maxW = min(room, dp(MAX_W_DP * s.width))
        val cut = cutoutTop?.takeIf { it.width > 0 && it.bottom > 0 }
        val top = max(cut?.bottom ?: statusBarH, 0)
        val approvalW = max(maxW, min(room, dp(MIN_REQUEST_W_DP)))
        // Keep the island on screen even when the cut-out is off-centre.
        val rawOffset = cut?.let { it.centerX - screenW / 2 } ?: 0
        val roomAside = max(screenW / 2 - max(maxW, approvalW) / 2, 0)
        val offset = rawOffset.coerceIn(-roomAside, roomAside)
        // Hidden, the island is just a little wider than the camera hole, so it appears to grow out of it.
        val notch = min(max((cut?.width ?: 0) + dp(HOLE_PAD_DP), dp(MIN_NOTCH_DP)), maxW)
        // A narrower request card may wrap its headline: a little more room for it.
        val requestH = REQUEST_H_DP * max(1f, s.text) + if (s.width < 1f) 16f * (1f - s.width) / 0.3f else 0f
        return IslandBox(
            centerOffsetX = offset, topInset = top, notchWidth = notch,
            workingW = min(dp(WORKING_W_DP * s.width), maxW), workingH = top + dp(workingContentDp(s)),
            cardW = maxW, cardH = top + dp(cardContentDp(s)),
            approvalW = approvalW, approvalH = top + dp(requestH),
            cornerSmall = dp(14), cornerLarge = dp(22),
            size = s,
        )
    }

    /** The working strip's height under the cut-out: the user's height, and never less than one line of text needs. */
    fun workingContentDp(s: IslandSize): Float = max(WORKING_H_DP * s.height, 12f * s.height + 13f * s.text * LINE)

    /** The result card's height under the cut-out: the user's height, and never less than its name and one line need. */
    fun cardContentDp(s: IslandSize): Float = max(CARD_H_DP * s.height, 16f * s.height + (14f + 13f) * s.text * LINE + 2f)

    /** Two lines in the working strip (the name, then what it does) when they fit, else one: "Claude Code · Running a command". */
    fun workingLines(size: IslandSize): Int {
        val s = size.clamped()
        val room = workingContentDp(s) - 12f * s.height
        return if ((13f + 12f) * s.text * LINE <= room) 2 else 1
    }

    /** Lines of text under the result card's title: two when they fit, else one. */
    fun resultLines(size: IslandSize): Int {
        val s = size.clamped()
        val room = cardContentDp(s) - 16f * s.height
        return if ((14f + 2 * 13f) * s.text * LINE + 2f <= room) 2 else 1
    }

    /** Size for a kind of content. */
    fun sizeFor(box: IslandBox, kind: IslandSpec.Kind): Pair<Int, Int> = when (kind) {
        IslandSpec.Kind.WORKING -> box.workingW to box.workingH
        IslandSpec.Kind.APPROVAL -> box.approvalW to box.approvalH
        else -> box.cardW to box.cardH
    }
}
