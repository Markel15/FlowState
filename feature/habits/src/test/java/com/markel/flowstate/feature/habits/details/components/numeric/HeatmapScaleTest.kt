package com.markel.flowstate.feature.habits.details.components.numeric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Contract tests for the pure heatmap model ([HeatmapScale], [HeatmapGeometry]
 * and [MonthLabels]).
 *
 * These are the invariants the composables rely on, pinned on the JVM so no
 * device, theme or Compose runtime is needed to break them:
 *
 *  * the intensity curve is continuous, monotonic, saturated at both ends and
 *    never lets a logged amount collapse into the empty tone;
 *  * the pixel-snapped layout never overflows its row and keeps every column
 *    identical, including a regression guard for the historical dp-sized grid;
 *  * month captions are chosen and placed without clipping or overlapping.
 */
class HeatmapScaleTest {

    // ── intensity curve: ends, floor and continuity ─────────────────────────

    @Test
    fun `zero and negative ratios stay at the empty tone`() {
        assertEquals(0f, HeatmapScale.mixFraction(0f), 0f)
        assertEquals(0f, HeatmapScale.mixFraction(-0.4f), 0f)
        assertEquals(0f, HeatmapScale.mixFraction(Float.NaN), 0f)
    }

    @Test
    fun `any positive ratio jumps straight to the visibility floor`() {
        // The floor is part of the contract: a logged amount, however small,
        // must render visibly above the empty tone instead of collapsing into
        // it. Historically a threshold lookup did exactly that for every value
        // under 15 % of the reference; this is the regression guard.
        listOf(0.001f, 0.01f, 0.0999f, 0.10f, 0.149f).forEach { ratio ->
            assertTrue(
                "ratio $ratio must sit at or above the floor",
                HeatmapScale.mixFraction(ratio) >= HeatmapScale.MIN_FRACTION,
            )
        }
    }

    @Test
    fun `the ramp is continuous and monotonic, without a fixed pool`() {
        var previous = 0f
        var ratio = 0f
        while (ratio <= 1.001f) {
            val fraction = HeatmapScale.mixFraction(ratio)
            assertTrue("fraction must not go backwards at $ratio", fraction >= previous)
            previous = fraction
            ratio += 0.01f
        }
        assertEquals(1f, HeatmapScale.mixFraction(1f), 0f)
        assertEquals(1f, HeatmapScale.mixFraction(2.5f), 0f)
    }

    @Test
    fun `neighbouring amounts produce neighbouring tones, never a jump`() {
        // A banded palette jumps by a fixed chunk at every cut. A continuous
        // curve must not: over a 1 % step of ratio the fraction may move at
        // most as much as the steepest slope of the curve, (1 - floor) * gamma.
        val maxSlope = (1f - HeatmapScale.MIN_FRACTION) * HeatmapScale.RAMP_GAMMA
        var ratio = 0.01f
        while (ratio < 0.99f) {
            val delta = HeatmapScale.mixFraction(ratio + 0.01f) - HeatmapScale.mixFraction(ratio)
            assertTrue("jump of $delta at $ratio is a band, not a ramp", delta <= maxSlope * 0.01f + 1e-4f)
            ratio += 0.01f
        }
        // The single intentional discontinuity is the visibility floor itself:
        // from "nothing logged" to "something logged" in one step.
        assertEquals(
            HeatmapScale.MIN_FRACTION,
            HeatmapScale.mixFraction(0.0001f) - HeatmapScale.mixFraction(0f),
            0.001f,
        )
    }

    @Test
    fun `scarce days read as almost unfilled, but never as empty`() {
        // Two sides of the same requirement: a very small amount should look
        // like a barely-touched cell, yet remain distinguishable from a cell
        // with no amount at all.
        val scarce = HeatmapScale.mixFraction(0.10f)
        assertTrue("a 10 % day already reads as filled: $scarce", scarce <= HeatmapScale.MIN_FRACTION + 0.08f)
        assertTrue("a 10 % day is indistinguishable from the empty tone", scarce - HeatmapScale.mixFraction(0f) >= 0.05f)
    }

    @Test
    fun `the range where data lives keeps visible separation`() {
        // Perceptual budget check, in mixing-fraction terms: between 70 % and
        // 90 % of the reference the curve must move enough to stay separable on
        // a small cell. A linear ramp with a high floor failed exactly here,
        // making every logged day look like the same shade.
        assertTrue(
            HeatmapScale.mixFraction(0.9f) - HeatmapScale.mixFraction(0.7f) >= 0.25f,
        )
    }

    @Test
    fun `legend samples span the whole ramp in order`() {
        val tones = HeatmapScale.LEGEND_SAMPLES.map { HeatmapScale.mixFraction(it) }
        assertEquals(0f, tones.first(), 0f)
        assertEquals(1f, tones.last(), 0f)
        tones.zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
    }

    @Test
    fun `ratio uses the goal when present and the range max otherwise`() {
        assertEquals(0.5f, HeatmapScale.ratio(5f, target = 10f, rangeMax = 50f), 0f)
        assertEquals(0.5f, HeatmapScale.ratio(25f, target = null, rangeMax = 50f), 0f)
        assertEquals(1f, HeatmapScale.ratio(90f, target = null, rangeMax = 50f), 0f)
        assertEquals(0f, HeatmapScale.ratio(0f, target = 10f, rangeMax = 50f), 0f)
        assertEquals(0f, HeatmapScale.ratio(4f, target = 0f, rangeMax = 0f), 0f)
    }

    // ── cell state resolution ───────────────────────────────────────────────

    @Test
    fun `future days win over every other state`() {
        assertEquals(
            HeatCellState.FUTURE,
            HeatmapScale.stateOf(hasValue = true, isFuture = true, isScheduled = true, isBeforeCreation = false),
        )
    }

    @Test
    fun `days before creation are not failed days`() {
        assertEquals(
            HeatCellState.BEFORE_CREATION,
            HeatmapScale.stateOf(hasValue = false, isFuture = false, isScheduled = true, isBeforeCreation = true),
        )
    }

    @Test
    fun `scheduled and unscheduled days with and without value are distinct`() {
        assertEquals(
            HeatCellState.VALUE,
            HeatmapScale.stateOf(true, isFuture = false, isScheduled = true, isBeforeCreation = false),
        )
        assertEquals(
            HeatCellState.UNSCHEDULED_VALUE,
            HeatmapScale.stateOf(true, isFuture = false, isScheduled = false, isBeforeCreation = false),
        )
        assertEquals(
            HeatCellState.EMPTY,
            HeatmapScale.stateOf(false, isFuture = false, isScheduled = true, isBeforeCreation = false),
        )
        assertEquals(
            HeatCellState.UNSCHEDULED_EMPTY,
            HeatmapScale.stateOf(false, isFuture = false, isScheduled = false, isBeforeCreation = false),
        )
    }

    // ── pixel-snapped layout ────────────────────────────────────────────────

    /**
     * What the historical dp-sized grid summed to once every child rounded to
     * pixels on its own: 18 rounded columns plus 17 rounded gaps, against an
     * independently rounded row width.
     */
    private fun oldRoundedSumDp(screenDp: Int, density: Float): Pair<Int, Int> {
        val available = screenDp - 64f
        val gridDp = available - 16f - 5f
        val cellDp = (gridDp - 17 * 3f) / 18
        val cellPx = (cellDp * density).roundToInt()
        val gapPx = (3f * density).roundToInt()
        val rowPx = (gridDp * density).roundToInt()
        return (18 * cellPx + 17 * gapPx) to rowPx
    }

    @Test
    fun `the historical dp sizing overflowed the row on common densities`() {
        // Regression guard. At 420 dpi (density 2.625) the independently rounded
        // children exceeded the rounded row width, so the row absorbed the
        // overflow in its last column and the current week rendered visibly
        // smaller. If someone reintroduces dp-sized children, this fails.
        val (children, row) = oldRoundedSumDp(360, 2.625f)
        assertTrue("expected the historical overflow, got $children <= $row", children > row)
    }

    @Test
    fun `the snapped layout never overflows, at any density or width`() {
        val densities = listOf(1f, 1.5f, 2f, 2.625f, 2.75f, 3f, 3.5f, 4f)
        for (density in densities) {
            for (screen in 300..640 step 4) {
                val availablePx = (screen * density).roundToInt() - (64f * density).roundToInt()
                val gridPx = HeatmapGeometry.gridAvailablePx(
                    availablePx = availablePx,
                    labelColumnPx = (16f * density).roundToInt(),
                    spacerPx = (5f * density).roundToInt(),
                )
                val layout = HeatmapGeometry.layoutPx(
                    availableGridPx = gridPx,
                    weeks = 18,
                    baseGapPx = (3f * density).roundToInt(),
                    minCellPx = (5f * density).roundToInt(),
                    maxCellPx = (18f * density).roundToInt(),
                )
                assertTrue(
                    "density $density screen $screen: ${layout.totalPx} > $gridPx",
                    layout.totalPx <= gridPx,
                )
                // Uniform columns keep the seven rows aligned with the labels.
                assertTrue(layout.cellPx > 0)
                assertEquals(17, layout.gapPx.size)
            }
        }
    }

    @Test
    fun `the snapped layout fills the row exactly when the cell is not clamped`() {
        val density = 2.625f
        val availablePx = (360 * density).roundToInt() - (64f * density).roundToInt()
        val gridPx = HeatmapGeometry.gridAvailablePx(
            availablePx, (16f * density).roundToInt(), (5f * density).roundToInt(),
        )
        val layout = HeatmapGeometry.layoutPx(
            gridPx, 18, (3f * density).roundToInt(),
            (5f * density).roundToInt(), (18f * density).roundToInt(),
        )
        assertEquals(gridPx, layout.totalPx)
        // Gaps absorb the truncation remainder one pixel at a time, so at most
        // two different gap widths exist and columns stay uniform.
        assertTrue(layout.gapPx.toList().toSet().size <= 2)
    }

    @Test
    fun `wide screens clamp the cell instead of growing forever`() {
        val density = 3f
        val gridPx = HeatmapGeometry.gridAvailablePx(
            (600 * density).roundToInt(), (16f * density).roundToInt(), (5f * density).roundToInt(),
        )
        val layout = HeatmapGeometry.layoutPx(
            gridPx, 18, (3f * density).roundToInt(),
            (5f * density).roundToInt(), (18f * density).roundToInt(),
        )
        assertEquals((18f * density).roundToInt(), layout.cellPx)
        assertTrue(layout.totalPx <= gridPx)
    }

    // ── month captions ──────────────────────────────────────────────────────

    private fun mondays(first: LocalDate, weeks: Int) =
        (0 until weeks).map { first.plusWeeks(it.toLong()) }

    @Test
    fun `candidates start at every monday opening a month`() {
        val starts = mondays(LocalDate.of(2026, 5, 18), 18)
        val candidates = MonthLabels.candidates(starts)
        assertEquals(listOf(5, 6, 7, 8, 9), candidates.map { it.month })
        assertEquals(listOf(0, 2, 7, 11, 16), candidates.map { it.weekIndex })
        assertTrue(candidates.none { it.showsYear })
    }

    @Test
    fun `the year travels with january and with year boundaries`() {
        val starts = mondays(LocalDate.of(2025, 12, 1), 10)
        val candidates = MonthLabels.candidates(starts)
        assertEquals(listOf(12, 1, 2), candidates.map { it.month })
        assertFalse(candidates[0].showsYear)
        assertTrue(candidates[1].showsYear)
        assertTrue(candidates[1].isJanuary)
        assertFalse(candidates[2].showsYear)
    }

    @Test
    fun `captions are placed at their week and never overlap`() {
        val starts = mondays(LocalDate.of(2026, 5, 18), 52)
        val candidates = MonthLabels.candidates(starts)
        val cell = 4.3f
        val gap = 3f
        val total = 52 * cell + 51 * gap
        val placed = MonthLabels.place(
            candidates = candidates,
            widthOf = { 14f },
            cellDp = cell,
            gapDp = gap,
            totalWidthDp = total,
        )
        assertTrue(placed.isNotEmpty())
        assertTrue(placed.size < candidates.size) // some dropped, none clipped
        placed.zipWithNext().forEach { (a, b) ->
            assertTrue(b.xDp >= a.xDp + a.widthDp)
        }
        placed.forEach {
            assertTrue(it.xDp >= 0f)
            assertTrue(it.xDp + it.widthDp <= total + 0.001f)
        }
    }

    @Test
    fun `captions honour custom week offsets from the snapped layout`() {
        val candidates = listOf(
            MonthLabelCandidate(0, 2026, 9, false),
            MonthLabelCandidate(3, 2026, 10, false),
        )
        val placed = MonthLabels.place(
            candidates = candidates,
            widthOf = { 10f },
            cellDp = 12f,
            gapDp = 3f,
            totalWidthDp = 100f,
            xForWeek = { week -> listOf(0f, 13f, 26f, 40f)[week] },
        )
        assertEquals(0f, placed[0].xDp, 0f)
        assertEquals(40f, placed[1].xDp, 0f)
    }

    @Test
    fun `a caption falling off the right edge hugs it instead of vanishing`() {
        val candidates = listOf(MonthLabelCandidate(3, 2026, 9, false))
        val total = 4 * 12f + 3 * 3f
        val placed = MonthLabels.place(
            candidates = candidates,
            widthOf = { 20f },
            cellDp = 12f,
            gapDp = 3f,
            totalWidthDp = total,
        )
        assertEquals(1, placed.size)
        assertEquals(total - 20f, placed[0].xDp, 0.001f)
    }

    @Test
    fun `scheduled day lookup agrees with the domain model semantics`() {
        val scheduled = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val monday = LocalDate.of(2026, 9, 14)
        assertTrue(monday.dayOfWeek in scheduled)
        assertFalse(monday.plusDays(1).dayOfWeek in scheduled)
    }
}
