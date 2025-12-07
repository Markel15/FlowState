package com.markel.flowstate.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object)
 * Esta interfaz define CÓMO hablamos con la base de datos.
 * Room escribirá el código por nosotros gracias a estas anotaciones.
 */
@Dao
interface TaskDao {

    // "Upsert" = "Update" (actualizar) o "Insert" (insertar).
    // Si la tarea ya existe, la actualiza. Si es nueva, la crea.
    @Upsert
    suspend fun upsertTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    // Pide todas las tareas ordenadas (las no hechas primero)
    // y las devuelve como un "Flow".
    // "Flow" significa que si algo cambia en la tabla,
    // nuestra UI se actualizará automáticamente.
    @Query("SELECT * FROM tasks ORDER BY isDone ASC, id DESC")
    fun getTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskEntity?
}