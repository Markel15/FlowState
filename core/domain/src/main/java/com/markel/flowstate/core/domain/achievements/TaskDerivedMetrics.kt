package com.markel.flowstate.core.domain.achievements

import com.markel.flowstate.core.domain.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Task-derived raw values used by the achievement catalog. */
data class TaskDerivedMetrics(
    /** Tasks completed between midnight and 5 AM (night owl sessions). */
    val nightOwlTasks: Int,
    /** Most tasks completed on a single Sunday. */
    val bestSundayTasks: Int
)

private const val NIGHT_OWL_END_HOUR = 5

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
    var nightOwl = 0

    tasks.forEach { task ->
        val completedAt = task.completedAt ?: return@forEach
        if (!task.isDone) return@forEach
        val zoned = Instant.ofEpochMilli(completedAt).atZone(zone)
        if (zoned.toLocalTime() < LocalTime.of(NIGHT_OWL_END_HOUR, 0)) {
            nightOwl++
        }
        completionDates += zoned.toLocalDate()
    }

    val bestSunday = completionDates
        .filter { it.dayOfWeek == DayOfWeek.SUNDAY }
        .groupingBy { it }
        .eachCount()
        .values
        .maxOrNull() ?: 0

    return TaskDerivedMetrics(
        nightOwlTasks = nightOwl,
        bestSundayTasks = bestSunday
    )
}
