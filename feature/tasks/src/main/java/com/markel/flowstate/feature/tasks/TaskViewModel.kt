package com.markel.flowstate.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para la pantalla de Tareas.
 * Contiene la lógica de negocio y expone el estado a la UI.
 */
@HiltViewModel
class TaskViewModel  @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    // Expone el Flow de tareas del repositorio como un StateFlow
    // que la UI puede consumir. Se mantiene vivo 5 segundos (SharingStarted.WhileSubscribed)
    val tasks: StateFlow<List<Task>> = repository.getTasks()
        .map { list -> list.filter { !it.isDone } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList() // Empieza con una lista vacía
        )

    // Función para añadir una nueva tarea
    fun addTask(title: String) {
        if (title.isBlank()) return // Evita tareas vacías
        viewModelScope.launch {
            repository.upsertTask(Task(title = title, isDone = false))
        }
    }

    // Función para eliminar una tarea
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // Función para marcar una tarea como hecha/no hecha
    fun toggleTaskDone(task: Task) {
        viewModelScope.launch {
            repository.upsertTask(task.copy(isDone = !task.isDone))
        }
    }
}