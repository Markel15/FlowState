package com.markel.flowstate.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitStreakCalculatorTest {

    private val monday = LocalDate.of(2026, 8, 17)
    private val everyDay = DayOfWeek.entries.toSet()
    private val mondayWednesdayFriday = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.FRIDAY
    )

    @Test
    fun current_dailyHabit_preservesConsecutiveDayBehaviour() {
        val completed = setOf(
            monday,
            monday.minusDays(1),
            monday.minusDays(2),
            monday.minusDays(4)
        )

        assertEquals(
            3,
            HabitStreakCalculator.current(completed, everyDay, today = monday)
        )
    }

    @Test
    fun current_pendingScheduledToday_doesNotBreakPreviousStreak() {
        val completed = setOf(
            monday.minusWeeks(1),
            monday.minusWeeks(2)
        )

        assertEquals(
            2,
            HabitStreakCalculator.current(
                completedDates = completed,
                scheduledDays = setOf(DayOfWeek.MONDAY),
                today = monday
            )
        )
    }

    @Test
    fun current_ignoresDaysBetweenScheduledOccurrences() {
        val friday = monday.plusDays(4)
        val completed = setOf(
            monday,
            monday.plusDays(2),
            friday
        )

        assertEquals(
            3,
            HabitStreakCalculator.current(
                completedDates = completed,
                scheduledDays = mondayWednesdayFriday,
                today = friday
            )
        )
    }

    @Test
    fun current_missedScheduledOccurrence_breaksStreak() {
        val friday = monday.plusDays(4)
        val completed = setOf(monday, friday)

        assertEquals(
            1,
            HabitStreakCalculator.current(
                completedDates = completed,
                scheduledDays = mondayWednesdayFriday,
                today = friday
            )
        )
    }

    @Test
    fun current_afterMissedScheduledOccurrence_isZero() {
        val thursday = monday.plusDays(3)

        assertEquals(
            0,
            HabitStreakCalculator.current(
                completedDates = setOf(monday),
                scheduledDays = mondayWednesdayFriday,
                today = thursday
            )
        )
    }

    @Test
    fun current_ignoresCompletionOnUnscheduledDay() {
        val wednesday = monday.plusDays(2)
        val tuesday = monday.plusDays(1)

        assertEquals(
            2,
            HabitStreakCalculator.current(
                completedDates = setOf(monday, tuesday, wednesday),
                scheduledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                today = wednesday
            )
        )
    }

    @Test
    fun current_handlesSundayToMondayTransition() {
        val sunday = monday.minusDays(1)

        assertEquals(
            2,
            HabitStreakCalculator.current(
                completedDates = setOf(sunday, monday),
                scheduledDays = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY),
                today = monday
            )
        )
    }

    @Test
    fun current_doesNotCountEntriesBeforeHabitCreation() {
        assertEquals(
            1,
            HabitStreakCalculator.current(
                completedDates = setOf(monday.minusDays(1), monday),
                scheduledDays = everyDay,
                today = monday,
                startedOn = monday
            )
        )
    }

    @Test
    fun best_countsLongestRunOfScheduledOccurrences() {
        val completed = setOf(
            monday,
            monday.plusDays(2),
            monday.plusDays(4),
            monday.plusWeeks(1),
            monday.plusWeeks(1).plusDays(4)
        )

        assertEquals(
            4,
            HabitStreakCalculator.best(completed, mondayWednesdayFriday)
        )
    }

    @Test
    fun best_dailyHabit_preservesConsecutiveDayBehaviour() {
        val completed = setOf(
            monday,
            monday.plusDays(1),
            monday.plusDays(3),
            monday.plusDays(4),
            monday.plusDays(5)
        )

        assertEquals(3, HabitStreakCalculator.best(completed, everyDay))
    }

    @Test
    fun emptySchedule_hasNoStreak() {
        assertEquals(
            0,
            HabitStreakCalculator.current(setOf(monday), emptySet(), today = monday)
        )
        assertEquals(0, HabitStreakCalculator.best(setOf(monday), emptySet()))
    }

    @Test
    fun qualifyingNumericDates_requiresTargetWhenPresent() {
        val entries = listOf(
            numericEntry(monday, 10f),
            numericEntry(monday.plusDays(1), 9f),
            numericEntry(monday.plusDays(2), 12f)
        )

        assertEquals(
            setOf(monday, monday.plusDays(2)),
            HabitStreakCalculator.qualifyingNumericDates(entries, targetValue = 10f)
        )
    }

    @Test
    fun qualifyingNumericDates_withoutTarget_requiresPositiveValue() {
        val entries = listOf(
            numericEntry(monday, 1f),
            numericEntry(monday.plusDays(1), 0f),
            numericEntry(monday.plusDays(2), -1f)
        )

        assertEquals(
            setOf(monday),
            HabitStreakCalculator.qualifyingNumericDates(entries, targetValue = null)
        )
    }

    private fun numericEntry(date: LocalDate, value: Float) = HabitNumericEntry(
        habitId = 1,
        date = date,
        value = value
    )
}
