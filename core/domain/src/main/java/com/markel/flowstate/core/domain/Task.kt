package com.markel.flowstate.core.domain

/**
 * Este es el modelo de Tarea "limpio".
 * Es la representación de una Tarea para la UI y la lógica de negocio (ViewModels).
 * No sabe nada de la base de datos (@Entity, @PrimaryKey, etc.).
 * Es un simple data class de Kotlin (a veces llamado POKO).
 */
data class Task(
    val id: Int = 0,
    val title: String,
    val isDone: Boolean
    // Aquí irían los demás campos del dominio como
    // priority: Priority,
    // dueDate: LocalDate?
)