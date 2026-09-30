package com.markel.flowstate.core.domain.usecase.tasks

import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskRepository
import javax.inject.Inject

class ToggleTaskUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(task: Task): Task {
        val newIsDone = !task.isDone
        val newCompletedAt = if (newIsDone) System.currentTimeMillis() else null

        // If a task is completed delete all the reminders (from the subtasks too)
        val updatedReminderTime = if (newIsDone) null else task.reminderTime
        val updatedSubTasks = if (newIsDone) {
            task.subTasks.map { it.copy(reminderTime = null) }
        } else {
            task.subTasks
        }

        val updatedTask = task.copy(
            isDone = newIsDone,
            completedAt = newCompletedAt,
            reminderTime = updatedReminderTime,
            subTasks = updatedSubTasks
        )
        repository.upsertTask(updatedTask)
        return updatedTask
    }
}