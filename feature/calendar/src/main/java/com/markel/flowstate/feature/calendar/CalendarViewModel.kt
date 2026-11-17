package com.markel.flowstate.feature.calendar

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
import kotlinx.coroutines.flow.update
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
        val selectedDate: LocalDate = LocalDate.now(),
        val today: LocalDate = LocalDate.now()
    ) : CalendarUiState
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val toggleTaskUseCase: ToggleTaskUseCase,
    private val reminderScheduler: ReminderScheduler
) : ViewModel(), DefaultLifecycleObserver {

    private data class DateState(
        val today: LocalDate = LocalDate.now(),
        val selectedDate: LocalDate = today
    )

    private val _dateState = MutableStateFlow(DateState())

    // Combine flows
    val uiState: StateFlow<CalendarUiState> = combine(
        repository.getTasks(),
        _dateState
    ) { tasks, dateState ->
        CalendarUiState.Success(
            tasksByDate = groupTasksByDate(tasks),
            selectedDate = dateState.selectedDate,
            today = dateState.today
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState.Loading
    )

    override fun onResume(owner: LifecycleOwner) {
        refreshTodayIfNeeded()
    }

    /**
     * Re-evaluates the current date when returning to the foreground.
     * Updating [today] and [selectedDate] inside a single [_dateState] avoids
     * emitting an intermediate frame where [today] has advanced while
     * [selectedDate] is still on the previous day.
     */
    fun refreshTodayIfNeeded(now: LocalDate = LocalDate.now()) {
        _dateState.update { state ->
            if (now == state.today) {
                state
            } else {
                state.copy(
                    today = now,
                    selectedDate = if (state.selectedDate == state.today) now else state.selectedDate
                )
            }
        }
    }

    fun onDateSelected(date: LocalDate) {
        _dateState.update { it.copy(selectedDate = date) }
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