package com.markel.flowstate.feature.flow.tasks.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DateSelectorTest {

    private fun LocalDate.toUtcMillis(): Long =
        atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    @Test
    fun isDateOverdue_becomesTrueWhenTodayAdvancesPastMidnight() {
        val day1 = LocalDate.of(2026, 11, 13)
        val day2 = day1.plusDays(1)
        val dueDate = day1.toUtcMillis()

        // On day 1, a task due on day 1 is not overdue
        assertFalse(isDateOverdue(dueDate, today = day1))

        // After midnight (day 2), the same dueDate becomes overdue
        assertTrue(isDateOverdue(dueDate, today = day2))
    }
}
