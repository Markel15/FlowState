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
 * This interface defines HOW we talk to the database.
 * Room will write the code for us thanks to these annotations.
 */
@Dao
interface TaskDao {

    // "Upsert" = "Update" or "Insert".
    // If the task already exists, it updates it. If it's new, it creates it.
    @Upsert
    suspend fun upsertTaskEntity(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTasks(subTasks: List<SubTaskEntity>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubTasksByTaskId(taskId: Int)

    @Delete
    suspend fun deleteTaskEntity(task: TaskEntity)

    /**
     * This function handles the complete logic of saving a task with its subtasks
     * Being @Transaction, if something fails, nothing is saved (total integrity)
     */
    @Transaction
    suspend fun upsertTaskWithSubTasks(task: TaskEntity, subTasks: List<SubTaskEntity>) {
        // 1. We save/update the parent.
        val rowId = upsertTaskEntity(task)

        // This new logic avoids errors when upsertTaskEntity returns unexpected FK values (normally when nothing has changed in the main task)
        val taskId = if (task.id == 0) rowId.toInt() else task.id

        // 2. We delete the old subtasks to avoid duplicates or "ghosts"
        deleteSubTasksByTaskId(taskId)

        // 3. We assign the task ID to the subtasks and insert them
        val subTasksWithId = subTasks.map { it.copy(taskId = taskId) }
        insertSubTasks(subTasksWithId)
    }

    // Requests all ordered tasks
    // "Flow" means that if something changes in the table, the UI will automatically update.
    @Transaction // Necessary because Room makes 2 internal queries since the method returns a class that contains a field with @Relation
    @Query("SELECT * FROM tasks ORDER BY position ASC")
    fun getTasks(): Flow<List<TaskWithSubTasks>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskWithSubTasks?

    @Update
    suspend fun updateTasks(tasks: List<TaskEntity>)
}