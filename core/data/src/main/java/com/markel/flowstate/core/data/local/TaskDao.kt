package com.markel.flowstate.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
    suspend fun upsertTaskEntity(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTasks(subTasks: List<SubTaskEntity>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubTasksByTaskId(taskId: Int)

    @Delete
    suspend fun deleteTaskEntity(task: TaskEntity)

    /**
     * Esta función maneja la lógica completa de guardar una tarea con sus subtareas
     * Al ser @Transaction, si algo falla, no se guarda nada (integridad total)
     */
    @Transaction
    suspend fun upsertTaskWithSubTasks(task: TaskEntity, subTasks: List<SubTaskEntity>) {
        // 1. Guardamos/Actualizamos el padre.
        val rowId = upsertTaskEntity(task)

        // Esta nueva lógica evita errores cuando upsertTaskEntity devuelve valores inesperados de FK (normalmente cuando no ha cambiado nada de la tarea principal)
        val taskId = if (task.id == 0) rowId.toInt() else task.id

        // 2. Borramos las subtareas antiguas para evitar duplicados o "fantasmas"
        deleteSubTasksByTaskId(taskId)

        // 3. Asignamos el ID de la tarea a las subtareas y las insertamos
        val subTasksWithId = subTasks.map { it.copy(taskId = taskId) }
        insertSubTasks(subTasksWithId)
    }

    // Pide todas las tareas ordenadas
    // "Flow" significa que si algo cambia en la tabla, la UI se actualizará automáticamente.
    @Transaction // Necesario porque Room hace 2 consultas internamente ya que el método devuelve una clase que contiene un campo con @Relation
    @Query("SELECT * FROM tasks ORDER BY position ASC")
    fun getTasks(): Flow<List<TaskWithSubTasks>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskWithSubTasks?

    @Update
    suspend fun updateTasks(tasks: List<TaskEntity>)
}