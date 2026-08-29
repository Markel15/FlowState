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
    val bestSundayTasks: Int,
    /** Every task currently marked as done. */
    val totalDone: Int,
    /** Tasks wrapped up before 7 AM (early bird sessions). */
    val earlyBirdTasks: Int,
    /** Tasks completed on or before their due date. */
    val onTimeTasks: Int,
    /** Days when at least 3 tasks were completed before noon. */
    val productiveMornings: Int
)

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
    val morningCounts = mutableMapOf<LocalDate, Int>()
    var nightOwl = 0
    var earlyBird = 0
    var onTime = 0

    tasks.forEach { task ->
        val completedAt = task.completedAt ?: return@forEach
        if (!task.isDone) return@forEach
        val zoned = Instant.ofEpochMilli(completedAt).atZone(zone)
        val time = zoned.toLocalTime()
        if (time < LocalTime.of(NIGHT_OWL_END_HOUR, 0)) {
            nightOwl++
        }
        if (time < LocalTime.of(EARLY_BIRD_END_HOUR, 0)) {
            earlyBird++
        }
        if (time < LocalTime.NOON) {
            val date = zoned.toLocalDate()
            morningCounts[date] = (morningCounts[date] ?: 0) + 1
        }
        val dueDate = task.dueDate
        if (dueDate != null) {
            val dueDay = Instant.ofEpochMilli(dueDate).atZone(zone).toLocalDate()
            if (!zoned.toLocalDate().isAfter(dueDay)) {
                onTime++
            }
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
        bestSundayTasks = bestSunday,
        totalDone = tasks.count { it.isDone },
        earlyBirdTasks = earlyBird,
        onTimeTasks = onTime,
        productiveMornings = morningCounts.values.count { it >= PRODUCTIVE_MORNING_MIN_TASKS }
    )
}
