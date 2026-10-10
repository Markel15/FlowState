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
import kotlinx.coroutines.delay
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
    data class Ready(
        val task: Task,
        /** Increments only for fields handled by the debounced autosave. */
        val autosaveRevision: Long = 0,
        /** Highest autosave revision confirmed by the repository. */
        val savedRevision: Long = 0
    ) : TaskEditorState
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
    private var autosaveJob: Job? = null

    /**
     * Serializes task writes coming from autosave and immediate actions such
     * as changing a category or reminder. Each write reads the latest editor
     * snapshot while holding the lock, so a stale composable callback cannot
     * overwrite a newer field.
     */
    private val taskMutationMutex = Mutex()

    /** Last snapshot successfully written, used as the alarm reconciliation baseline. */
    private var persistedTask: Task? = null

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
        autosaveJob?.cancel()
        autosaveJob = null
        persistedTask = null
        _editor.value = TaskEditorState.Loading
        loadTaskJob = viewModelScope.launch {
            try {
                val task = repository.getTaskById(taskId)
                _editor.value = if (task == null) {
                    persistedTask = null
                    TaskEditorState.NotFound
                } else {
                    persistedTask = task
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
     * Requests an immediate save. The debounce itself is owned by this ViewModel;
     * this entry point remains temporarily for the screen's best-effort dispose flush.
     */
    fun saveTask() {
        autosaveJob?.cancel()
        autosaveJob = null
        viewModelScope.launch { persistCurrentDraft() }
    }

    fun updateTitle(value: String) = updateReadyTask(autosave = true) { it.copy(title = value) }

    fun updateDescription(value: String) =
        updateReadyTask(autosave = true) { it.copy(description = value) }

    fun updatePriority(value: Priority) =
        updateReadyTask(autosave = true) { it.copy(priority = value) }

    fun updateDueDate(value: Long?) =
        updateReadyTask(autosave = true) { it.copy(dueDate = value) }

    fun updateSubTasks(value: List<SubTask>) =
        updateReadyTask(autosave = true) { it.copy(subTasks = value) }

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

        saveTask()
    }

    fun updateReminderTime(value: Long?) {
        val effectiveValue = if (value != null && value > System.currentTimeMillis()) value else null
        updateReadyTask { it.copy(reminderTime = effectiveValue) }

        saveTask()
    }

    fun toggleDone() {
        if (_editor.value.taskOrNull == null) return

        viewModelScope.launch {
            taskMutationMutex.withLock {
                val current = _editor.value.taskOrNull ?: return@withLock
                val updatedTask = toggleTaskUseCase(current)

                persistedTask = updatedTask
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

    private fun updateReadyTask(
        autosave: Boolean = false,
        transform: (Task) -> Task
    ) {
        var changed = false
        _editor.update { state ->
            if (state !is TaskEditorState.Ready) return@update state
            val updated = transform(state.task)
            if (updated == state.task) return@update state

            changed = true
            state.copy(
                task = updated,
                autosaveRevision = if (autosave) state.autosaveRevision + 1 else state.autosaveRevision
            )
        }

        // Keep coroutine scheduling outside update(), whose reducer may retry.
        if (autosave && changed) scheduleAutosave()
    }

    /** Restarts the debounce so rapid edits result in one save of the latest draft. */
    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            // Clear the debounce handle before I/O so a new edit does not cancel
            // an upsert that has already started.
            autosaveJob = null
            persistCurrentDraft()
        }
    }

    /**
     * Reads the draft after acquiring the lock. Persistence never publishes
     * its snapshot back into editor state, so an older save cannot replace an
     * edit made while Room was suspended.
     */
    private suspend fun persistCurrentDraft() {
        taskMutationMutex.withLock {
            val ready = _editor.value as? TaskEditorState.Ready ?: return@withLock
            val task = ready.task
            val revision = ready.autosaveRevision
            if (task.title.isBlank()) return@withLock
            val original = persistedTask ?: task

            repository.upsertTask(task)
            reconcileTaskAlarm(original = original, updated = task)
            reconcileSubTaskAlarms(original = original, updated = task)
            persistedTask = task

            // Mark only the revision represented by this persisted snapshot. If
            // the user edited during the Room write, the newer draft stays dirty.
            _editor.update { current ->
                if (current is TaskEditorState.Ready && current.task.id == task.id) {
                    current.copy(savedRevision = maxOf(current.savedRevision, revision))
                } else {
                    current
                }
            }
        }
    }

    /** Replaces the task alarm when its time or displayed content changed. */
    private fun reconcileTaskAlarm(original: Task, updated: Task) {
        val alarmChanged = original.reminderTime != updated.reminderTime ||
            original.title != updated.title ||
            original.description != updated.description
        if (!alarmChanged) return

        reminderScheduler.cancel(updated.id)
        val reminderTime = updated.reminderTime
        if (!updated.isDone && reminderTime != null && reminderTime > System.currentTimeMillis()) {
            reminderScheduler.schedule(
                updated.id,
                updated.title,
                updated.description,
                reminderTime
            )
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

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 600L
    }
}
