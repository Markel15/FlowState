package com.markel.flowstate.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Esta es la "tabla" de la base de datos.
 * Representa una única Tarea.
 */
@Entity(tableName = "tasks") // Así se llamará la tabla en la base de datos
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String = "",
    val isDone: Boolean = false

    // Más adelante, si "Prioridades" o "Fechas"
    // val priority: Int = 1,
    // val dueDate: Long? = null
)