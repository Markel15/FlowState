package com.markel.flowstate.core.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Calculates streaks in scheduled habit occurrences rather than consecutive
 * calendar days. Days on which the habit is not scheduled are ignored.
 *
 * Entries completed on dates before the habit's creation date (for example
 * backfilled from the week calendar) still count towards the streak.
 */
object HabitStreakCalculator {

    fun current(
        completedDates: Collection<LocalDate>,
        scheduledDays: Set<DayOfWeek>,
        today: LocalDate = LocalDate.now()
    ): Int {
        if (completedDates.isEmpty() || scheduledDays.isEmpty()) return 0

        val validDates = completedDates.asSequence()
            .filter { it.dayOfWeek in scheduledDays }
            .filter { !it.isAfter(today) }
            .toSet()

        if (validDates.isEmpty()) return 0

        // If today is scheduled but still pending, the streak survives one
        // grace occurrence: counting starts from the previous scheduled date.
        var expected = if (today.dayOfWeek in scheduledDays && today in validDates) {
            today
        } else {
            previousScheduledDate(today, scheduledDays)
        }

        var streak = 0
        while (expected in validDates) {
            streak++
            expected = previousScheduledDate(expected, scheduledDays)
        }
        return streak
    }

    fun best(
        completedDates: Collection<LocalDate>,
        scheduledDays: Set<DayOfWeek>
    ): Int {
        if (completedDates.isEmpty() || scheduledDays.isEmpty()) return 0

        val validDates = completedDates.asSequence()
            .filter { it.dayOfWeek in scheduledDays }
            .distinct()
            .sorted()
            .toList()

        if (validDates.isEmpty()) return 0

        var best = 1
        var current = 1
        for (index in 1 until validDates.size) {
            val previous = validDates[index - 1]
            val date = validDates[index]
            if (date == nextScheduledDate(previous, scheduledDays)) {
                current++
                best = maxOf(best, current)
            } else {
                current = 1
            }
        }
        return best
    }

    fun qualifyingNumericDates(
        entries: Collection<HabitNumericEntry>,
        targetValue: Float?
    ): Set<LocalDate> = entries.asSequence()
        .filter { entry ->
            if (targetValue != null) entry.value >= targetValue else entry.value > 0f
        }
        .mapTo(mutableSetOf()) { it.date }

    private fun previousScheduledDate(
        date: LocalDate,
        scheduledDays: Set<DayOfWeek>
    ): LocalDate {
        var candidate = date.minusDays(1)
        while (candidate.dayOfWeek !in scheduledDays) {
            candidate = candidate.minusDays(1)
        }
        return candidate
    }

    private fun nextScheduledDate(
        date: LocalDate,
        scheduledDays: Set<DayOfWeek>
    ): LocalDate {
        var candidate = date.plusDays(1)
        while (candidate.dayOfWeek !in scheduledDays) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }
}
