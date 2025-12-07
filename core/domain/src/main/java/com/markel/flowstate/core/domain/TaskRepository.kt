package com.markel.flowstate.core.domain

import kotlinx.coroutines.flow.Flow

/**
 * Define las operaciones de datos para las Tareas.
 * Esta interfaz vive en :core:domain y no sabe nada de Room o APIs.
 * Es el contrato que los ViewModels usarán.
 */
interface TaskRepository {

    /**
     * Obtiene un Flow de todas las tareas.
     * La UI se actualizará automáticamente cuando esto cambie.
     */
    fun getTasks(): Flow<List<Task>>

    /**
     * Obtiene una única tarea por su ID.
     * Devuelve null si no la encuentra.
     */
    suspend fun getTaskById(id: Int): Task?

    /**
     * Inserta o actualiza una tarea.
     */
    suspend fun upsertTask(task: Task)

    /**
     * Borra una tarea.
     */
    suspend fun deleteTask(task: Task)
}