package com.markel.flowstate.feature.flow.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.core.domain.CategoryRepository
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.SubTask
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskRepository
import com.markel.flowstate.core.domain.usecase.tasks.DeleteTaskUseCase
import com.markel.flowstate.core.domain.usecase.tasks.ToggleTaskUseCase
import com.markel.flowstate.core.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * ViewModel exclusive for TaskEditorScreen.
 *
 * Separating it from TaskViewModel (which lives in FlowScreen/Tasks) prevents crashes:
 *
 * Possible crash because TaskViewModel was obtained with hiltViewModel() in both FlowScreen
 * and TaskEditorScreen. By navigating quickly, the editor's backstack entry was destroyed
 * while FlowScreen was still trying to access the same ViewModel, causing conflicts.
 *
 * With a dedicated ViewModel per screen, each lives and dies with its NavBackStackEntry and solves that problem.
 */
sealed interface TaskEditorState {
    data object Loading : TaskEditorState
    data class Ready(val task: Task) : TaskEditorState
    data object NotFound : TaskEditorState
    data class Error(val cause: Throwable) : TaskEditorState
}

val TaskEditorState.taskOrNull: Task?
    get() = (this as? TaskEditorState.Ready)?.task

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val toggleTaskUseCase: ToggleTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val reminderScheduler: ReminderScheduler,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _editor = MutableStateFlow<TaskEditorState>(TaskEditorState.Loading)
    val editor: StateFlow<TaskEditorState> = _editor.asStateFlow()

    private var loadTaskJob: Job? = null

    /**
     * Serializes task writes coming from autosave and immediate actions such
     * as changing a category or reminder. Each write reads the latest editor
     * snapshot while holding the lock, so a stale composable callback cannot
     * overwrite a newer field.
     */
    private val taskMutationMutex = Mutex()

    /** User categories, exposed so the editor can populate the category selector. */
    val categories: StateFlow<List<Category>> = categoryRepository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether category tabs are enabled — the selector is only shown when true. */
    val categoriesEnabled: StateFlow<Boolean> = userPreferencesRepository.categoriesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val generalCategoryName: StateFlow<String?> = userPreferencesRepository.generalCategoryName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Loads one task and represents every outcome explicitly in editor state. */
    fun loadTask(taskId: Int) {
        loadTaskJob?.cancel()
        _editor.value = TaskEditorState.Loading
        loadTaskJob = viewModelScope.launch {
            try {
                val task = repository.getTaskById(taskId)
                _editor.value = if (task == null) {
                    TaskEditorState.NotFound
                } else {
                    TaskEditorState.Ready(task)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (cause: Throwable) {
                _editor.value = TaskEditorState.Error(cause)
            }
        }
    }

    /**
     * Saves the latest draft fields on top of the canonical task snapshot
     * held by this ViewModel. The composable deliberately does not pass an
     * "original task" back here because that object may be stale after an
     * immediate mutation such as a category change.
     */
    fun updateTask(
        newTitle: String,
        newDescription: String,
        newPriority: Priority,
        newDueDate: Long?,
        newReminderTime: Long?,
        newSubTasks: List<SubTask>
    ) {
        if (newTitle.isBlank()) return

        viewModelScope.launch {
            taskMutationMutex.withLock {
                val currentTask = _editor.value.taskOrNull ?: return@withLock
                val updatedTask = currentTask.copy(
                    title = newTitle,
                    description = newDescription,
                    priority = newPriority,
                    dueDate = newDueDate,
                    reminderTime = newReminderTime,
                    categoryId = currentTask.categoryId ?: Category.GENERAL_ID,
                    isDone = currentTask.isDone,
                    subTasks = newSubTasks
                )

                repository.upsertTask(updatedTask)
                reconcileSubTaskAlarms(original = currentTask, updated = updatedTask)
                updateReadyTask { updatedTask }
            }
        }
    }

    fun updatePriority(value: Priority) = updateReadyTask { it.copy(priority = value) }

    fun updateDueDate(value: Long?) = updateReadyTask { it.copy(dueDate = value) }

    /**
     * Moves the task being edited to a different category.
     *
     * Pass [Category.GENERAL_ID] to move the task to the default (General)
     * category. The change is persisted immediately and the editor state is
     * updated so the selector reflects the new value.
     */
    fun updateCategory(categoryId: Int?) {
        val normalizedCategoryId = categoryId ?: Category.GENERAL_ID
        updateReadyTask { it.copy(categoryId = normalizedCategoryId) }

        viewModelScope.launch {
            taskMutationMutex.withLock {
                val task = _editor.value.taskOrNull ?: return@withLock
                repository.upsertTask(task)
            }
        }
    }

    fun updateReminderTime(value: Long?) {
        val effectiveValue = if (value != null && value > System.currentTimeMillis()) value else null
        updateReadyTask { it.copy(reminderTime = effectiveValue) }

        viewModelScope.launch {
            taskMutationMutex.withLock {
                val task = _editor.value.taskOrNull ?: return@withLock
                val currentReminderTime = task.reminderTime

                // Cancel the old alarm regardless of whether we're setting a new one.
                reminderScheduler.cancel(task.id)
                repository.upsertTask(task)

                if (currentReminderTime != null) {
                    reminderScheduler.schedule(
                        task.id,
                        task.title,
                        task.description,
                        currentReminderTime
                    )
                }
            }
        }
    }

    fun toggleDone() {
        if (_editor.value.taskOrNull == null) return

        viewModelScope.launch {
            taskMutationMutex.withLock {
                val current = _editor.value.taskOrNull ?: return@withLock
                val updatedTask = toggleTaskUseCase(current)

                updateReadyTask { updatedTask }

                if (updatedTask.isDone) {
                    reminderScheduler.cancel(updatedTask.id)
                    current.subTasks
                        .filter { it.reminderTime != null }
                        .forEach { subTask ->
                            reminderScheduler.cancelSubTask(subTask.id)
                        }
                }
            }
        }
    }
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            reminderScheduler.cancel(task.id)
            task.subTasks.forEach { reminderScheduler.cancelSubTask(it.id) }  // Cancel all subtask alarms
            deleteTaskUseCase(task)
        }
    }


    // ── Internal ──────────────────────────────────────────────────────────────

    private fun updateReadyTask(transform: (Task) -> Task) {
        _editor.update { state ->
            if (state is TaskEditorState.Ready) {
                state.copy(task = transform(state.task))
            } else {
                state
            }
        }
    }

    /**
     * Diffs old vs new subtask list to cancel removed alarms and schedule new ones.
     * Only touches alarms for subtasks whose reminderTime actually changed.
     */
    private fun reconcileSubTaskAlarms(original: Task, updated: Task) {
        val now = System.currentTimeMillis()
        val oldMap = original.subTasks.associateBy { it.id }
        val newMap = updated.subTasks.associateBy { it.id }

        // Canceled or removed subtasks
        oldMap.forEach { (id, old) ->
            if (old.reminderTime != null && newMap[id]?.reminderTime != old.reminderTime) {
                reminderScheduler.cancelSubTask(id)
            }
        }

        // New or updated reminders
        newMap.forEach { (id, new) ->
            val oldReminder = oldMap[id]?.reminderTime
            val newReminder = new.reminderTime
            if (newReminder != null &&
                newReminder != oldReminder &&
                newReminder > now
            ) {
                reminderScheduler.scheduleSubTask(id, new.title, newReminder)
            }
        }
    }

}