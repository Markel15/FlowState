package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.local.SubTaskEntity
import com.markel.flowstate.core.data.local.TaskDao
import com.markel.flowstate.core.data.local.TaskEntity
import com.markel.flowstate.core.data.local.TaskWithSubTasks
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.SubTask
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * This is the REAL implementation of the repository.
 * It lives in :core:data because it KNOWS the data layer (the DAO).
 * Its job is to:
 * 1. Receive orders from the ViewModel (through the TaskRepository interface).
 * 2. Talk to the DAO (TaskDao).
 * 3. Map (convert) data models (TaskEntity) to domain models (Task) and vice versa.
 */
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao // It will receive the DAO through dependency injection
) : TaskRepository {

    override fun getTasks(): Flow<List<Task>> {
        return dao.getTasks().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getTaskById(id: Int): Task? {
        return dao.getTaskById(id)?.toDomain()
    }

    override suspend fun upsertTask(task: Task) {
        // We break down the Domain object into Parent Entity + Child Entities
        val taskEntity = task.toEntity()
        val subTaskEntities = task.subTasks.map { it.toEntity(taskId = task.id) }

        // We delegate the complex transaction to the DAO
        dao.upsertTaskWithSubTasks(taskEntity, subTaskEntities)
    }

    override suspend fun deleteTask(task: Task) {
        // When deleting the parent, the DB's CASCADE will automatically delete the children
        dao.deleteTaskEntity(task.toEntity())
    }

    override suspend fun updateTasksOrder(tasks: List<Task>) {
        val entities = tasks.map { it.toEntity() }
        dao.updateTasks(entities)
    }

    // --- MAPPING FUNCTIONS ---
    // These functions convert between the DB model and the Domain model

    private fun TaskWithSubTasks.toDomain(): Task {
        val priorityEnum = Priority.entries.getOrElse(this.task.priority) { Priority.NOTHING }

        return Task(
            id = this.task.id,
            title = this.task.title,
            description = this.task.description,
            isDone = this.task.isDone,
            position = this.task.position,
            priority = priorityEnum,
            dueDate = this.task.dueDate,
            subTasks = this.subTasks.map { it.toDomain() }
        )
    }

    private fun SubTaskEntity.toDomain(): SubTask {
        return SubTask(
            id = this.id,
            title = this.title,
            description = this.description,
            isDone = this.isDone,
            priority = Priority.entries.getOrElse(this.priority) { Priority.NOTHING },
            dueDate = this.dueDate,
            position = this.position
        )
    }

    private fun Task.toEntity(): TaskEntity {
        return TaskEntity(
            id = this.id,
            title = this.title,
            description = this.description,
            isDone = this.isDone,
            position = this.position,
            priority = this.priority.ordinal,
            dueDate = this.dueDate
        )
    }

    private fun SubTask.toEntity(taskId: Int): SubTaskEntity {
        return SubTaskEntity(
            id = this.id,
            taskId = taskId,
            title = this.title,
            description = this.description,
            isDone = this.isDone,
            priority = this.priority.ordinal,
            dueDate = this.dueDate,
            position = this.position
        )
    }
}