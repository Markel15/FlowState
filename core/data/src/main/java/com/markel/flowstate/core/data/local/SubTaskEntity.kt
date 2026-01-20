package com.markel.flowstate.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE // If the parent task is deleted, the children are also deleted
        )
    ],
    // Creating an index on taskId makes queries much faster
    indices = [Index(value = ["taskId"])]
)
data class SubTaskEntity(
    @PrimaryKey
    val id: String, // We use String (UUID) for subtasks
    val taskId: Int, // The reference to the parent
    val title: String,
    val isDone: Boolean
)