package com.markel.flowstate.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * This is the database "table".
 * It represents a single Task.
 */
@Entity(tableName = "tasks") // This is what the table will be called in the database
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String = "",
    val isDone: Boolean = false,
    val position: Int = 0,
    val priority: Int = 0
)