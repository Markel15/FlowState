package com.markel.flowstate.core.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Scheduled-days-aware statistics for habits. Complements
 * [HabitStreakCalculator] with counters/denominators so that habits
 * scheduled only some weekdays are measured against their own
 * opportunities instead of against every calendar day.
 */
object HabitStatsCalculator {

    /**
     * Number of days in [start]..[end] (both inclusive) whose weekday is in
     * [scheduledDays]. Returns 0 for an empty schedule or inverted range.
     * O(7) — no day-by-day iteration.
     */
    fun countScheduledDaysBetween(
        start: LocalDate,
        end: LocalDate,
        scheduledDays: Set<DayOfWeek>
    ): Int {
        if (start.isAfter(end) || scheduledDays.isEmpty()) return 0
        val totalDays = ChronoUnit.DAYS.between(start, end) + 1
        val startDow = start.dayOfWeek.value
        return scheduledDays.sumOf { dow ->
            val diff = (dow.value - startDow + 7) % 7L
            val occurrences = if (diff < totalDays) 1L + (totalDays - 1L - diff) / 7L else 0L
            occurrences.toInt()
        }
    }

    /**
     * Completion percentage (floor, 0..100) of the scheduled occurrences in
     * [start]..[end] (both inclusive) that were completed. Completions on
     * unscheduled weekdays are ignored, matching the streak semantics.
     *
     * Returns null when the range has no scheduled occurrences, so callers
     * can decide what to display instead of dividing by zero.
     */
    fun consistencyPercent(
        completedDates: Collection<LocalDate>,
        scheduledDays: Set<DayOfWeek>,
        start: LocalDate,
        end: LocalDate
    ): Int? {
        val opportunities = countScheduledDaysBetween(start, end, scheduledDays)
        if (opportunities == 0) return null
        val done = completedDates
            .asSequence()
            .distinct()
            .count { date ->
                date.dayOfWeek in scheduledDays &&
                        !date.isBefore(start) && !date.isAfter(end)
            }
        return done * 100 / opportunities
    }
}
