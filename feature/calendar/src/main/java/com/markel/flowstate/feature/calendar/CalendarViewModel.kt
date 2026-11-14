package com.markel.flowstate.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskRepository
import com.markel.flowstate.core.domain.usecase.tasks.ToggleTaskUseCase
import com.markel.flowstate.core.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// UI state
sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data class Success(
        val tasksByDate: Map<LocalDate, List<Task>>,
        val selectedDate: LocalDate = LocalDate.now()
    ) : CalendarUiState
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val toggleTaskUseCase: ToggleTaskUseCase,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())

    // Combine both flows
    val uiState: StateFlow<CalendarUiState> = combine(
        repository.getTasks(),
        _selectedDate
    ) { tasks, selectedDate ->
        CalendarUiState.Success(
            tasksByDate = groupTasksByDate(tasks),
            selectedDate = selectedDate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState.Loading
    )

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
        // the combine() will automatically emit a new state every time a new date is selected
    }

    private fun groupTasksByDate(tasks: List<Task>): Map<LocalDate, List<Task>> {
        return tasks
            .mapNotNull { task ->
                val dateKey = when {
                    task.dueDate != null -> {
                        Instant.ofEpochMilli(task.dueDate!!)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                    }
                    task.isDone && task.completedAt != null -> {
                        Instant.ofEpochMilli(task.completedAt!!)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    else -> null
                }
                dateKey?.let { it to task }
            }
            .groupBy({ it.first }, { it.second })
    }

    fun toggleTaskDone(task: Task) {
        viewModelScope.launch {
            val completing = !task.isDone
            toggleTaskUseCase(task)
            if (completing) {
                reminderScheduler.cancel(task.id)
                task.subTasks.forEach { reminderScheduler.cancelSubTask(it.id) }
            }
        }
    }

    fun undoTaskToggle(originalTask: Task) {
        viewModelScope.launch {
            repository.upsertTask(
                originalTask.copy(
                    isDone = true,
                    completedAt = originalTask.completedAt
                )
            )
        }
    }
}