package com.markel.flowstate.feature.habits.details.components.numeric

import java.time.LocalDate
import kotlin.math.pow

/**
 * Pure, UI-free model behind the numeric habit heatmap.
 *
 * The heatmap card composes three independent problems, each isolated here so
 * it can be unit-tested on the JVM without Compose, Android or instrumentation:
 *
 *  1. **Intensity** ([HeatmapScale]) — maps a logged amount to the mixing
 *     fraction between the "empty" tone and the habit color, and resolves the
 *     semantic state of each cell ([HeatCellState]).
 *  2. **Geometry** ([HeatmapGeometry]) — distributes the available width into a
 *     whole-pixel grid of week columns, shared by the cells, the weekday label
 *     column and the month captions so the three can never drift apart.
 *  3. **Month captions** ([MonthLabels]) — decides which weeks open a month and
 *     places their captions from measured widths, with collision avoidance.
 *
 * Nothing here depends on a theme, a locale or a screen: the composables only
 * translate these results into dp, colors and text. Keeping the perceptual and
 * geometric policy in one pure object is what makes it reviewable, testable
 * and impossible for the UI to contradict itself.
 */

/**
 * Semantic state of a heatmap cell; drives both its color and its interaction.
 *
 * The states are mutually exclusive; [HeatmapScale.stateOf] resolves them in a
 * fixed priority order, documented there.
 */
enum class HeatCellState {
    /** Scheduled day with a logged amount above zero: full-intensity ramp. */
    VALUE,

    /** Scheduled day without a logged amount: the reference "empty" tone. */
    EMPTY,

    /** Logged amount on a day outside the habit's schedule: dimmed ramp tone. */
    UNSCHEDULED_VALUE,

    /** Day outside the habit's schedule, without a logged amount. */
    UNSCHEDULED_EMPTY,

    /** Day after today: no amount can exist yet; rendered as a ghost. */
    FUTURE,

    /** Day before the habit existed; a ghost, never a missed day. */
    BEFORE_CREATION,
}

/**
 * Intensity mapping for heatmap cells.
 *
 * A cell's fill is `lerp(emptyTone, habitColor, mixFraction(ratio))`, with
 * `ratio = amount / reference` (see [ratio]). All the perceptual policy of the
 * ramp lives in [mixFraction]; the colors themselves stay in the composable,
 * which keeps this object theme-independent and trivially testable.
 */
object HeatmapScale {

    /**
     * Mixing fraction of the habit color for an `amount / reference` ratio.
     *
     * Contract:
     *  * returns `0f` for every non-positive or NaN input, so a day without a
     *    logged amount renders exactly the empty tone;
     *  * continuous and strictly increasing on `(0, 1]`: nearby amounts always
     *    render nearby tones, there are no bands or steps to snap to;
     *  * saturates at `1f`, so amounts above the reference keep the full habit
     *    color instead of extrapolating past it;
     *  * bounded below by [MIN_FRACTION] for any positive ratio, so "logged a
     *    little" is never indistinguishable from "logged nothing".
     *
     * The curve is eased with [RAMP_GAMMA] instead of being linear. Measured in
     * CIE L*, a linear mix spends most of its perceptual budget on the
     * empty→first-value jump and leaves only ~3.4 L* per 10 % of ratio for
     * everything above, which reads as "every day looks the same" on cells this
     * small. The gamma compresses the scarce end — a barely-logged day reads as
     * almost unfilled, which is what it means — and widens the steps in the
     * range where logged amounts actually cluster.
     */
    fun mixFraction(ratio: Float): Float =
        if (!(ratio > 0f)) 0f
        else MIN_FRACTION + (1f - MIN_FRACTION) * ratio.coerceIn(0f, 1f).pow(RAMP_GAMMA)

    /**
     * Mixing fraction of the faintest possible logged day.
     *
     * Small but deliberately non-zero: at 0.10 the faintest tone still sits
     * ~5 L* away from the empty tone (~1.16:1 contrast against it), enough to
     * tell "logged a little" from "logged nothing" at a glance while reading as
     * an almost-unfilled cell. Raising it makes scarce days look fuller than
     * they are; lowering it merges them back into the empty tone.
     */
    const val MIN_FRACTION: Float = 0.10f

    /**
     * Exponent of the intensity curve; greater than 1.
     *
     * At 1.7, consecutive 15 %-of-reference steps above ratio 0.35 differ by
     * 5.8–8.7 L* — comfortably separable on a 12 dp cell — while steps below
     * ratio 0.2 stay under ~2 L*, so scarce days all read as "almost empty", by
     * design. Much larger values flatten the scarce end into a single tone;
     * values near 1 fall back to the linear ramp's uniform but too-small steps.
     */
    const val RAMP_GAMMA: Float = 1.7f

    /**
     * Ratios sampled by the legend swatches, from the empty end to the full
     * habit color.
     *
     * The legend is built by pushing these samples through the same
     * [mixFraction] the grid uses, so legend and grid cannot drift apart:
     * whatever the curve becomes, the legend always shows real samples of it.
     */
    val LEGEND_SAMPLES: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

    /**
     * `amount / reference`, clamped to `0..1`.
     *
     * The reference is the habit's daily target when it has a positive one —
     * the color then means "fraction of the goal" and stays comparable across
     * weeks — and otherwise the largest amount of the visible window, which
     * keeps the ramp fully used for target-less habits. Zero or negative
     * amounts yield 0; amounts above the reference saturate at 1.
     */
    fun ratio(value: Float, target: Float?, rangeMax: Float): Float {
        if (value <= 0f) return 0f
        val reference = when {
            target != null && target > 0f -> target
            rangeMax > 0f -> rangeMax
            else -> return 0f
        }
        return (value / reference).coerceIn(0f, 1f)
    }

    /**
     * Resolves the visual state of one cell, in priority order:
     *
     *  1. [HeatCellState.FUTURE] — no amount can exist yet, whatever the data
     *     map holds; painting it as a value would lie about the future;
     *  2. [HeatCellState.BEFORE_CREATION] — the habit did not exist yet, so an
     *     absent amount is not a missed day and must not read as a failure;
     *  3. the value/schedule combinations, which keep "did the habit on an
     *     off-schedule day" ([HeatCellState.UNSCHEDULED_VALUE]) visually
     *     distinct from "skipped a scheduled day" ([HeatCellState.EMPTY]).
     */
    fun stateOf(
        hasValue: Boolean,
        isFuture: Boolean,
        isScheduled: Boolean,
        isBeforeCreation: Boolean,
    ): HeatCellState = when {
        isFuture -> HeatCellState.FUTURE
        isBeforeCreation -> HeatCellState.BEFORE_CREATION
        hasValue && isScheduled -> HeatCellState.VALUE
        hasValue -> HeatCellState.UNSCHEDULED_VALUE
        isScheduled -> HeatCellState.EMPTY
        else -> HeatCellState.UNSCHEDULED_EMPTY
    }
}

/**
 * Whole-pixel geometry of the week grid.
 *
 * Two rules keep the grid visually exact:
 *
 *  * **One geometry for everything.** The cell side computed here feeds the
 *    cells, the weekday label column and the month captions, so label rows and
 *    cell rows cannot drift apart at any screen width.
 *  * **Snap to pixels once, centrally.** Sizing children in dp and letting each
 *    of them round to pixels on its own is not safe: at several common screen
 *    densities the sum of the rounded column widths plus the rounded gaps
 *    exceeds the equally rounded row width, and the row absorbs the overflow in
 *    its last column — the current week visibly shrank on real devices.
 *    [layoutPx] truncates once, spreads the leftover over the gaps and derives
 *    the row width *from* the children, so the sum is exact by construction.
 */
object HeatmapGeometry {

    /**
     * Width left for the week columns once the weekday label column and its
     * spacer are paid for, in device pixels. The three terms round
     * independently, matching how the composables place them side by side.
     */
    fun gridAvailablePx(availablePx: Int, labelColumnPx: Int, spacerPx: Int): Int =
        availablePx - labelColumnPx - spacerPx

    /**
     * Distributes [availableGridPx] into [weeks] equal columns separated by
     * `weeks - 1` gaps of about [baseGapPx] pixels.
     *
     * The cell side is the truncated share of the width that remains after
     * paying for the base gaps, clamped to [minCellPx]..[maxCellPx]; the
     * truncation remainder — at most one pixel per gap — is then added one
     * pixel at a time to the leading gaps. Columns therefore stay perfectly
     * uniform, which keeps cell rows aligned with the weekday labels, and only
     * the gaps absorb the rounding, which nobody measures by eye.
     *
     * Invariants, given `availableGridPx >= weeks * minCellPx +
     * (weeks - 1) * baseGapPx` (below that the caller must scroll or show fewer
     * weeks, since the minimum readable cell no longer fits):
     *  * `totalPx == availableGridPx` whenever the cell side is not clamped;
     *  * `totalPx <= availableGridPx` when it is clamped to [maxCellPx], leaving
     *    the row left-aligned with spare width instead of stretched;
     *  * every gap is either [baseGapPx] or `baseGapPx + 1`;
     *  * all columns share the same `cellPx`.
     */
    fun layoutPx(
        availableGridPx: Int,
        weeks: Int,
        baseGapPx: Int,
        minCellPx: Int,
        maxCellPx: Int,
    ): GridLayout {
        require(weeks > 0) { "weeks must be positive" }
        val gaps = (weeks - 1).coerceAtLeast(0)
        val cell = ((availableGridPx - gaps * baseGapPx) / weeks.coerceAtLeast(1))
            .coerceIn(minCellPx, maxCellPx)
        // The truncation remainder is spread over the gaps one pixel at a time:
        // columns stay identical (so rows stay aligned) and the total is exact.
        val remainder = (availableGridPx - weeks * cell - gaps * baseGapPx)
            .coerceIn(0, gaps)
        val gapWidths = IntArray(gaps) { baseGapPx + if (it < remainder) 1 else 0 }
        return GridLayout(cell, gapWidths, weeks * cell + gapWidths.sum())
    }
}

/**
 * Result of [HeatmapGeometry.layoutPx]: the uniform column side, the width of
 * each of the `weeks - 1` gaps, and the exact row width they add up to.
 *
 * Composables render every week column with its own trailing gap, so the row's
 * total width equals [totalPx] to the pixel instead of being an independently
 * rounded value the children have to fit into.
 */
data class GridLayout(val cellPx: Int, val gapPx: IntArray, val totalPx: Int) {
    /** Width of the gap after column [index]; 0 past the last column. */
    fun gapPx(index: Int): Int = gapPx.getOrElse(index) { 0 }

    // [gapPx] is an array, so value equality has to compare it by content.
    override fun equals(other: Any?): Boolean =
        other is GridLayout && cellPx == other.cellPx && totalPx == other.totalPx &&
                gapPx.contentEquals(other.gapPx)
    override fun hashCode(): Int = 31 * (31 * cellPx + totalPx) + gapPx.contentHashCode()
}

/**
 * A week whose Monday opens a month, together with the caption it should carry.
 */
data class MonthLabelCandidate(
    val weekIndex: Int,
    val year: Int,

    /** 1-12, as [java.time.Month.getValue]. */
    val month: Int,

    /** The year must travel with the caption (January or a year boundary). */
    val showsYear: Boolean,
) {
    /** January captions are emphasized, since they carry the year change. */
    val isJanuary: Boolean get() = month == 1
}

/** A caption ready to draw, with the x offset (dp) of its week column. */
data class PlacedMonthLabel(
    val candidate: MonthLabelCandidate,
    val xDp: Float,
    val widthDp: Float,
)

/**
 * Month captions for the grid header.
 *
 * Captions are placed from *measured* text widths instead of being squeezed
 * into one fixed box per week, which clipped them mid-word. The policy is:
 * draw a caption where its month starts, drop it when it would collide with
 * the previous one, and never clip or ellipsize — a dropped caption is
 * invisible but honest, a clipped one looks broken.
 */
object MonthLabels {

    /**
     * The weeks whose Monday falls in a different month than the previous
     * week's Monday: the weeks where a caption should start.
     *
     * The year travels with the caption only where it adds information —
     * January, and any month that crosses a year boundary inside the window —
     * so an 18-week window within one year reads «sep oct nov dic» while a
     * 52-week one reads «… dic ene 26 feb …» without ambiguous repeats.
     */
    fun candidates(weekStartDates: List<LocalDate>): List<MonthLabelCandidate> {
        val out = ArrayList<MonthLabelCandidate>(weekStartDates.size / 4 + 2)
        var previous: LocalDate? = null
        weekStartDates.forEachIndexed { index, start ->
            val prev = previous
            val isNewMonth = prev == null || start.monthValue != prev.monthValue
            if (isNewMonth) {
                val showsYear = start.monthValue == 1 ||
                        (prev != null && start.year != prev.year)
                out += MonthLabelCandidate(index, start.year, start.monthValue, showsYear)
            }
            previous = start
        }
        return out
    }

    /**
     * Places [candidates] at their week's x offset and returns the ones that
     * fit, left to right.
     *
     * @param widthOf measured caption width in dp for each candidate; the
     *   composable supplies it from `TextMeasurer`, tests from a stub.
     * @param cellDp cell side in dp, part of the default week pitch.
     * @param gapDp base gap between columns in dp, part of the default pitch.
     * @param totalWidthDp width of the grid row; a caption that would fall off
     *   the right edge is pulled in to hug it instead of disappearing.
     * @param minGapDp minimum free space between two captions; a candidate
     *   starting closer than this to the previous placed caption is dropped.
     * @param xForWeek x offset in dp of a week column; pixel-snapped layouts
     *   pass their own prefix sums so captions sit exactly over their column.
     */
    fun place(
        candidates: List<MonthLabelCandidate>,
        widthOf: (MonthLabelCandidate) -> Float,
        cellDp: Float,
        gapDp: Float,
        totalWidthDp: Float,
        minGapDp: Float = 3f,
        xForWeek: (Int) -> Float = { it * (cellDp + gapDp) },
    ): List<PlacedMonthLabel> {
        val placed = ArrayList<PlacedMonthLabel>(candidates.size)
        var previousEnd = Float.NEGATIVE_INFINITY
        for (candidate in candidates) {
            val width = widthOf(candidate)
            var x = xForWeek(candidate.weekIndex)
            if (x + width > totalWidthDp) x = totalWidthDp - width
            if (x < 0f) continue
            if (x < previousEnd + minGapDp) continue
            placed += PlacedMonthLabel(candidate, x, width)
            previousEnd = x + width
        }
        return placed
    }
}