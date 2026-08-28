package com.markel.flowstate.core.domain.achievements

import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.HabitEntryFlat
import com.markel.flowstate.core.domain.HabitNumericEntry
import com.markel.flowstate.core.domain.HabitStreakCalculator
import com.markel.flowstate.core.domain.HabitType
import com.markel.flowstate.core.domain.isScheduledFor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Habit-derived raw values used by the achievement catalog. */
data class HabitAchievementMetrics(
    val totalCompletions: Int,
    val bestStreak: Int,
    val perfectDays: Int,
    val mondayPerfectDays: Int,
    val perfectWeeks: Int
)

/** Completed dates per habit (numeric entries must reach the target). */
private fun completedDatesByHabit(
    habits: List<Habit>,
    booleanEntries: List<HabitEntryFlat>,
    numericEntries: List<HabitNumericEntry>
): Map<Int, Set<LocalDate>> {
    val boolDatesByHabit: Map<Int, Set<LocalDate>> = booleanEntries
        .groupBy({ it.habitId }, { LocalDate.ofEpochDay(it.epochDay) })
        .mapValues { (_, dates) -> dates.toSet() }

    val numericByHabit = numericEntries.groupBy { it.habitId }

    return habits.associate { habit ->
        val dates = if (habit.habitType == HabitType.NUMERIC) {
            HabitStreakCalculator.qualifyingNumericDates(
                numericByHabit[habit.id].orEmpty(),
                habit.targetValue
            )
        } else {
            boolDatesByHabit[habit.id].orEmpty()
        }
        habit.id to dates
    }
}

/**
 * Computes [HabitAchievementMetrics] from the raw habit data.
 *
 * Pure (no Android, no I/O) so it can be unit-tested. A "perfect day"
 * means: every habit that already existed on that date AND was scheduled
 * for it is completed. Days with nothing scheduled don't count either way.
 */
fun computeHabitAchievementMetrics(
    habits: List<Habit>,
    booleanEntries: List<HabitEntryFlat>,
    numericEntries: List<HabitNumericEntry>,
    today: LocalDate = LocalDate.now()
): HabitAchievementMetrics {
    if (habits.isEmpty()) {
        return HabitAchievementMetrics(0, 0, 0, 0, 0)
    }

    val datesByHabit = completedDatesByHabit(habits, booleanEntries, numericEntries)

    val bestStreak = habits.maxOf { habit ->
        HabitStreakCalculator.best(
            datesByHabit[habit.id].orEmpty(),
            habit.scheduledDays
        )
    }

    return HabitAchievementMetrics(
        totalCompletions = datesByHabit.values.sumOf { it.size },
        bestStreak = bestStreak,
        perfectDays = countPerfectDays(habits, datesByHabit, today),
        mondayPerfectDays = countPerfectDays(habits, datesByHabit, today, DayOfWeek.MONDAY),
        perfectWeeks = countPerfectWeeks(habits, datesByHabit, today)
    )
}

/**
 * Number of dates in which every habit that already existed and was
 * scheduled that day is completed. Optionally restricted to a single
 * [onlyDayOfWeek]. The search starts at the oldest habit creation date
 * and stops at [today].
 */
internal fun countPerfectDays(
    habits: List<Habit>,
    datesByHabit: Map<Int, Set<LocalDate>>,
    today: LocalDate,
    onlyDayOfWeek: DayOfWeek? = null
): Int {
    val start = habits.minOf { it.createdAt }
    if (start.isAfter(today)) return 0

    var perfect = 0
    var date = start
    while (!date.isAfter(today)) {
        if (onlyDayOfWeek == null || date.dayOfWeek == onlyDayOfWeek) {
            val relevant = habits.filter { !it.createdAt.isAfter(date) && it.isScheduledFor(date) }
            if (relevant.isNotEmpty() &&
                relevant.all { date in datesByHabit[it.id].orEmpty() }
            ) {
                perfect++
            }
        }
        date = date.plusDays(1)
    }
    return perfect
}

/**
 * Number of COMPLETE calendar weeks (Monday..Sunday, already finished)
 * in which every scheduled habit occurrence is completed. The current
 * unfinished week never counts.
 */
internal fun countPerfectWeeks(
    habits: List<Habit>,
    datesByHabit: Map<Int, Set<LocalDate>>,
    today: LocalDate
): Int {
    val start = habits.minOf { it.createdAt }
    if (start.isAfter(today)) return 0

    val firstMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastCompletedWeekStart =
        today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY))

    var perfect = 0
    var weekStart = firstMonday
    while (!weekStart.isAfter(lastCompletedWeekStart)) {
        var allOk = true
        for (offset in 0L..6L) {
            val date = weekStart.plusDays(offset)
            if (date.isBefore(start) || date.isAfter(today)) continue
            val relevant = habits.filter { !it.createdAt.isAfter(date) && it.isScheduledFor(date) }
            if (relevant.isNotEmpty() &&
                !relevant.all { date in datesByHabit[it.id].orEmpty() }
            ) {
                allOk = false
                break
            }
        }
        if (allOk) perfect++
        weekStart = weekStart.plusWeeks(1)
    }
    return perfect
}
