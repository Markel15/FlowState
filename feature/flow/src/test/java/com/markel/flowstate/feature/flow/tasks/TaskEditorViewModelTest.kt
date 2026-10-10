package com.markel.flowstate.feature.flow.tasks

import app.cash.turbine.test
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
import com.markel.flowstate.core.testing.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TaskEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: TaskRepository = mockk(relaxed = true)
    private val toggleTaskUseCase: ToggleTaskUseCase = mockk(relaxed = true)
    private val deleteTaskUseCase: DeleteTaskUseCase = mockk(relaxed = true)
    private val reminderScheduler: ReminderScheduler = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private lateinit var viewModel: TaskEditorViewModel

    /** Drives the same field-level API used by Compose, then requests a save. */
    private fun updateTaskDraft(
        newTitle: String,
        newDescription: String,
        newPriority: Priority,
        newDueDate: Long?,
        newReminderTime: Long?,
        newSubTasks: List<SubTask>
    ) {
        viewModel.updateTitle(newTitle)
        viewModel.updateDescription(newDescription)
        viewModel.updatePriority(newPriority)
        viewModel.updateDueDate(newDueDate)
        if (viewModel.editor.value.taskOrNull?.reminderTime != newReminderTime) {
            viewModel.updateReminderTime(newReminderTime)
        }
        viewModel.updateSubTasks(newSubTasks)
        viewModel.saveTask()
    }

    @Test
    fun initialState_isEmpty() = runTest {
        // GIVEN / WHEN - A fresh ViewModel with no loadTask call yet
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        // THEN - editor state should be empty
        viewModel.editor.test {
            val state = awaitItem()
            assertEquals(TaskEditorState.Loading, state)
            assertNull(state.taskOrNull)
        }
    }

    @Test
    fun loadTask_whenTaskExists_populatesEditorState() = runTest {
        // GIVEN - A task in the repository
        val task = Task(id = 42, title = "Buy milk", isDone = false, priority = Priority.HIGH, dueDate = 1000L)
        coEvery { repository.getTaskById(any()) } returns task

        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.loadTask(taskId = 42)

        // THEN - Editor state should reflect the task
        viewModel.editor.test {
            val state = awaitItem()
            assertEquals(task, state.taskOrNull)
            assertEquals(Priority.HIGH, state.taskOrNull?.priority)
            assertEquals(1000L, state.taskOrNull?.dueDate)
            assertEquals(false, state.taskOrNull?.isDone)
        }
    }

    @Test
    fun loadTask_whenTaskDoesNotExist_keepsEmptyState() = runTest {
        // GIVEN - Repository returns a list that does NOT contain the requested ID
        coEvery { repository.getTaskById(any()) } returns null

        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)


        // WHEN
        viewModel.loadTask(taskId = 99)

        // THEN - State should remain null (no crash, no phantom data)
        viewModel.editor.test {
            val state = awaitItem()
            assertEquals(TaskEditorState.NotFound, state)
            assertNull(state.taskOrNull)
        }
    }

    @Test
    fun loadTask_whenRepositoryFails_exposesErrorState() = runTest {
        val failure = IllegalStateException("Database unavailable")
        coEvery { repository.getTaskById(42) } throws failure
        viewModel = TaskEditorViewModel(
            repository,
            toggleTaskUseCase,
            deleteTaskUseCase,
            reminderScheduler,
            categoryRepository,
            userPreferencesRepository
        )

        viewModel.loadTask(42)

        viewModel.editor.test {
            val state = awaitItem()
            assertTrue(state is TaskEditorState.Error)
            assertEquals(failure, (state as TaskEditorState.Error).cause)
        }
    }

    @Test
    fun updatePriority_updatesTaskSnapshotCorrectly() = runTest {
        // GIVEN
        val task = Task(id = 1, title = "Task", isDone = false)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)
        viewModel.loadTask(1)
        advanceUntilIdle()

        // WHEN
        viewModel.updatePriority(Priority.MEDIUM)

        // THEN - the task snapshot is the only source of truth
        viewModel.editor.test {
            assertEquals(Priority.MEDIUM, awaitItem().taskOrNull?.priority)
        }
    }

    @Test
    fun updateDueDate_updatesTaskSnapshotCorrectly() = runTest {
        // GIVEN
        val task = Task(id = 1, title = "Task", isDone = false)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)
        viewModel.loadTask(1)
        advanceUntilIdle()

        // WHEN
        viewModel.updateDueDate(9999L)

        // THEN - the task snapshot is the only source of truth
        viewModel.editor.test {
            assertEquals(9999L, awaitItem().taskOrNull?.dueDate)
        }
    }

    @Test
    fun updateTask_callsRepositoryWithUpdatedData() = runTest {
        // GIVEN - A task loaded in the editor
        val original = Task(id = 5, title = "Old title", isDone = false, priority = Priority.NOTHING)
        coEvery { repository.getTaskById(any()) } returns original
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(5)

        // WHEN - User edits the task
        updateTaskDraft(
            newTitle = "New title",
            newDescription = "New desc",
            newPriority = Priority.HIGH,
            newDueDate = 5000L,
            newReminderTime = null,
            newSubTasks = emptyList()
        )

        // THEN - Repository should receive the updated copy
        coVerify {
            repository.upsertTask(match { task ->
                task.id == 5 &&
                        task.title == "New title" &&
                        task.description == "New desc" &&
                        task.priority == Priority.HIGH &&
                        task.dueDate == 5000L
            })
        }
    }

    @Test
    fun updateTask_withBlankTitle_doesNotCallRepository() = runTest {
        // GIVEN
        val original = Task(id = 1, title = "Original", isDone = false)
        coEvery { repository.getTaskById(any()) } returns original
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)
        viewModel.loadTask(1)
        advanceUntilIdle()

        // WHEN - Trying to save with a blank title
        updateTaskDraft("   ", "", Priority.NOTHING, null, null, emptyList())

        // THEN - Repository must NOT be called
        coVerify(exactly = 0) { repository.upsertTask(any()) }
    }

    @Test
    fun toggleDone_callsToggleUseCaseAndUpdatesState() = runTest {
        // GIVEN
        val task = Task(id = 1, title = "Task", isDone = false)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(1)
        coEvery { toggleTaskUseCase(task) } returns task.copy(
            isDone = true,
            completedAt = 1234L,
            reminderTime = null
        )

        // WHEN
        viewModel.toggleDone()

        // THEN - State updates optimistically
        viewModel.editor.test {
            assertEquals(true, awaitItem().taskOrNull?.isDone)
        }
        // AND - Use case is called
        coVerify { toggleTaskUseCase(task) }
    }

    @Test
    fun deleteTask_cancelsAlarmsAndCallsDeleteUseCase() = runTest {
        // GIVEN
        val task = Task(id = 3, title = "Task to delete", isDone = false)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(3)

        // WHEN
        viewModel.deleteTask(task)

        // THEN
        coVerify { reminderScheduler.cancel(3) }
        coVerify { deleteTaskUseCase(task) }
    }

    // ── updateReminderTime tests ──────────────────────────────────────────────
    @Test
    fun updateReminderTime_with_future_time_cancels_old_and_schedules_new() = runTest {
        val task = Task(id = 10, title = "My Task", description = "Desc", isDone = false)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(10)

        val futureTime = System.currentTimeMillis() + 60_000L

        viewModel.updateReminderTime(futureTime)

        // Old alarm should be canceled
        coVerify { reminderScheduler.cancel(10) }
        // New alarm should be scheduled
        coVerify { reminderScheduler.schedule(10, "My Task", "Desc", futureTime) }
        // Task should be persisted with new reminderTime
        coVerify { repository.upsertTask(match { it.reminderTime == futureTime }) }
    }

    @Test
    fun updateReminderTime_with_past_time_cancels_old_and_does_not_schedule() = runTest {
        val task = Task(id = 10, title = "My Task", description = "Desc", isDone = false, reminderTime = System.currentTimeMillis() + 60_000L)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(10)

        val pastTime = System.currentTimeMillis() - 60_000L

        viewModel.updateReminderTime(pastTime)

        // Old alarm should be canceled
        coVerify { reminderScheduler.cancel(10) }
        // No new alarm should be scheduled (past time → effectiveValue is null)
        coVerify(exactly = 0) { reminderScheduler.schedule(any(), any(), any(), any()) }
    }

    @Test
    fun updateReminderTime_with_null_cancels_old_alarm() = runTest {
        val task = Task(id = 10, title = "My Task", isDone = false, reminderTime = System.currentTimeMillis() + 60_000L)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(10)

        viewModel.updateReminderTime(null)

        coVerify { reminderScheduler.cancel(10) }
        coVerify(exactly = 0) { reminderScheduler.schedule(any(), any(), any(), any()) }
    }

    // ── toggleDone with subtask alarms ────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun toggleDone_completing_cancels_task_alarm_and_subtask_alarms_with_reminders() = runTest {
        val sub1 = SubTask(id = "s1", title = "Sub1", reminderTime = System.currentTimeMillis() + 60_000L)
        val sub2 = SubTask(id = "s2", title = "Sub2", reminderTime = null) // no reminder → should NOT be canceled
        val task = Task(id = 10, title = "Task", isDone = false, subTasks = listOf(sub1, sub2))
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        coEvery { toggleTaskUseCase(task) } returns task.copy(
            isDone = true,
            completedAt = 1234L,
            reminderTime = null,
            subTasks = task.subTasks.map {
                it.copy(reminderTime = null)
            }
        )

        viewModel.loadTask(10)
        advanceUntilIdle()

        viewModel.toggleDone()
        advanceUntilIdle()

        coVerify { reminderScheduler.cancel(10) }
        coVerify { reminderScheduler.cancelSubTask("s1") }
        // s2 has no reminder, so cancelSubTask should NOT be called for it
        coVerify(exactly = 0) { reminderScheduler.cancelSubTask("s2") }
    }

    @Test
    fun toggleDone_uncompleting_does_not_cancel_alarms() = runTest {
        val task = Task(id = 10, title = "Done task", isDone = true)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(10)

        viewModel.toggleDone()

        coVerify(exactly = 0) { reminderScheduler.cancel(any()) }
        coVerify(exactly = 0) { reminderScheduler.cancelSubTask(any()) }
    }

    // ── reconcileSubTaskAlarms (via updateTask) ───────────────────────────────

    @Test
    fun updateTask_cancels_alarm_for_removed_subtask_with_reminder() = runTest {
        val subWithReminder = SubTask(id = "s1", title = "Sub1", reminderTime = System.currentTimeMillis() + 60_000L)
        val original = Task(id = 5, title = "Original", isDone = false, subTasks = listOf(subWithReminder))
        coEvery { repository.getTaskById(any()) } returns original
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(5)

        // Update: remove the subtask
        updateTaskDraft("Updated", "", Priority.NOTHING, null, null, emptyList())

        coVerify { reminderScheduler.cancelSubTask("s1") }
    }

    @Test
    fun updateTask_schedules_alarm_for_new_subtask_with_future_reminder() = runTest {
        val original = Task(id = 5, title = "Original", isDone = false, subTasks = emptyList())
        coEvery { repository.getTaskById(any()) } returns original
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(5)

        val futureTime = System.currentTimeMillis() + 60_000L
        val newSub = SubTask(id = "s-new", title = "New Sub", reminderTime = futureTime)

        updateTaskDraft("Updated", "", Priority.NOTHING, null, null, listOf(newSub))

        coVerify { reminderScheduler.scheduleSubTask("s-new", "New Sub", futureTime) }
    }

    @Test
    fun updateTask_does_not_schedule_alarm_for_new_subtask_with_past_reminder() = runTest {
        val original = Task(id = 5, title = "Original", isDone = false, subTasks = emptyList())
        coEvery { repository.getTaskById(any()) } returns original
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(5)

        val pastTime = System.currentTimeMillis() - 60_000L
        val newSub = SubTask(id = "s-old", title = "Old Sub", reminderTime = pastTime)

        updateTaskDraft("Updated", "", Priority.NOTHING, null, null, listOf(newSub))

        coVerify(exactly = 0) { reminderScheduler.scheduleSubTask(any(), any(), any()) }
    }

    @Test
    fun updateTask_does_not_cancel_alarm_when_subtask_reminderTime_unchanged() = runTest {
        val futureTime = System.currentTimeMillis() + 60_000L
        val sub = SubTask(id = "s1", title = "Sub1", reminderTime = futureTime)
        val original = Task(id = 5, title = "Original", isDone = false, subTasks = listOf(sub))
        coEvery { repository.getTaskById(any()) } returns original
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(5)

        // Update with same subtask, same reminder → no cancel, no schedule
        updateTaskDraft("Updated", "", Priority.NOTHING, null, null, listOf(sub))

        coVerify(exactly = 0) { reminderScheduler.cancelSubTask(any()) }
        coVerify(exactly = 0) { reminderScheduler.scheduleSubTask(any(), any(), any()) }
    }

    @Test
    fun updateTask_cancels_old_and_schedules_new_when_subtask_reminderTime_changes() = runTest {
        val oldTime = System.currentTimeMillis() + 30_000L
        val subOld = SubTask(id = "s1", title = "Sub1", reminderTime = oldTime)
        val original = Task(id = 5, title = "Original", isDone = false, subTasks = listOf(subOld))
        coEvery { repository.getTaskById(any()) } returns original
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(5)

        val newTime = System.currentTimeMillis() + 90_000L
        val subUpdated = SubTask(id = "s1", title = "Sub1", reminderTime = newTime)

        updateTaskDraft("Updated", "", Priority.NOTHING, null, null, listOf(subUpdated))

        coVerify { reminderScheduler.cancelSubTask("s1") }
        coVerify { reminderScheduler.scheduleSubTask("s1", "Sub1", newTime) }
    }

    // ── deleteTask with subtask alarms ────────────────────────────────────────

    @Test
    fun deleteTask_cancels_task_alarm_and_all_subtask_alarms() = runTest {
        val sub1 = SubTask(id = "s1", title = "Sub1", reminderTime = System.currentTimeMillis() + 60_000L)
        val sub2 = SubTask(id = "s2", title = "Sub2", reminderTime = null) // even without reminder
        val task = Task(id = 10, title = "Task", isDone = false, subTasks = listOf(sub1, sub2))
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(10)

        viewModel.deleteTask(task)

        coVerify { reminderScheduler.cancel(10) }
        // deleteTask cancels ALL subtask alarms (not just ones with reminders)
        coVerify { reminderScheduler.cancelSubTask("s1") }
        coVerify { reminderScheduler.cancelSubTask("s2") }
        coVerify { deleteTaskUseCase(task) }
    }


    // ── Category selection in the editor ──────────────────────────────────────

    @Test
    fun loadTask_populatesEditorStateWithTaskCategoryId() = runTest {
        // GIVEN — a task that belongs to category 5
        val task = Task(id = 1, title = "Buy milk", isDone = false, categoryId = 5)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.loadTask(1)

        // THEN — the editor state reflects the task's category
        viewModel.editor.test {
            val state = awaitItem()
            assertEquals(5, state.taskOrNull?.categoryId)
        }
    }

    @Test
    fun loadTask_withGeneralTask_populatesGeneralCategoryId() = runTest {
        // GIVEN — a task in the General category
        val task = Task(id = 1, title = "Buy milk", isDone = false, categoryId = Category.GENERAL_ID)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        viewModel.loadTask(1)

        viewModel.editor.test {
            val state = awaitItem()
            assertEquals(Category.GENERAL_ID, state.taskOrNull?.categoryId)
        }
    }

    @Test
    fun updateCategory_updatesEditorStateImmediately() = runTest {
        // GIVEN — a loaded task with categoryId = 1
        val task = Task(id = 10, title = "T", isDone = false, categoryId = 1)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)
        viewModel.loadTask(10)

        // WHEN — move the task to category 2
        viewModel.updateCategory(2)

        // THEN — the editor state reflects the new category synchronously
        viewModel.editor.test {
            val state = awaitItem()
            assertEquals(2, state.taskOrNull?.categoryId)
        }
    }

    @Test
    fun updateCategory_persistsTaskCopyWithNewCategoryId() = runTest {
        // GIVEN — a loaded task with categoryId = 1
        val task = Task(id = 10, title = "T", isDone = false, categoryId = 1)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)
        viewModel.loadTask(10)

        // WHEN — move the task to category 2
        viewModel.updateCategory(2)

        // THEN — the repository receives an upsert with the new categoryId,
        // keeping the rest of the task fields intact.
        coVerify {
            repository.upsertTask(match { t ->
                t.id == 10 && t.categoryId == 2 && t.title == "T"
            })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun autosave_usesLatestCategoryAfterCategoryChange() = runTest {
        val original = Task(
            id = 10,
            title = "Original",
            isDone = false,
            categoryId = Category.GENERAL_ID
        )
        coEvery { repository.getTaskById(any()) } returns original
        viewModel = TaskEditorViewModel(
            repository,
            toggleTaskUseCase,
            deleteTaskUseCase,
            reminderScheduler,
            categoryRepository,
            userPreferencesRepository
        )

        viewModel.loadTask(10)
        advanceUntilIdle()
        viewModel.updateCategory(2)
        advanceUntilIdle()

        // Simulate a later autosave after editing the title.
        updateTaskDraft(
            newTitle = "Edited",
            newDescription = "",
            newPriority = Priority.NOTHING,
            newDueDate = null,
            newReminderTime = null,
            newSubTasks = emptyList()
        )
        advanceUntilIdle()

        coVerify {
            repository.upsertTask(match {
                it.title == "Edited" && it.categoryId == 2
            })
        }
    }

    @Test
    fun updateCategory_toNull_movesTaskToGeneral() = runTest {
        // GIVEN — a loaded task that belongs to category 1
        val task = Task(id = 10, title = "T", isDone = false, categoryId = 1)
        coEvery { repository.getTaskById(any()) } returns task
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)
        viewModel.loadTask(10)

        // WHEN — move to General (null is mapped to GENERAL_ID by the ViewModel)
        viewModel.updateCategory(null)

        // THEN — repository receives the task with categoryId = Category.GENERAL_ID
        coVerify {
            repository.upsertTask(match { t ->
                t.id == 10 && t.categoryId == Category.GENERAL_ID
            })
        }
    }

    @Test
    fun updateCategory_whenNoTaskLoaded_doesNotCallRepository() = runTest {
        // GIVEN — fresh ViewModel, no task loaded yet
        viewModel = TaskEditorViewModel(repository, toggleTaskUseCase, deleteTaskUseCase, reminderScheduler, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.updateCategory(5)

        // THEN — the VM early-returns when state.taskOrNull == null, so no upsert
        coVerify(exactly = 0) { repository.upsertTask(any()) }
    }

    // ── Editor consistency tests ────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun categoryChange_followedImmediatelyByAutosave_neverPersistsPreviousCategory() = runTest {
        // GIVEN — A task loaded in category GENERAL_ID
        val original = Task(
            id = 10,
            title = "Original",
            isDone = false,
            categoryId = Category.GENERAL_ID
        )
        val persistedTasks = mutableListOf<Task>()
        coEvery { repository.getTaskById(any()) } returns original
        coEvery { repository.upsertTask(capture(persistedTasks)) } returns 10L
        viewModel = TaskEditorViewModel(
            repository,
            toggleTaskUseCase,
            deleteTaskUseCase,
            reminderScheduler,
            categoryRepository,
            userPreferencesRepository
        )
        viewModel.loadTask(10)
        advanceUntilIdle()

        // WHEN — Category changes to 2 and an autosave is triggered immediately
        viewModel.updateCategory(2)
        updateTaskDraft(
            newTitle = "Edited",
            newDescription = "",
            newPriority = Priority.NOTHING,
            newDueDate = null,
            newReminderTime = null,
            newSubTasks = emptyList()
        )
        advanceUntilIdle()

        // THEN — The state and all persisted copies use category 2
        assertEquals(2, viewModel.editor.value.taskOrNull?.categoryId)
        assertTrue(persistedTasks.isNotEmpty())
        assertTrue(persistedTasks.all { it.categoryId == 2 })
        assertTrue(persistedTasks.any { it.title == "Edited" })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun rapidCategoryChanges_latestCategoryWins() = runTest {
        // GIVEN — A loaded task in category 1
        val original = Task(id = 10, title = "Task", isDone = false, categoryId = 1)
        val persistedTasks = mutableListOf<Task>()
        coEvery { repository.getTaskById(any()) } returns original
        coEvery { repository.upsertTask(capture(persistedTasks)) } returns 10L
        viewModel = TaskEditorViewModel(
            repository,
            toggleTaskUseCase,
            deleteTaskUseCase,
            reminderScheduler,
            categoryRepository,
            userPreferencesRepository
        )
        viewModel.loadTask(10)
        advanceUntilIdle()

        // WHEN — Rapid consecutive category updates occur
        viewModel.updateCategory(2)
        viewModel.updateCategory(3)
        advanceUntilIdle()

        // THEN — Only the latest category snapshot (3) is persisted and held in state
        assertEquals(3, viewModel.editor.value.taskOrNull?.categoryId)
        assertTrue(persistedTasks.isNotEmpty())
        assertEquals(3, persistedTasks.last().categoryId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pastReminder_isNormalizedToNullInStateAndPersistence() = runTest {
        // GIVEN — A task with a future reminder loaded in the editor
        val futureReminder = System.currentTimeMillis() + 60_000L
        val original = Task(
            id = 10,
            title = "Task",
            isDone = false,
            reminderTime = futureReminder
        )
        coEvery { repository.getTaskById(any()) } returns original
        viewModel = TaskEditorViewModel(
            repository,
            toggleTaskUseCase,
            deleteTaskUseCase,
            reminderScheduler,
            categoryRepository,
            userPreferencesRepository
        )
        viewModel.loadTask(10)
        advanceUntilIdle()

        // WHEN — The reminder is updated to a time in the past
        viewModel.updateReminderTime(System.currentTimeMillis() - 1_000L)
        advanceUntilIdle()

        // THEN — The reminder is normalized to null in state and repository, and no alarm is scheduled
        assertNull(viewModel.editor.value.taskOrNull?.reminderTime)
        coVerify { repository.upsertTask(match { it.id == 10 && it.reminderTime == null }) }
        coVerify(exactly = 0) { reminderScheduler.schedule(any(), any(), any(), any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun toggleFollowedImmediatelyByAutosave_preservesCompletedState() = runTest {
        // GIVEN — A task loaded in the editor
        val original = Task(id = 10, title = "Task", isDone = false)
        val completed = original.copy(
            isDone = true,
            completedAt = 1_234L,
            reminderTime = null
        )
        val persistedTasks = mutableListOf<Task>()
        coEvery { repository.getTaskById(any()) } returns original
        coEvery { toggleTaskUseCase(original) } returns completed
        coEvery { repository.upsertTask(capture(persistedTasks)) } returns 10L
        viewModel = TaskEditorViewModel(
            repository,
            toggleTaskUseCase,
            deleteTaskUseCase,
            reminderScheduler,
            categoryRepository,
            userPreferencesRepository
        )
        viewModel.loadTask(10)
        advanceUntilIdle()

        // WHEN — The task is toggled done and immediately followed by an autosave update
        viewModel.toggleDone()
        updateTaskDraft(
            newTitle = "Edited after toggle",
            newDescription = "",
            newPriority = Priority.NOTHING,
            newDueDate = null,
            newReminderTime = null,
            newSubTasks = emptyList()
        )
        advanceUntilIdle()

        // THEN — The completed state is preserved in state and in the persisted updates
        assertEquals(true, viewModel.editor.value.taskOrNull?.isDone)
        assertTrue(persistedTasks.any { it.title == "Edited after toggle" && it.isDone })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun saveCompletingAfterNewEdit_doesNotReplaceNewerDraft() = runTest {
        val original = Task(id = 10, title = "Original", isDone = false)
        val allowSaveToFinish = CompletableDeferred<Unit>()
        coEvery { repository.getTaskById(10) } returns original
        coEvery { repository.upsertTask(match { it.title == "First edit" }) } coAnswers {
            allowSaveToFinish.await()
            10L
        }
        viewModel = TaskEditorViewModel(
            repository,
            toggleTaskUseCase,
            deleteTaskUseCase,
            reminderScheduler,
            categoryRepository,
            userPreferencesRepository
        )
        viewModel.loadTask(10)
        advanceUntilIdle()

        viewModel.updateTitle("First edit")
        viewModel.saveTask() // Suspends inside the repository.
        viewModel.updateTitle("Newer edit")
        allowSaveToFinish.complete(Unit)
        advanceUntilIdle()

        assertEquals("Newer edit", viewModel.editor.value.taskOrNull?.title)
    }
}