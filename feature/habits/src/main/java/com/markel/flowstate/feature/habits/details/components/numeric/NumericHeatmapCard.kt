package com.markel.flowstate.feature.habits.details.components.numeric

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markel.flowstate.feature.habits.R
import com.markel.flowstate.feature.habits.util.formatFloat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.roundToInt

/**
 * Activity heatmap for numeric habits.
 *
 * Geometry: a single cell size derived from the available width feeds both the
 * seven weekday rows and the 7×N cell grid, so the two can never drift apart.
 *
 * Color: a continuous ramp built with [lerp] between the empty tone and the
 * habit color. The amount logged moves the cell smoothly towards the habit
 * color rather than snapping it to a fixed pool.
 *
 * Layout is snapped to whole pixels ([HeatmapGeometry.layoutPx]): sizing in dp
 * let the 18 rounded column widths overflow the rounded row width on some
 * device densities, squeezing the current week's cells.
 *
 * Motion follows [MaterialTheme.motionScheme]: cell colors cross-fade with the
 * effects spec and selection enters with a springy scale.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NumericHeatmapCard(
    heatmapData: Map<LocalDate, Float>,
    targetValue: Float?,
    habitColor: Color,
    unit: String?,
    scheduledDays: Set<DayOfWeek>,
    createdAt: LocalDate?,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val weeksToDisplay = 18
    val startDate = remember(today) {
        today.with(DayOfWeek.MONDAY).minusWeeks((weeksToDisplay - 1).toLong())
    }
    val locale = LocalLocale.current.platformLocale

    val rangeMax = remember(heatmapData) { heatmapData.values.maxOrNull() ?: 0f }

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    // ── palette (opaque, background-independent) ────────────────────────────
    val cardBackground = MaterialTheme.colorScheme.surfaceContainerLow
    val emptyTone = MaterialTheme.colorScheme.surfaceContainerHighest
    val palette = remember(cardBackground, emptyTone, habitColor) {
        HeatPalette(
            empty = emptyTone,
            ghost = lerp(cardBackground, emptyTone, GHOST_FRACTION),
            unscheduledEmpty = lerp(cardBackground, emptyTone, UNSCHEDULED_EMPTY_FRACTION),
            habit = habitColor,
        )
    }
    val hatchColor = MaterialTheme.colorScheme.onSurface.copy(alpha = HATCH_ALPHA)

    val dateLabel = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val dayLabel = remember(locale) {
        DateTimeFormatter.ofPattern("EEE d MMM", locale)
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val gapBasePx = with(density) { CELL_GAP_DP.dp.toPx() }.roundToInt()
        val layout = HeatmapGeometry.layoutPx(
            availableGridPx = HeatmapGeometry.gridAvailablePx(
                availablePx = with(density) { maxWidth.toPx() }.roundToInt(),
                labelColumnPx = with(density) { LABEL_COLUMN_DP.dp.toPx() }.roundToInt(),
                spacerPx = with(density) { LABEL_SPACER_DP.dp.toPx() }.roundToInt(),
            ),
            weeks = weeksToDisplay,
            baseGapPx = gapBasePx,
            minCellPx = with(density) { MIN_CELL_DP.dp.toPx() }.roundToInt(),
            maxCellPx = with(density) { MAX_CELL_DP.dp.toPx() }.roundToInt(),
        )
        val gap = CELL_GAP_DP
        val cell = with(density) { layout.cellPx.toDp() }
        val corner = cell * CELL_CORNER_FRACTION
        val gridWidth = with(density) { layout.totalPx.toDp() }
        // Per-week x offsets and trailing gaps, in dp, honoring the snapped pixels.
        val weekXsDp = remember(layout, density) {
            var acc = 0
            List(weeksToDisplay) { i ->
                val x = with(density) { acc.toDp().value }
                acc += layout.cellPx + layout.gapPx(i)
                x
            }
        }
        val weekGapsDp = remember(layout, density) {
            List(weeksToDisplay) { with(density) { layout.gapPx(it).toDp() } }
        }

        Column {
            HeatmapHeader(
                weeks = weeksToDisplay,
                selectedDate = selectedDate,
                heatmapData = heatmapData,
                unit = unit,
                habitColor = habitColor,
                dayLabel = dayLabel,
            )

            MonthLabelRow(
                startDate = startDate,
                weeks = weeksToDisplay,
                cell = cell,
                gap = gap,
                gridWidth = gridWidth,
                locale = locale,
                weekXsDp = weekXsDp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                WeekdayColumn(cell = cell, gap = gap, locale = locale)
                Spacer(modifier = Modifier.width(LABEL_SPACER_DP.dp))

                Row(modifier = Modifier.width(gridWidth)) {
                    (0 until weeksToDisplay).forEach { weekIndex ->
                        Column(
                            // The trailing gap rides on the column so the row's
                            // total width is exactly the snapped layout's.
                            modifier = Modifier
                                .padding(end = weekGapsDp[weekIndex])
                                .width(cell),
                            verticalArrangement = Arrangement.spacedBy(gap.dp),
                        ) {
                            (0..6).forEach { dayIndex ->
                                val date = startDate
                                    .plusWeeks(weekIndex.toLong())
                                    .plusDays(dayIndex.toLong())
                                val value = heatmapData[date]
                                val hasValue = value != null && value > 0f
                                val state = HeatmapScale.stateOf(
                                    hasValue = hasValue,
                                    isFuture = date.isAfter(today),
                                    isScheduled = date.dayOfWeek in scheduledDays,
                                    isBeforeCreation = createdAt != null && date.isBefore(createdAt),
                                )
                                val fraction = HeatmapScale.mixFraction(
                                    HeatmapScale.ratio(value ?: 0f, targetValue, rangeMax)
                                )

                                HeatCell(
                                    date = date,
                                    value = value,
                                    state = state,
                                    fraction = fraction,
                                    cell = cell,
                                    corner = corner,
                                    palette = palette,
                                    hatchColor = hatchColor,
                                    isSelected = date == selectedDate,
                                    isToday = date == today,
                                    dateLabel = dateLabel,
                                    unit = unit,
                                    onClick = {
                                        selectedDate =
                                            if (selectedDate == date) null else date
                                    },
                                )
                            }
                        }
                    }
                }
            }

            HeatmapLegend(
                palette = palette,
                hatchColor = hatchColor,
                hasUnscheduledDays = scheduledDays.size < DayOfWeek.entries.size,
            )
        }
    }
}

/* ══════════════════════════════════════════════════════════════════════════
   Cell
   ══════════════════════════════════════════════════════════════════════════ */

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeatCell(
    date: LocalDate,
    value: Float?,
    state: HeatCellState,
    fraction: Float,
    cell: Dp,
    corner: Dp,
    palette: HeatPalette,
    hatchColor: Color,
    isSelected: Boolean,
    isToday: Boolean,
    dateLabel: DateTimeFormatter,
    unit: String?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(corner)

    val baseColor = when (state) {
        HeatCellState.VALUE -> palette.tone(fraction)
        HeatCellState.UNSCHEDULED_VALUE ->
            lerp(palette.tone(fraction), palette.empty, UNSCHEDULED_VALUE_DIM)
        HeatCellState.EMPTY -> palette.empty
        HeatCellState.UNSCHEDULED_EMPTY -> palette.unscheduledEmpty
        HeatCellState.FUTURE, HeatCellState.BEFORE_CREATION -> palette.ghost
    }
    val color by animateColorAsState(
        targetValue = baseColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "cell_color",
    )

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) SELECTED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "cell_scale",
    )

    val selectable = state != HeatCellState.FUTURE && state != HeatCellState.BEFORE_CREATION
    val hatched = state == HeatCellState.UNSCHEDULED_VALUE ||
        state == HeatCellState.UNSCHEDULED_EMPTY

    val description = cellDescription(date, value, unit, dateLabel)

    Box(
        modifier = Modifier
            .size(cell)
            .graphicsLayer {
                scaleX = selectionScale
                scaleY = selectionScale
            }
            .semantics { contentDescription = description }
            .clip(shape)
            .background(color, shape)
            .then(if (hatched) Modifier.diagonalHatch(hatchColor) else Modifier)
            .then(
                if (isSelected) {
                    Modifier.border(SELECTION_RING_DP.dp, MaterialTheme.colorScheme.onSurface, shape)
                } else if (isToday) {
                    Modifier.border(TODAY_RING_DP.dp, palette.accentRing, shape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = selectable,
                onClick = onClick,
            ),
    )
}

@Composable
private fun cellDescription(
    date: LocalDate,
    value: Float?,
    unit: String?,
    formatter: DateTimeFormatter,
): String {
    val formattedDate = date.format(formatter)
    return if (value != null && value > 0f) {
        stringResource(
            R.string.habit_heatmap_day_cd,
            formattedDate,
            formatFloat(value) + (unit?.let { " $it" } ?: ""),
        )
    } else {
        stringResource(R.string.habit_heatmap_day_empty_cd, formattedDate)
    }
}

/** Subtle diagonal hatch for days outside the habit's schedule. */
private fun Modifier.diagonalHatch(color: Color): Modifier = this.drawBehind {
    val step = HATCH_STEP_DP.dp.toPx()
    val stroke = HATCH_STROKE_DP.dp.toPx()
    val h = size.height
    var x = -h
    while (x < size.width) {
        drawLine(
            color = color,
            start = Offset(x, h),
            end = Offset(x + h, 0f),
            strokeWidth = stroke,
        )
        x += step
    }
}

/* ══════════════════════════════════════════════════════════════════════════
   Header
   ══════════════════════════════════════════════════════════════════════════ */

@Composable
private fun HeatmapHeader(
    weeks: Int,
    selectedDate: LocalDate?,
    heatmapData: Map<LocalDate, Float>,
    unit: String?,
    habitColor: Color,
    dayLabel: DateTimeFormatter,
) {
    val average = remember(heatmapData) {
        val values = heatmapData.values.filter { it > 0f }
        if (values.isEmpty()) 0f else values.average().toFloat()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_ROW_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.habit_heatmap_weeks, weeks),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (selectedDate == null) {
                HeaderChip(container = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        text = "μ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatFloat(average),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                    if (!unit.isNullOrEmpty()) {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
            } else {
                HeaderChip(container = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        text = selectedDate.format(dayLabel).capitalizeFirst(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HeaderChip(container = habitColor.copy(alpha = CHIP_TINT_ALPHA)) {
                    Text(
                        text = formatFloat(heatmapData[selectedDate] ?: 0f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = habitColor,
                    )
                    if (!unit.isNullOrEmpty()) {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = habitColor,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(
    container: Color,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .height(CHIP_HEIGHT_DP.dp)
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private fun String.capitalizeFirst(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

/* ══════════════════════════════════════════════════════════════════════════
   Months and weekdays
   ══════════════════════════════════════════════════════════════════════════ */

@Composable
private fun MonthLabelRow(
    startDate: LocalDate,
    weeks: Int,
    cell: Dp,
    gap: Float,
    gridWidth: Dp,
    locale: java.util.Locale,
    weekXsDp: List<Float>,
) {
    val captionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val captionStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = MONTH_LABEL_SP.sp,
        color = captionColor,
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val candidates = remember(startDate, weeks) {
        MonthLabels.candidates((0 until weeks).map { startDate.plusWeeks(it.toLong()) })
    }

    val placed = remember(
        candidates, cell, gap, gridWidth, weekXsDp, locale, captionStyle, measurer, density,
    ) {
        MonthLabels.place(
            candidates = candidates,
            widthOf = { candidate ->
                val text = captionText(candidate, locale)
                val result = measurer.measure(AnnotatedString(text), captionStyle)
                with(density) { result.size.width.toDp().value }
            },
            cellDp = cell.value,
            gapDp = gap,
            totalWidthDp = gridWidth.value,
            xForWeek = { week -> weekXsDp[week] },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(MONTH_ROW_HEIGHT_DP.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(start = LABEL_COLUMN_DP.dp + LABEL_SPACER_DP.dp)
                .width(gridWidth)
                .height(MONTH_ROW_HEIGHT_DP.dp),
        ) {
            placed.forEach { item ->
                val text = captionText(item.candidate, locale)
                Text(
                    text = text,
                    style = captionStyle,
                    fontWeight = if (item.candidate.isJanuary) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .offset { IntOffset(x = with(density) { item.xDp.dp.roundToPx() }, y = 0) }
                        .align(Alignment.CenterStart),
                )
            }
        }
    }
}

private fun captionText(candidate: MonthLabelCandidate, locale: java.util.Locale): String {
    val month = java.time.Month.of(candidate.month)
        .getDisplayName(TextStyle.SHORT, locale)
    return if (candidate.showsYear) {
        "$month ${candidate.year.toString().takeLast(2)}"
    } else {
        month
    }
}

@Composable
private fun WeekdayColumn(
    cell: Dp,
    gap: Float,
    locale: java.util.Locale,
) {
    Column(
        modifier = Modifier.width(LABEL_COLUMN_DP.dp),
        verticalArrangement = Arrangement.spacedBy(gap.dp),
    ) {
        DayOfWeek.entries.forEachIndexed { index, dayOfWeek ->
            // Same row height and gap as the grid
            Box(
                modifier = Modifier.height(cell),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (index % 2 == 0) {
                    Text(
                        text = dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = WEEKDAY_LABEL_SP.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/* ══════════════════════════════════════════════════════════════════════════
   Legend
   ══════════════════════════════════════════════════════════════════════════ */

@Composable
private fun HeatmapLegend(
    palette: HeatPalette,
    hatchColor: Color,
    hasUnscheduledDays: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LEGEND_TOP_PADDING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasUnscheduledDays) {
            LegendHint(color = palette.unscheduledEmpty, hatch = hatchColor) {
                Text(
                    text = stringResource(R.string.habit_heatmap_unscheduled),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = LEGEND_SP.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.habit_heatmap_less),
                style = MaterialTheme.typography.labelSmall,
                fontSize = LEGEND_SP.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                palette.legendTones.forEach { tone ->
                    Box(
                        modifier = Modifier
                            .size(LEGEND_SWATCH_DP.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(tone),
                    )
                }
            }
            Text(
                text = stringResource(R.string.habit_heatmap_more),
                style = MaterialTheme.typography.labelSmall,
                fontSize = LEGEND_SP.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun LegendHint(
    color: Color,
    hatch: Color?,
    label: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(LEGEND_SWATCH_DP.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
                .then(if (hatch != null) Modifier.diagonalHatch(hatch) else Modifier),
        )
        Spacer(modifier = Modifier.width(4.dp))
        label()
    }
}

/* ══════════════════════════════════════════════════════════════════════════
   Palette and constants
   ══════════════════════════════════════════════════════════════════════════ */

private class HeatPalette(
    val empty: Color,
    val ghost: Color,
    val unscheduledEmpty: Color,
    val habit: Color,
) {
    /** Continuous tone: the habit color creeps in as the logged amount grows. */
    fun tone(fraction: Float): Color =
        if (fraction <= 0f) empty else lerp(empty, habit, fraction)

    /** Samples of the ramp for the legend, from empty to full habit color. */
    val legendTones: List<Color> = HeatmapScale.LEGEND_SAMPLES.map { tone(it) }

    /** Ring color for today; the habit color reads well over every tone. */
    val accentRing: Color = habit
}

private const val CELL_GAP_DP = 3f
private const val LABEL_COLUMN_DP = 16f
private const val LABEL_SPACER_DP = 5f
private const val MIN_CELL_DP = 5f
private const val MAX_CELL_DP = 18f
private const val CELL_CORNER_FRACTION = 0.28f
private const val GHOST_FRACTION = 0.28f
private const val UNSCHEDULED_EMPTY_FRACTION = 0.5f
private const val UNSCHEDULED_VALUE_DIM = 0.45f
private const val HATCH_ALPHA = 0.10f
private const val HATCH_STEP_DP = 3f
private const val HATCH_STROKE_DP = 1.1f
private const val SELECTED_SCALE = 1.1f
private const val SELECTION_RING_DP = 1.5f
private const val TODAY_RING_DP = 1.2f
private const val HEADER_ROW_HEIGHT_DP = 26f
private const val CHIP_HEIGHT_DP = 24f
private const val CHIP_TINT_ALPHA = 0.16f
private const val MONTH_ROW_HEIGHT_DP = 14f
private const val MONTH_LABEL_SP = 9.5f
private const val WEEKDAY_LABEL_SP = 9.5f
private const val LEGEND_TOP_PADDING_DP = 14f
private const val LEGEND_SWATCH_DP = 12f
private const val LEGEND_SP = 10f
