package com.markel.flowstate.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Esta es la "tabla" de tu base de datos.
 * Representa una única Tarea.
 * Basado en los "Must Have", empezamos solo con lo esencial.
 */
@Entity(tableName = "tasks") // Así se llamará la tabla en la base de datos
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,
    val isDone: Boolean = false

    // Más adelante, si "Prioridades" o "Fechas"
    // val priority: Int = 1,
    // val dueDate: Long? = null
)