package com.markel.flowstate.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.SubTask
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


// Define possible screen states
sealed interface TasksUiState {
    data object Loading : TasksUiState
    data class Success(val tasks: List<Task>) : TasksUiState
}
/**
 * ViewModel for the Tasks screen.
 * Contains business logic and exposes state to the UI.
 */
@HiltViewModel
class TaskViewModel  @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TasksUiState>(TasksUiState.Loading)


    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    // The init block acts as a "subscriber" to the repository
    init {
        viewModelScope.launch {
            repository.getTasks().collect { list ->
                val filteredList = list.filter { !it.isDone }
                _uiState.value = TasksUiState.Success(filteredList)
            }
        }
    }

    // Function to add a new task
    fun addTask(title: String, description: String, priority: Priority, dueDate: Long?, subTasks: List<SubTask>) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val currentTasks = (uiState.value as? TasksUiState.Success)?.tasks ?: emptyList()
            val minPosition = currentTasks.minOfOrNull { it.position } ?: 0

            val newTask = Task(
                title = title,
                description = description,
                isDone = false,
                position = minPosition - 1,
                priority = priority,
                dueDate = dueDate,
                subTasks = subTasks
            )
            repository.upsertTask(newTask)
        }
    }

    // Function to edit an existing task
    fun updateTask(originalTask: Task, newTitle: String, newDescription: String, newPriority: Priority, newDueDate: Long?, newSubTasks: List<SubTask>) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.upsertTask(
                originalTask.copy(
                    title = newTitle,
                    description = newDescription,
                    priority = newPriority,
                    dueDate = newDueDate,
                    subTasks = newSubTasks
                )
            )
        }
    }

    // Function to delete a task
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // Function to toggle task completion status
    fun toggleTaskDone(task: Task) {
        viewModelScope.launch {
            repository.upsertTask(task.copy(isDone = !task.isDone))
        }
    }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val currentList = (uiState.value as? TasksUiState.Success)?.tasks?.toMutableList() ?: return

        // 1. Apply movement in the temporary list
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)

        // 2. Recalculate positions (indices 0, 1, 2...)
        val updatedList = currentList.mapIndexed { index, task ->
            task.copy(position = index)
        }

        // 3. Update the interface NOW
        _uiState.value = TasksUiState.Success(updatedList)

        // 4. Save to database in the background
        viewModelScope.launch {
            repository.updateTasksOrder(updatedList)
        }
    }
}