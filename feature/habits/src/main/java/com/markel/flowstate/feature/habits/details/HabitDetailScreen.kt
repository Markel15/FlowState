package com.markel.flowstate.feature.habits.details

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import com.markel.flowstate.core.designsystem.R as DesignR
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.markel.flowstate.feature.habits.R
import com.markel.flowstate.feature.habits.details.components.numeric.NumericEvolutionCard
import com.markel.flowstate.feature.habits.details.components.bool.HabitMonthCalendar
import com.markel.flowstate.feature.habits.details.components.bool.RadarChart
import com.markel.flowstate.feature.habits.details.components.SectionHeader
import com.markel.flowstate.feature.habits.details.components.bool.BooleanHabitSummaryCard
import com.markel.flowstate.feature.habits.details.components.bool.StatCard
import com.markel.flowstate.feature.habits.details.components.bool.WeeklyBarsCard
import com.markel.flowstate.feature.habits.details.components.numeric.MonthlyGoalCard
import com.markel.flowstate.feature.habits.details.components.numeric.NumericHabitSummaryCard
import com.markel.flowstate.feature.habits.details.components.numeric.NumericHeatmapCard
import com.markel.flowstate.feature.habits.details.components.numeric.ValueDistributionCard
import com.markel.flowstate.feature.habits.util.formatFloat

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HabitDetailScreen(
    habitId: Int,
    onBack: () -> Unit,
    viewModel: HabitDetailViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val habit = state.habit ?: return
    val habitColor = Color(habit.colorArgb)
    val locale = LocalLocale.current.platformLocale

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top bar + Hero ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = habitColor.copy(alpha = 0.20f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(DesignR.drawable.arrow_back_24px),
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = habit.name,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp)
                    .basicMarquee(repeatDelayMillis = 3500)
            )
        }

        // ── Header summary ──────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.habit_detail_section_summary))
        if (state.isNumeric) {
            val avgValue = state.monthlyProgress?.dailyAverage ?: 0f
            NumericHabitSummaryCard(
                startDate = habit.createdAt,
                currentStreak = state.currentStreak,
                bestStreak = state.bestStreak,
                averageValue = avgValue,
                unit = habit.unit ?: "",
                accentColor = habitColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
            )
        } else {
            BooleanHabitSummaryCard(
                startDate = habit.createdAt,
                currentStreak = state.currentStreak,
                bestStreak = state.bestStreak,
                consistency = state.completionPct(),
                consistencyLabel = state.pctLabel(),
                accentColor = habitColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            )
        }

        if (state.isNumeric) {
            // Evolution "line chart"
            SectionHeader(title = stringResource(R.string.habit_detail_evolution))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                NumericEvolutionCard(
                    dailyValues = state.dailyValues,
                    targetValue = habit.targetValue,
                    habitColor = habitColor,
                    unit = habit.unit,
                    modifier = Modifier.padding(16.dp)
                )
            }

            SectionHeader(title = stringResource(R.string.habit_heatmap))
            // Heatmap
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                NumericHeatmapCard(
                    heatmapData = state.heatmapData,
                    targetValue = habit.targetValue,
                    habitColor = habitColor,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Monthly goal
            if (habit.targetValue != null) {
                SectionHeader(title = stringResource(R.string.habit_monthly_goal))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    MonthlyGoalCard(
                        progress = state.monthlyProgress,
                        habitColor = habitColor,
                        unit = habit.unit,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.habit_detail_average))
            // Weekly averages
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                ValueDistributionCard(
                    distribution = state.dayOfWeekAverages,
                    habitColor = habitColor,
                    unit = habit.unit,
                    modifier = Modifier.padding(16.dp)
                )
            }

        }
        else {

            // ── History ─────────────────────────────────────────────
            SectionHeader(
                title = stringResource(R.string.habit_detail_section_history),
                actionLabel = state.viewMode.label(),
                onAction = { viewModel.cycleViewMode() }
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                // Navigation arrows
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { viewModel.navigatePrevious() }) {
                        Text("‹", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = state.navigationLabel(locale),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = { viewModel.navigateNext() }) {
                        Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                AnimatedContent(
                    targetState = state.viewMode,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "calendar_view"
                ) { mode ->
                    when (mode) {
                        CalendarViewMode.ONE_MONTH -> {
                            HabitMonthCalendar(
                                year = state.displayYear,
                                month = state.displayMonth,
                                completedEpochDays = state.allEntries,
                                habitColor = habitColor,
                                modifier = Modifier.padding(
                                    start = 12.dp, end = 12.dp, bottom = 12.dp
                                )
                            )
                        }

                        CalendarViewMode.THREE_MONTHS -> {
                            Row(
                                modifier = Modifier.padding(
                                    start = 8.dp, end = 8.dp, bottom = 12.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                (2 downTo 0).forEach { monthsAgo ->
                                    var m = state.displayMonth - monthsAgo
                                    var y = state.displayYear
                                    if (m < 0) {
                                        m += 12; y--
                                    }
                                    HabitMonthCalendar(
                                        year = y, month = m,
                                        completedEpochDays = state.allEntries,
                                        habitColor = habitColor,
                                        showMonthLabel = true,
                                        compact = false,
                                        showNumbers = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        CalendarViewMode.ONE_YEAR -> {
                            Column(
                                modifier = Modifier.padding(
                                    start = 12.dp, end = 12.dp, bottom = 12.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                (0..11).chunked(4).forEach { rowMonths ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowMonths.forEach { m ->
                                            HabitMonthCalendar(
                                                year = state.displayYear,
                                                month = m,
                                                completedEpochDays = state.allEntries,
                                                habitColor = habitColor,
                                                showMonthLabel = true,
                                                compact = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Weekly Bars ──────────────────────────────────────
            val selectedWeekData = run {
                val data = state.weeklyCompletions.takeLast(state.weeklyBarsRange.weeks)
                val idx = state.selectedBarIndex ?: (data.size - 1)
                data.getOrNull(idx)
            }

            val weekLabel = selectedWeekData?.first?.let { weekStart ->
                val now = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
                if (weekStart == now) stringResource(R.string.habit_detail_this_week)
                else "${weekStart.dayOfMonth} ${
                    weekStart.month
                        .getDisplayName(
                            java.time.format.TextStyle.SHORT,
                            LocalLocale.current.platformLocale
                        )
                }"
            }

            SectionHeader(
                title =
                    if (weekLabel != null)
                        stringResource(R.string.habit_detail_section_weekly, weekLabel)
                    else
                        stringResource(R.string.habit_detail_section_weekly_empty)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                WeeklyBarsCard(
                    weeklyCompletions = state.weeklyCompletions,
                    selectedIndex = state.selectedBarIndex,
                    barsRange = state.weeklyBarsRange,
                    habitColor = habitColor,
                    onBarSelected = { viewModel.selectBar(it) },
                    onRangeChanged = { viewModel.setWeeklyBarsRange(it) },
                    modifier = Modifier.padding(16.dp)
                )
            }

            // ── Radar chart ───────────────────────────────────────────────
            SectionHeader(title = stringResource(R.string.habit_detail_section_radar))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RadarChart(
                        dayOfWeekCompletions = state.dayOfWeekCompletions,
                        habitColor = habitColor
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}