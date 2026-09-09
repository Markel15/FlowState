package com.markel.flowstate.feature.habits.details.components.bool

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markel.flowstate.feature.habits.details.WeeklyBarsRange
import java.time.LocalDate
import com.markel.flowstate.feature.habits.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeeklyBarsCard(
    weeklyCompletions: List<Pair<LocalDate, Int>>,
    selectedIndex: Int?,
    barsRange: WeeklyBarsRange,
    habitColor: Color,
    onBarSelected: (Int) -> Unit,
    onRangeChanged: (WeeklyBarsRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val data = weeklyCompletions.takeLast(barsRange.weeks)

    val effectiveSelected = selectedIndex ?: (data.size - 1)
    val max = data
        .map { it.second.toDouble() }
        .takeIf { it.any { v -> v != 0.0 } }
        ?.maxOrNull()

    val badgeBgColor = habitColor.copy(
        red = habitColor.red * 0.55f,
        green = habitColor.green * 0.55f,
        blue = habitColor.blue * 0.55f
    )

    // For long ranges (>= ~4 months) the bars get too thin to show a number,
    // so we drop the per-bar count badges and rely on tap-to-select.
    val showBadges = data.size <= 8
    val badgeSize = when {
        data.size <= 8 -> 24.dp
        data.size <= 18 -> 16.dp
        else -> 0.dp
    }
    val badgeSizeTertiary = when {
        data.size <= 8 -> 20.dp
        data.size <= 18 -> 13.dp
        else -> 0.dp
    }
    val barSpacing = if (data.size <= 18) 2.dp else 1.dp

    val options = listOf(
        WeeklyBarsRange.TWO_MONTHS to stringResource(R.string.weekly_bars_2m),
        WeeklyBarsRange.FOUR_MONTHS to stringResource(R.string.weekly_bars_4m),
        WeeklyBarsRange.EIGHT_MONTHS to stringResource(R.string.weekly_bars_8m),
        WeeklyBarsRange.ONE_YEAR to stringResource(R.string.weekly_bars_1y)
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                ButtonGroupDefaults.ConnectedSpaceBetween
            )
        ) {
            options.forEachIndexed { i, (range, label) ->
                ToggleButton(
                    checked = barsRange == range,
                    onCheckedChange = { if (it) onRangeChanged(range) },
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        checkedContainerColor = habitColor,
                        checkedContentColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(text = label)
                }
            }
        }

        if (max != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(barSpacing)
            ) {
                data.forEachIndexed { index, (_, count) ->
                    val isSelected = index == effectiveSelected
                    val isMax = count.toDouble() == max
                    val isAboveHalf = count > (max / 2) && showBadges
                    val barColor = if (isSelected) habitColor else habitColor.copy(alpha = 0.3f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (count > 0) Modifier.clickable { onBarSelected(index) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // If count = 0 we don't draw the bar
                        if (count > 0) {
                            val barHeight = ((count.toFloat() / max.toFloat()) * 140).dp

                            Box(
                                modifier = Modifier
                                    .height(barHeight.coerceAtLeast(8.dp))
                                    .fillMaxWidth()
                                    .background(
                                        color = barColor,
                                        shape = CircleShape
                                    )
                            ) {
                                if (showBadges) {
                                    when {
                                        isMax -> Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = 4.dp)
                                                .size(badgeSize)
                                                .background(
                                                    color = badgeBgColor,
                                                    shape = MaterialShapes.SoftBurst.toShape()
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        isAboveHalf -> Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = 4.dp)
                                                .size(badgeSizeTertiary)
                                                .background(
                                                    color = badgeBgColor.copy(alpha = 0.7f),
                                                    shape = MaterialShapes.Circle.toShape()
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }

                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.habit_detail_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
