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
            onDelete = ForeignKey.CASCADE // Si se borra la tarea padre, se borran las hijas
        )
    ],
    // Crear un índice en taskId hace que las consultas sean mucho más rápidas
    indices = [Index(value = ["taskId"])]
)
data class SubTaskEntity(
    @PrimaryKey
    val id: String, // Usamos String (UUID) para las subtareas
    val taskId: Int, // La referencia al padre
    val title: String,
    val isDone: Boolean
)