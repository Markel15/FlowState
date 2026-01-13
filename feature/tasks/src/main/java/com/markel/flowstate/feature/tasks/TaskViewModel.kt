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


// Definimos los estados posibles de la pantalla
sealed interface TasksUiState {
    data object Loading : TasksUiState
    data class Success(val tasks: List<Task>) : TasksUiState
}
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
    val uiState: StateFlow<TasksUiState> = repository.getTasks()
        .map { list ->
            val filteredList = list.filter { !it.isDone }
            TasksUiState.Success(filteredList)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = TasksUiState.Loading
        )

    // Función para añadir una nueva tarea
    fun addTask(title: String, description: String) {
        if (title.isBlank()) return // Evita tareas vacías
        viewModelScope.launch {
            repository.upsertTask(Task(title = title, description = description, isDone = false))
        }
    }

    // Recibe la tarea original para mantener su ID, estado isDone y subtareas
    fun updateTask(originalTask: Task, newTitle: String, newDescription: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.upsertTask(
                originalTask.copy(
                    title = newTitle,
                    description = newDescription
                )
            )
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