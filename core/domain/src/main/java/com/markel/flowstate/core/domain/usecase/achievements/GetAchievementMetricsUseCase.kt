package com.markel.flowstate.core.domain.usecase.achievements

import com.markel.flowstate.core.domain.HabitRepository
import com.markel.flowstate.core.domain.TaskRepository
import com.markel.flowstate.core.domain.achievements.HabitAchievementMetrics
import com.markel.flowstate.core.domain.achievements.TaskDerivedMetrics
import com.markel.flowstate.core.domain.achievements.computeHabitAchievementMetrics
import com.markel.flowstate.core.domain.achievements.computeTaskDerivedMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Habit + task derived metrics feeding the achievement catalog. */
data class AchievementMetrics(
    val habit: HabitAchievementMetrics,
    val tasks: TaskDerivedMetrics
)

/**
 * Streams the derived achievement metrics from the existing Room data —
 * no extra persistence required, so everything travels with the regular
 * JSON backup.
 */
class GetAchievementMetricsUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val taskRepository: TaskRepository
) {
    operator fun invoke(): Flow<AchievementMetrics> = combine(
        habitRepository.getHabits(),
        habitRepository.getAllEntries(),
        habitRepository.getAllNumericEntries(),
        taskRepository.getTasks()
    ) { habits, booleanEntries, numericEntries, tasks ->
        AchievementMetrics(
            habit = computeHabitAchievementMetrics(habits, booleanEntries, numericEntries),
            tasks = computeTaskDerivedMetrics(tasks)
        )
    }
}
