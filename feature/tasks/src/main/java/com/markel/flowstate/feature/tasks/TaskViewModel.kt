package com.markel.flowstate.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _uiState = MutableStateFlow<TasksUiState>(TasksUiState.Loading)


    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

     // El bloque init actúa como un "suscriptor" permanente.
     // Se conecta al Repositorio y actualiza el estado cada vez que la base de datos cambia.
    init {
        viewModelScope.launch {
            repository.getTasks().collect { list ->
                val filteredList = list.filter { !it.isDone }
                _uiState.value = TasksUiState.Success(filteredList)
            }
        }
    }

    // Función para añadir una nueva tarea
    fun addTask(title: String, description: String) {
        if (title.isBlank()) return // Evita tareas vacías
        viewModelScope.launch {
            val currentTasks = (uiState.value as? TasksUiState.Success)?.tasks ?: emptyList()
            val minPosition = currentTasks.minOfOrNull { it.position } ?: 0

            val newTask = Task(
                title = title,
                description = description,
                isDone = false,
                position = minPosition - 1
            )
            repository.upsertTask(newTask)
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

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val currentList = (uiState.value as? TasksUiState.Success)?.tasks?.toMutableList() ?: return

        // 1. Aplicar el movimiento en la lista temporal
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)

        // 2. Recalcular las posiciones (índices 0, 1, 2...)
        val updatedList = currentList.mapIndexed { index, task ->
            task.copy(position = index)
        }

        // 3. Actualizar la interfaz YA
        _uiState.value = TasksUiState.Success(updatedList)

        // 4. Persistir en Base de Datos
        viewModelScope.launch {
            repository.updateTasksOrder(updatedList)
        }
    }
}