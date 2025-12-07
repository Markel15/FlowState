package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.local.TaskDao
import com.markel.flowstate.core.data.local.TaskEntity
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
        // 1. Llama al DAO para obtener el Flow de TaskEntity
        val tasksFromDb: Flow<List<TaskEntity>> = dao.getTasks()

        // 2. Mapea el Flow<List<TaskEntity>> a Flow<List<Task>>
        return tasksFromDb.map { entityList ->
            entityList.map { entity ->
                // Este es el mapeo de Entity -> Domain
                entity.toDomain()
            }
        }
    }

    override suspend fun getTaskById(id: Int): Task? {
        val entity = dao.getTaskById(id)
        // Mapeo de Entity? -> Domain?
        return entity?.toDomain()
    }

    override suspend fun upsertTask(task: Task) {
        // Mapeo de Domain -> Entity
        dao.upsertTask(task.toEntity())
    }

    override suspend fun deleteTask(task: Task) {
        // Mapeo de Domain -> Entity
        dao.deleteTask(task.toEntity())
    }

    // --- FUNCIONES DE MAPEO ---
    // Estas funciones convierten entre el modelo de DB y el modelo de Dominio

    private fun TaskEntity.toDomain(): Task {
        return Task(
            id = this.id,
            title = this.title,
            isDone = this.isDone
        )
    }

    private fun Task.toEntity(): TaskEntity {
        return TaskEntity(
            id = this.id,
            title = this.title,
            isDone = this.isDone
        )
    }
}