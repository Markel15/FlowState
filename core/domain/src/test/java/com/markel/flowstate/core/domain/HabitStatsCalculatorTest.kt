package com.markel.flowstate.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitStatsCalculatorTest {

    private val monday = LocalDate.of(2026, 8, 17)
    private val everyDay = DayOfWeek.entries.toSet()
    private val mondayWednesdayFriday = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.FRIDAY
    )

    @Test
    fun countScheduledDaysBetween_fullWeek_countsEveryScheduledWeekdayOnce() {
        val sunday = monday.plusDays(6)
        assertEquals(
            7,
            HabitStatsCalculator.countScheduledDaysBetween(monday, sunday, everyDay)
        )
        assertEquals(
            3,
            HabitStatsCalculator.countScheduledDaysBetween(monday, sunday, mondayWednesdayFriday)
        )
    }

    @Test
    fun countScheduledDaysBetween_twoWeeks_mwf_countsSix() {
        val end = monday.plusDays(13)
        assertEquals(
            6,
            HabitStatsCalculator.countScheduledDaysBetween(monday, end, mondayWednesdayFriday)
        )
    }

    @Test
    fun countScheduledDaysBetween_singleDay_countsOnlyIfScheduled() {
        assertEquals(
            1,
            HabitStatsCalculator.countScheduledDaysBetween(monday, monday, mondayWednesdayFriday)
        )
        assertEquals(
            0,
            HabitStatsCalculator.countScheduledDaysBetween(
                monday.plusDays(1), monday.plusDays(1), mondayWednesdayFriday
            )
        )
    }

    @Test
    fun countScheduledDaysBetween_partialWeek_countsElapsedOccurrencesOnly() {
        // Mon 17 .. Sat 22 of the same week -> M, W, F = 3
        assertEquals(
            3,
            HabitStatsCalculator.countScheduledDaysBetween(
                monday, monday.plusDays(5), mondayWednesdayFriday
            )
        )
    }

    @Test
    fun countScheduledDaysBetween_invertedRangeOrEmptySchedule_isZero() {
        assertEquals(
            0,
            HabitStatsCalculator.countScheduledDaysBetween(monday, monday.minusDays(1), everyDay)
        )
        assertEquals(
            0,
            HabitStatsCalculator.countScheduledDaysBetween(monday, monday.plusDays(6), emptySet())
        )
    }

    @Test
    fun consistencyPercent_scheduledHabit_measuresAgainstScheduledOccurrences() {
        // Two full weeks (M/W/F -> 6 opportunities), 5 done on scheduled
        // days and 1 extra completion on an unscheduled day that is ignored
        val end = monday.plusDays(13)
        val completed = setOf(
            monday, monday.plusDays(2), monday.plusDays(4),
            monday.plusDays(7), monday.plusDays(9),
            monday.plusDays(1) // Tuesday: not scheduled, must not count
        )

        assertEquals(
            83, // floor(5 * 100 / 6)
            HabitStatsCalculator.consistencyPercent(completed, mondayWednesdayFriday, monday, end)
        )
    }

    @Test
    fun consistencyPercent_dailyHabit_everyScheduledDayDone_is100() {
        val end = monday.plusDays(6)
        val completed = (0..6).map { monday.plusDays(it.toLong()) }

        assertEquals(
            100,
            HabitStatsCalculator.consistencyPercent(completed, everyDay, monday, end)
        )
    }

    @Test
    fun consistencyPercent_partialDailyCompletion_floorsLikeBefore() {
        val end = monday.plusDays(6)
        val completed = setOf(monday, monday.plusDays(1), monday.plusDays(2))

        assertEquals(
            42, // floor(3 * 100 / 7), mimics the old calendar-day math
            HabitStatsCalculator.consistencyPercent(completed, everyDay, monday, end)
        )
    }

    @Test
    fun consistencyPercent_noScheduledOccurrencesInRange_isNull() {
        // Sunday-only habit measured over Mon..Sat
        assertNull(
            HabitStatsCalculator.consistencyPercent(
                completedDates = setOf(monday),
                scheduledDays = setOf(DayOfWeek.SUNDAY),
                start = monday,
                end = monday.plusDays(5)
            )
        )
    }

    @Test
    fun consistencyPercent_duplicatedCompletions_doNotInflateTheResult() {
        val end = monday.plusDays(6)
        val completed = listOf(monday, monday, monday.plusDays(1))

        assertEquals(
            28, // 2 distinct completed days out of 7
            HabitStatsCalculator.consistencyPercent(completed, everyDay, monday, end)
        )
    }
}
