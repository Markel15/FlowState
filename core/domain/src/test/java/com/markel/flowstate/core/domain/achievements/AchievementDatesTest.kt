package com.markel.flowstate.core.domain.achievements

import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.HabitEntryFlat
import com.markel.flowstate.core.domain.HabitStreakCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tier dates are derived, never stored: they must always agree with the
 * counters (same event list) and disappear when the events behind them do.
 */
class AchievementDatesTest {

    private fun progress(
        id: AchievementId,
        inputs: AchievementInputs,
        dates: AchievementDates
    ): AchievementProgress =
        AchievementEvaluator.evaluate(inputs, dates).first { it.definition.id == id }

    private fun dates(from: LocalDate, count: Int): List<LocalDate> =
        (0 until count).map { from.plusDays(it.toLong()) }

    @Test
    fun `evaluating without dates leaves every tier undated`() {
        val p = progress(AchievementId.TASK_TOTAL, AchievementInputs(totalTaskCompletions = 30), AchievementDates())
        // Counters keep working without dates; nothing is invented.
        assertEquals(1, p.tierReached)
        assertEquals(3, p.tierDates.size)
        assertTrue(p.tierDates.all { it == null })
    }

    @Test
    fun `every tier gets a slot and only crossed tiers are dated`() {
        val start = LocalDate.of(2026, 1, 1)
        val completionDates = dates(start, 30)
        val p = progress(
            AchievementId.TASK_TOTAL,
            AchievementInputs(totalTaskCompletions = 30),
            AchievementDates(taskCompletions = completionDates)
        )

        assertEquals(3, p.tierDates.size)
        assertEquals(1, p.tierReached)
        assertEquals(completionDates[24], p.tierDates[0]) // 25th task
        assertNull(p.tierDates[1])
        assertNull(p.tierDates[2])
    }

    @Test
    fun `maxed achievement exposes the date of its last tier`() {
        val start = LocalDate.of(2026, 1, 1)
        val completionDates = dates(start, 500)
        val p = progress(
            AchievementId.TASK_TOTAL,
            AchievementInputs(totalTaskCompletions = 500),
            AchievementDates(taskCompletions = completionDates)
        )

        assertTrue(p.maxed)
        assertEquals(completionDates[499], p.completedAt)
    }

    @Test
    fun `removing the crossing event removes the date`() {
        val start = LocalDate.of(2026, 1, 1)
        val twoTiers = dates(start, 100)
        val before = progress(
            AchievementId.TASK_TOTAL,
            AchievementInputs(totalTaskCompletions = 100),
            AchievementDates(taskCompletions = twoTiers)
        )
        assertEquals(twoTiers[99], before.tierDates[1])

        // One task deleted: the second tier is no longer reached and its date
        // cannot survive, exactly like the counter.
        val after = progress(
            AchievementId.TASK_TOTAL,
            AchievementInputs(totalTaskCompletions = 99),
            AchievementDates(taskCompletions = twoTiers.dropLast(1))
        )
        assertEquals(1, after.tierReached)
        assertNull(after.tierDates[1])
    }

    @Test
    fun `sunday tier lands on the first sunday whose count gets there`() {
        val quiet = LocalDate.of(2026, 2, 1)
        val busy = LocalDate.of(2026, 2, 8)
        val p = progress(
            AchievementId.SUNDAY_FUN_DAY,
            AchievementInputs(bestSundayTasks = 7),
            AchievementDates(
                sundayCounts = listOf(DayCount(quiet, 3), DayCount(busy, 7))
            )
        )

        assertEquals(2, p.tierReached) // 3 and 5 reached
        assertEquals(quiet, p.tierDates[0])
        assertEquals(busy, p.tierDates[1])
        assertNull(p.tierDates[2])
    }

    @Test
    fun `streak date counts scheduled occurrences`() {
        val friday = LocalDate.of(2026, 8, 28)
        val monFri = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        val run = StreakRun(end = friday, length = 5, scheduledDays = monFri)

        // 5 occurrences: 14, 17, 21, 24 and 28. Reaching the 1st is the start
        // of the run, reaching the 5th is its last day.
        assertEquals(LocalDate.of(2026, 8, 14), run.dateReaching(1))
        assertEquals(LocalDate.of(2026, 8, 21), run.dateReaching(3))
        assertEquals(friday, run.dateReaching(5))
        assertNull(run.dateReaching(6))
    }

    @Test
    fun `a run shorter than the first tier leaves it undated`() {
        val monFri = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        val short = StreakRun(LocalDate.of(2026, 3, 6), 20, monFri)
        val p = progress(
            AchievementId.STREAK,
            AchievementInputs(bestStreak = 20),
            AchievementDates(streakRuns = listOf(short))
        )

        assertEquals(0, p.tierReached) // tier 1 asks for 21 occurrences
        assertNull(p.tierDates[0])
    }

    @Test
    fun `streak tier is dated when the run gets long enough`() {
        val start = LocalDate.of(2026, 1, 5) // a Monday
        val daily = DayOfWeek.entries.toSet()
        val run = StreakRun(end = start.plusDays(29), length = 30, scheduledDays = daily)
        val p = progress(
            AchievementId.STREAK,
            AchievementInputs(bestStreak = 30),
            AchievementDates(streakRuns = listOf(run))
        )

        assertEquals(1, p.tierReached) // 21 reached, 100 not
        // 21st consecutive day of the run.
        assertEquals(start.plusDays(20), p.tierDates[0])
        assertNull(p.tierDates[1])
    }

    @Test
    fun `dated tiers and counters never disagree`() {
        val start = LocalDate.of(2025, 1, 1)
        val habitDates = dates(start, 120)
        val metricsDates = AchievementDates(habitCompletions = habitDates)
        val p = progress(
            AchievementId.UNSTOPPABLE,
            AchievementInputs(totalHabitCompletions = habitDates.size),
            metricsDates
        )

        // tierReached is computed from the counter, and the counter is the list
        // size: if the Nth date existed without the counter, this would fail.
        assertEquals(habitDates.size, p.current)
        assertEquals(1, p.tierReached) // 100 reached, 1000 not
        assertEquals(habitDates[99], p.tierDates[0])
    }

    @Test
    fun `habit metrics keep the streak semantics of the calculator`() {
        val today = LocalDate.of(2026, 8, 24) // a Monday
        val habit = Habit(id = 1, name = "h1", createdAt = today.minusDays(10))
        val entries = listOf(today.minusDays(2), today.minusDays(1), today)
            .map { HabitEntryFlat(1, it.toEpochDay()) }

        val metrics = computeHabitAchievementMetrics(listOf(habit), entries, emptyList(), today)
        val expected = HabitStreakCalculator.best(
            entries.map { LocalDate.ofEpochDay(it.epochDay) },
            habit.scheduledDays
        )

        assertEquals(expected, metrics.bestStreak)
        assertEquals(3, metrics.bestStreak)
        // One run of 3, ending today: the run itself is exposed for dating.
        assertEquals(1, metrics.streakRuns.size)
        assertEquals(today, metrics.streakRuns.single().end)
        assertEquals(3, metrics.streakRuns.single().length)
    }

    @Test
    fun `habit completion dates feed the counters`() {
        val today = LocalDate.of(2026, 8, 24)
        val habit = Habit(id = 1, name = "h1", createdAt = today.minusDays(10))
        val entries = listOf(today.minusDays(2), today.minusDays(1))
            .map { HabitEntryFlat(1, it.toEpochDay()) }

        val metrics = computeHabitAchievementMetrics(listOf(habit), entries, emptyList(), today)

        assertEquals(2, metrics.totalCompletions)
        assertEquals(listOf(today.minusDays(2), today.minusDays(1)), metrics.completionDates)
    }
}
