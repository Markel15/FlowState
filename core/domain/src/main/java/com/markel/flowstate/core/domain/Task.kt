package com.markel.flowstate.core.domain

import java.util.UUID

/**
 * This is the "clean" Task model.
 * It is the representation of a Task for the UI and business logic (ViewModels).
 * It knows nothing about the database (@Entity, @PrimaryKey, etc.).
 */

enum class Priority {
    NOTHING, LOW, MEDIUM, HIGH
}
data class SubTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false
)
data class Task(
    val id: Int = 0,
    val title: String,
    val description: String = "",
    val isDone: Boolean,
    val position: Int = 0,
    val priority: Priority = Priority.NOTHING,
    val dueDate: Long? = null,
    val subTasks: List<SubTask> = emptyList()
    // Other domain fields would go here, such as:
    // priority: Priority,
    // dueDate: LocalDate?
)