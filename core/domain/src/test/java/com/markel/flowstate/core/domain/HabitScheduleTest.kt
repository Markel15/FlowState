package com.markel.flowstate.core.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitScheduleTest {

    private val monday = LocalDate.of(2026, 8, 17)

    @Test
    fun isScheduledFor_returnsTrueForSelectedWeekday() {
        val habit = Habit(
            name = "Exercise",
            scheduledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        )

        assertTrue(habit.isScheduledFor(monday))
    }

    @Test
    fun isScheduledFor_returnsFalseForUnselectedWeekday() {
        val habit = Habit(
            name = "Exercise",
            scheduledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        )

        assertFalse(habit.isScheduledFor(monday.plusDays(1)))
    }
}
