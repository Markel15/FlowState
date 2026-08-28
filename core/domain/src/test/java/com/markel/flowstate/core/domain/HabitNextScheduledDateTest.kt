package com.markel.flowstate.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitNextScheduledDateTest {

    private val monday = LocalDate.of(2026, 8, 17)

    private fun habit(scheduledDays: Set<DayOfWeek>) = Habit(
        name = "Test habit",
        scheduledDays = scheduledDays
    )

    @Test
    fun nextScheduledDate_monWedFri_afterMonday_isWednesday() {
        assertEquals(
            monday.plusDays(2),
            habit(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
                .nextScheduledDate(monday)
        )
    }

    @Test
    fun nextScheduledDate_monWedFri_afterFriday_wrapsToNextMonday() {
        val friday = monday.plusDays(4)
        assertEquals(
            monday.plusWeeks(1),
            habit(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
                .nextScheduledDate(friday)
        )
    }

    @Test
    fun nextScheduledDate_dailyHabit_isTheFollowingDay() {
        assertEquals(
            monday.plusDays(1),
            habit(DayOfWeek.entries.toSet()).nextScheduledDate(monday)
        )
    }

    @Test
    fun nextScheduledDate_singleWeekdaySchedule_isSevenDaysLater() {
        assertEquals(
            monday.plusWeeks(1),
            habit(setOf(DayOfWeek.MONDAY)).nextScheduledDate(monday)
        )
    }

    @Test
    fun nextScheduledDate_emptySchedule_isNullAndTerminates() {
        assertNull(habit(emptySet()).nextScheduledDate(monday))
    }

    @Test
    fun nextScheduledDate_referenceDayScheduled_stillReturnsTheNextOccurrence() {
        // "after" itself is scheduled, but the contract is strictly later
        val wednesdayHabit = habit(setOf(DayOfWeek.WEDNESDAY))
        val fromWednesday = monday.plusDays(2)
        assertEquals(
            fromWednesday.plusWeeks(1),
            wednesdayHabit.nextScheduledDate(fromWednesday)
        )
    }
}
