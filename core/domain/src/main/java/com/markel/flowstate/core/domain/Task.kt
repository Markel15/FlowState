package com.markel.flowstate.core.domain

import java.util.UUID

/**
 * Este es el modelo de Tarea "limpio".
 * Es la representación de una Tarea para la UI y la lógica de negocio (ViewModels).
 * No sabe nada de la base de datos (@Entity, @PrimaryKey, etc.).
 */

data class SubTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false
)
data class Task(
    val id: Int = 0,
    val title: String,
    val description: String = "",
    val isDone: Boolean,
    val subTasks: List<SubTask> = emptyList()
    // Aquí irían los demás campos del dominio como
    // priority: Priority,
    // dueDate: LocalDate?
)