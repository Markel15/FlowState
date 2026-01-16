package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.local.SubTaskEntity
import com.markel.flowstate.core.data.local.TaskDao
import com.markel.flowstate.core.data.local.TaskEntity
import com.markel.flowstate.core.data.local.TaskWithSubTasks
import com.markel.flowstate.core.domain.SubTask
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Esta es la implementación REAL del repositorio.
 * Vive en :core:data porque CONOCE la capa de datos (el DAO).
 * Su trabajo es:
 * 1. Recibir órdenes del ViewModel (a través de la interfaz TaskRepository).
 * 2. Hablar con el DAO (TaskDao).
 * 3. Mapear (convertir) los modelos de datos (TaskEntity) a modelos de dominio (Task) y viceversa.
 */
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao // Recibirá el DAO por inyección de dependencias
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
        // Descomponemos el objeto de Dominio en Entidad Padre + Entidades Hijas
        val taskEntity = task.toEntity()
        val subTaskEntities = task.subTasks.map { it.toEntity(taskId = task.id) }

        // Delegamos la transacción compleja al DAO
        dao.upsertTaskWithSubTasks(taskEntity, subTaskEntities)
    }

    override suspend fun deleteTask(task: Task) {
        // Al borrar el padre, el CASCADE de la DB borrará los hijos automáticamente
        dao.deleteTaskEntity(task.toEntity())
    }

    override suspend fun updateTasksOrder(tasks: List<Task>) {
        val entities = tasks.map { it.toEntity() }
        dao.updateTasks(entities)
    }

    // --- FUNCIONES DE MAPEO ---
    // Estas funciones convierten entre el modelo de DB y el modelo de Dominio

    private fun TaskWithSubTasks.toDomain(): Task {
        return Task(
            id = this.task.id,
            title = this.task.title,
            description = this.task.description,
            isDone = this.task.isDone,
            position = this.task.position,
            subTasks = this.subTasks.map { it.toDomain() }
        )
    }

    private fun SubTaskEntity.toDomain(): SubTask {
        return SubTask(
            id = this.id,
            title = this.title,
            isDone = this.isDone
        )
    }

    private fun Task.toEntity(): TaskEntity {
        return TaskEntity(
            id = this.id,
            title = this.title,
            description = this.description,
            isDone = this.isDone,
            position = this.position
        )
    }

    private fun SubTask.toEntity(taskId: Int): SubTaskEntity {
        return SubTaskEntity(
            id = this.id,
            taskId = taskId,
            title = this.title,
            isDone = this.isDone
        )
    }
}