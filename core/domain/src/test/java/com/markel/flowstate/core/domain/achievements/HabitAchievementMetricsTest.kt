package com.markel.flowstate.core.domain.achievements

import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.HabitEntryFlat
import com.markel.flowstate.core.domain.HabitNumericEntry
import com.markel.flowstate.core.domain.HabitType
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitAchievementMetricsTest {

    private val today = LocalDate.of(2026, 8, 24) // a Monday

    private fun boolHabit(
        id: Int,
        createdAt: LocalDate,
        scheduled: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    ) = Habit(
        id = id,
        name = "h$id",
        createdAt = createdAt,
        habitType = HabitType.BOOLEAN,
        scheduledDays = scheduled
    )

    private fun entry(habitId: Int, date: LocalDate) =
        HabitEntryFlat(habitId, date.toEpochDay())

    @Test
    fun `no habits means all zeros`() {
        val m = computeHabitAchievementMetrics(emptyList(), emptyList(), emptyList(), today)
        assertEquals(0, m.totalCompletions)
        assertEquals(0, m.bestStreak)
        assertEquals(0, m.perfectDays)
        assertEquals(0, m.mondayPerfectDays)
        assertEquals(0, m.perfectWeeks)
    }

    @Test
    fun `numeric completions only count when the target is reached`() {
        val habit = Habit(
            id = 1,
            name = "run",
            createdAt = today.minusDays(3),
            habitType = HabitType.NUMERIC,
            targetValue = 5f
        )
        val numeric = listOf(
            HabitNumericEntry(1, today.minusDays(2), 6f), // qualifies
            HabitNumericEntry(1, today.minusDays(1), 2f), // below target
            HabitNumericEntry(1, today, 5f)               // exactly target
        )
        val m = computeHabitAchievementMetrics(listOf(habit), emptyList(), numeric, today)
        assertEquals(2, m.totalCompletions)
    }

    @Test
    fun `best streak is the maximum across habits`() {
        val h1 = boolHabit(1, today.minusDays(10))
        val h2 = boolHabit(2, today.minusDays(10))
        val entries = listOf(
            entry(1, today.minusDays(2)),
            entry(1, today.minusDays(1)),
            entry(1, today),
            entry(2, today.minusDays(1)),
            entry(2, today)
        )
        val m = computeHabitAchievementMetrics(listOf(h1, h2), entries, emptyList(), today)
        assertEquals(3, m.bestStreak)
        assertEquals(5, m.totalCompletions)
    }

    @Test
    fun `perfect day requires every scheduled habit that already existed`() {
        val older = boolHabit(1, today.minusDays(2))
        val newer = boolHabit(2, today)
        val entries = listOf(
            entry(1, today.minusDays(2)),
            entry(1, today.minusDays(1)),
            entry(1, today),
            entry(2, today)
        )
        val m = computeHabitAchievementMetrics(listOf(older, newer), entries, emptyList(), today)
        assertEquals(3, m.perfectDays)
    }

    @Test
    fun `monday perfect days only count mondays`() {
        val habit = boolHabit(1, today.minusDays(3)) // created Friday
        val entries = listOf(
            entry(1, today.minusDays(3)), // Friday
            entry(1, today.minusDays(2)), // Saturday
            entry(1, today.minusDays(1)), // Sunday
            // Monday (today) NOT completed
        )
        val m = computeHabitAchievementMetrics(listOf(habit), entries, emptyList(), today)
        assertEquals(3, m.perfectDays)
        assertEquals(0, m.mondayPerfectDays)
    }

    @Test
    fun `days with nothing scheduled do not count as perfect`() {
        val weekendOnly = boolHabit(
            1,
            today.minusDays(7),
            scheduled = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        )
        val m = computeHabitAchievementMetrics(listOf(weekendOnly), emptyList(), emptyList(), today)
        assertEquals(0, m.perfectDays)
    }

    @Test
    fun `perfect week requires the whole completed week`() {
        val habit = boolHabit(1, today.minusWeeks(2)) // created 2 weeks ago (Monday)
        // Last week (Mon Aug 17 - Sun Aug 23 2026) fully completed except Sunday
        val goodDates = (0L..5L).map { today.minusWeeks(1).plusDays(it) }
        val previousWeek = (0L..6L).map { today.minusWeeks(2).plusDays(it) }
        val entries = (goodDates + previousWeek).map { entry(1, it) }
        // today is Monday of the current (unfinished) week, not completed

        val m = computeHabitAchievementMetrics(listOf(habit), entries, emptyList(), today)
        // Previous week counts; last week misses Sunday; current week is unfinished
        assertEquals(1, m.perfectWeeks)
    }

    @Test
    fun `current unfinished week never counts`() {
        val habit = boolHabit(1, today)
        // today (Monday) completed, but the week just started
        val m = computeHabitAchievementMetrics(
            listOf(habit),
            listOf(entry(1, today)),
            emptyList(),
            today
        )
        assertEquals(0, m.perfectWeeks)
    }

    @Test
    fun `comeback needs three missed occurrences in a row`() {
        val habit = boolHabit(1, createdAt = today.minusDays(6))
        // Daily habit silent Aug 18-22 (5 missed), completed yesterday Aug 23
        val entries = listOf(entry(1, today.minusDays(1)))
        val m = computeHabitAchievementMetrics(listOf(habit), entries, emptyList(), today)
        assertEquals(1, m.comebacks)
    }

    @Test
    fun `two missed days are not enough for a comeback`() {
        val habit = boolHabit(1, createdAt = today.minusDays(4))
        // Missed Aug 20-21 (2), completed Aug 22, missed Aug 23, today pending
        val entries = listOf(entry(1, today.minusDays(2)))
        val m = computeHabitAchievementMetrics(listOf(habit), entries, emptyList(), today)
        assertEquals(0, m.comebacks)
    }

    @Test
    fun `a pending today is never counted as missed`() {
        val habit = boolHabit(1, createdAt = today.minusDays(3))
        // Three past days missed but no completion after them, today still open
        val m = computeHabitAchievementMetrics(listOf(habit), emptyList(), emptyList(), today)
        assertEquals(0, m.comebacks)
    }

    @Test
    fun `scheduled days shape the missed run`() {
        // Created Sunday Aug 2: the Sundays of Aug 2, 9 and 16 were missed,
        // Aug 23 (yesterday, also a Sunday) completed -> one comeback.
        val sundaysOnly = boolHabit(
            1,
            createdAt = today.minusDays(22),
            scheduled = setOf(DayOfWeek.SUNDAY)
        )
        val entries = listOf(entry(1, today.minusDays(1)))
        val m = computeHabitAchievementMetrics(listOf(sundaysOnly), entries, emptyList(), today)
        assertEquals(1, m.comebacks)
    }
}
