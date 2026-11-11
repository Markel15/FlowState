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

/**
 * Habit-derived raw values used by the achievement catalog.
 *
 * Every value is kept as the LIST of dates that produced it instead of as a
 * bare counter: the counter is just the list size, and the list is what lets
 * the UI say when each tier was crossed. Nothing new is stored — the lists are
 * rebuilt from Room on every emission, so they stay coherent with the data
 * (delete an entry and its date disappears with it).
 */
data class HabitAchievementMetrics(
    /** Every completed habit occurrence, across habits, ascending. */
    val completionDates: List<LocalDate>,
    /** Streaks actually achieved, one entry per run. */
    val streakRuns: List<StreakRun>,
    val perfectDayDates: List<LocalDate>,
    val mondayPerfectDayDates: List<LocalDate>,
    val perfectWeekDates: List<LocalDate>,
    /** Times a habit was completed right after missing 3+ scheduled occurrences in a row. */
    val comebackDates: List<LocalDate>
) {
    val totalCompletions: Int get() = completionDates.size
    val bestStreak: Int get() = streakRuns.maxOfOrNull { it.length } ?: 0
    val perfectDays: Int get() = perfectDayDates.size
    val mondayPerfectDays: Int get() = mondayPerfectDayDates.size
    val perfectWeeks: Int get() = perfectWeekDates.size
    val comebacks: Int get() = comebackDates.size

    companion object {
        val EMPTY = HabitAchievementMetrics(
            completionDates = emptyList(),
            streakRuns = emptyList(),
            perfectDayDates = emptyList(),
            mondayPerfectDayDates = emptyList(),
            perfectWeekDates = emptyList(),
            comebackDates = emptyList()
        )
    }
}

/**
 * A stretch of [length] consecutive scheduled occurrences completed without a
 * miss, ending on [end].
 */
data class StreakRun(
    val end: LocalDate,
    val length: Int,
    val scheduledDays: Set<DayOfWeek>
) {
    /**
     * Date of the occurrence in which the run reached [target] occurrences, or
     * null when the run never got that long. Counts SCHEDULED occurrences, not
     * calendar days, so a Monday/Friday habit reaching 3 lands two weeks before
     * a daily one would.
     */
    fun dateReaching(target: Int): LocalDate? {
        if (target > length || scheduledDays.isEmpty()) return null
        var remaining = length - target
        var date = end
        while (remaining > 0) {
            date = previousScheduledDate(date, scheduledDays)
            remaining--
        }
        return date
    }

    private fun previousScheduledDate(date: LocalDate, days: Set<DayOfWeek>): LocalDate {
        var candidate = date.minusDays(1)
        while (candidate.dayOfWeek !in days) {
            candidate = candidate.minusDays(1)
        }
        return candidate
    }
}

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
    if (habits.isEmpty()) return HabitAchievementMetrics.EMPTY

    val datesByHabit = completedDatesByHabit(habits, booleanEntries, numericEntries)

    return HabitAchievementMetrics(
        completionDates = datesByHabit.values.flatten().sorted(),
        streakRuns = habits.flatMap { habit ->
            computeStreakRuns(datesByHabit[habit.id].orEmpty(), habit.scheduledDays)
        },
        perfectDayDates = perfectDays(habits, datesByHabit, today),
        mondayPerfectDayDates = perfectDays(habits, datesByHabit, today, DayOfWeek.MONDAY),
        perfectWeekDates = perfectWeeks(habits, datesByHabit, today),
        comebackDates = habits.flatMap { habit ->
            comebackDates(habit, datesByHabit[habit.id].orEmpty(), today)
        }.sorted()
    )
}

/**
 * All the runs of consecutive scheduled occurrences in [completedDates],
 * ascending. Mirrors [HabitStreakCalculator.best], which is now
 * `runs.maxOf { it.length }`.
 */
internal fun computeStreakRuns(
    completedDates: Collection<LocalDate>,
    scheduledDays: Set<DayOfWeek>
): List<StreakRun> {
    if (completedDates.isEmpty() || scheduledDays.isEmpty()) return emptyList()

    val dates = completedDates.asSequence()
        .filter { it.dayOfWeek in scheduledDays }
        .distinct()
        .sorted()
        .toList()

    if (dates.isEmpty()) return emptyList()

    val runs = mutableListOf<StreakRun>()
    var runEnd = dates.first()
    var runLength = 1

    for (index in 1 until dates.size) {
        val previous = dates[index - 1]
        val date = dates[index]
        if (date == nextScheduledDate(previous, scheduledDays)) {
            runLength++
        } else {
            runs += StreakRun(runEnd, runLength, scheduledDays)
            runLength = 1
        }
        runEnd = date
    }
    runs += StreakRun(runEnd, runLength, scheduledDays)
    return runs
}

private fun nextScheduledDate(date: LocalDate, scheduledDays: Set<DayOfWeek>): LocalDate {
    var candidate = date.plusDays(1)
    while (candidate.dayOfWeek !in scheduledDays) {
        candidate = candidate.plusDays(1)
    }
    return candidate
}

/**
 * Dates in which every habit that already existed and was scheduled that day is
 * completed. Optionally restricted to a single [onlyDayOfWeek]. The search
 * starts at the oldest habit creation date and stops at [today].
 */
internal fun perfectDays(
    habits: List<Habit>,
    datesByHabit: Map<Int, Set<LocalDate>>,
    today: LocalDate,
    onlyDayOfWeek: DayOfWeek? = null
): List<LocalDate> {
    val start = habits.minOf { it.createdAt }
    if (start.isAfter(today)) return emptyList()

    val perfect = mutableListOf<LocalDate>()
    var date = start
    while (!date.isAfter(today)) {
        if (onlyDayOfWeek == null || date.dayOfWeek == onlyDayOfWeek) {
            val relevant = habits.filter { !it.createdAt.isAfter(date) && it.isScheduledFor(date) }
            if (relevant.isNotEmpty() &&
                relevant.all { date in datesByHabit[it.id].orEmpty() }
            ) {
                perfect += date
            }
        }
        date = date.plusDays(1)
    }
    return perfect
}

private const val COMEBACK_MIN_MISSED = 3

/**
 * Dates in which the habit was completed right after a run of at least
 * [COMEBACK_MIN_MISSED] consecutive MISSED scheduled occurrences. Only past
 * scheduled days count as missed (today is still pending), and the scan starts
 * at the habit's creation — or at an earlier backfilled entry.
 */
internal fun comebackDates(
    habit: Habit,
    completedDates: Set<LocalDate>,
    today: LocalDate
): List<LocalDate> {
    val firstEntry = completedDates.minOrNull() ?: habit.createdAt
    val start = minOf(habit.createdAt, firstEntry)
    if (start.isAfter(today)) return emptyList()

    val dates = mutableListOf<LocalDate>()
    var missed = 0
    var date = start
    while (!date.isAfter(today)) {
        if (habit.isScheduledFor(date)) {
            when {
                date in completedDates -> {
                    if (missed >= COMEBACK_MIN_MISSED) dates += date
                    missed = 0
                }
                date.isBefore(today) -> missed++
                // Today is still pending: neither a miss nor a completion.
            }
        }
        date = date.plusDays(1)
    }
    return dates
}

/**
 * The Monday of every COMPLETE calendar week (Monday..Sunday, already finished)
 * in which every scheduled habit occurrence is completed. The current
 * unfinished week never counts.
 */
internal fun perfectWeeks(
    habits: List<Habit>,
    datesByHabit: Map<Int, Set<LocalDate>>,
    today: LocalDate
): List<LocalDate> {
    val start = habits.minOf { it.createdAt }
    if (start.isAfter(today)) return emptyList()

    val firstMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastCompletedWeekStart = today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY))

    val weeks = mutableListOf<LocalDate>()
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
        if (allOk) weeks += weekStart
        weekStart = weekStart.plusWeeks(1)
    }
    return weeks
}
