package com.markel.flowstate.core.domain.achievements

import com.markel.flowstate.core.domain.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A date and how many times the event happened on it. */
data class DayCount(val date: LocalDate, val count: Int)

/**
 * Task-derived raw values used by the achievement catalog.
 *
 * Same idea as [HabitAchievementMetrics]: keep the dates that produced each
 * counter so the UI can date every tier, and derive the counters from them.
 * `totalDone` is the only exception — it mirrors "tasks marked as done", which
 * is not exactly the same as "tasks with a completion timestamp" — so it is
 * tracked separately and never used to date a tier.
 */
data class TaskDerivedMetrics(
    /** One entry per completed task with a timestamp, ascending. */
    val completionDates: List<LocalDate>,
    /** Tasks completed between midnight and 5 AM. */
    val nightOwlDates: List<LocalDate>,
    /** Tasks wrapped up before 7 AM. */
    val earlyBirdDates: List<LocalDate>,
    /** Days when at least 3 tasks were completed before noon. */
    val productiveMorningDates: List<LocalDate>,
    /** Tasks completed on or before their due date. */
    val onTimeDates: List<LocalDate>,
    /** Tasks completed on a Sunday, grouped by date, ascending. */
    val sundayCounts: List<DayCount>,
    /** Every task currently marked as done, with or without a timestamp. */
    val doneTasks: Int
) {
    val totalDone: Int get() = doneTasks
    val nightOwlTasks: Int get() = nightOwlDates.size
    val earlyBirdTasks: Int get() = earlyBirdDates.size
    val productiveMornings: Int get() = productiveMorningDates.size
    val onTimeTasks: Int get() = onTimeDates.size
    /** Most tasks completed on a single Sunday. */
    val bestSundayTasks: Int get() = sundayCounts.maxOfOrNull { it.count } ?: 0

    companion object {
        val EMPTY = TaskDerivedMetrics(
            completionDates = emptyList(),
            nightOwlDates = emptyList(),
            earlyBirdDates = emptyList(),
            productiveMorningDates = emptyList(),
            onTimeDates = emptyList(),
            sundayCounts = emptyList(),
            doneTasks = 0
        )
    }
}

private const val NIGHT_OWL_END_HOUR = 5
private const val EARLY_BIRD_END_HOUR = 7
private const val PRODUCTIVE_MORNING_MIN_TASKS = 3

/**
 * Computes [TaskDerivedMetrics] from the completion timestamps.
 *
 * Pure apart from the injectable [zone] so unit tests can pin a timezone.
 * Timestamps live in `completedAt`, so these values mirror the current
 * task list: deleting a completed task rolls its related milestones back.
 */
fun computeTaskDerivedMetrics(
    tasks: List<Task>,
    zone: ZoneId = ZoneId.systemDefault()
): TaskDerivedMetrics {
    val completionDates = mutableListOf<LocalDate>()
    val nightOwlDates = mutableListOf<LocalDate>()
    val earlyBirdDates = mutableListOf<LocalDate>()
    val onTimeDates = mutableListOf<LocalDate>()
    val morningCounts = mutableMapOf<LocalDate, Int>()
    val sundayCounts = mutableMapOf<LocalDate, Int>()

    tasks.forEach { task ->
        val completedAt = task.completedAt ?: return@forEach
        if (!task.isDone) return@forEach
        val zoned = Instant.ofEpochMilli(completedAt).atZone(zone)
        val date = zoned.toLocalDate()
        val time = zoned.toLocalTime()

        completionDates += date
        if (date.dayOfWeek == DayOfWeek.SUNDAY) {
            sundayCounts[date] = (sundayCounts[date] ?: 0) + 1
        }
        if (time < LocalTime.of(NIGHT_OWL_END_HOUR, 0)) {
            nightOwlDates += date
        }
        if (time < LocalTime.of(EARLY_BIRD_END_HOUR, 0)) {
            earlyBirdDates += date
        }
        if (time < LocalTime.NOON) {
            morningCounts[date] = (morningCounts[date] ?: 0) + 1
        }
        val dueDate = task.dueDate
        if (dueDate != null) {
            val dueDay = Instant.ofEpochMilli(dueDate).atZone(zone).toLocalDate()
            if (!date.isAfter(dueDay)) {
                onTimeDates += date
            }
        }
    }

    return TaskDerivedMetrics(
        completionDates = completionDates.sorted(),
        nightOwlDates = nightOwlDates.sorted(),
        earlyBirdDates = earlyBirdDates.sorted(),
        productiveMorningDates = morningCounts
            .filterValues { it >= PRODUCTIVE_MORNING_MIN_TASKS }
            .keys
            .sorted(),
        onTimeDates = onTimeDates.sorted(),
        sundayCounts = sundayCounts
            .map { (date, count) -> DayCount(date, count) }
            .sortedBy { it.date },
        doneTasks = tasks.count { it.isDone }
    )
}
