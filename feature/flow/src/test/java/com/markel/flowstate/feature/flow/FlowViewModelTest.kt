package com.markel.flowstate.feature.flow

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.core.domain.CategoryRepository
import com.markel.flowstate.core.domain.CheckList
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.CheckListRepository
import com.markel.flowstate.core.domain.IdeaRepository
import com.markel.flowstate.core.domain.SubTask
import com.markel.flowstate.core.domain.TaskRepository
import com.markel.flowstate.core.domain.usecase.tasks.DeleteTaskUseCase
import com.markel.flowstate.core.notifications.ReminderScheduler
import com.markel.flowstate.core.testing.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FlowViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val taskRepository: TaskRepository = mockk(relaxed = true)
    private val ideaRepository: IdeaRepository = mockk(relaxed = true)
    private val checkListRepository: CheckListRepository = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val reminderScheduler: ReminderScheduler = mockk(relaxed = true)
    private val deleteTaskUseCase: DeleteTaskUseCase = mockk(relaxed = true)

    private lateinit var viewModel: FlowViewModel

    /**
     * Default application scope for tests that don't need virtual time control.
     * Uses its own UnconfinedTestDispatcher so coroutines start eagerly up to
     * the first suspension point (delay), which is sufficient for UI-state checks.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val defaultTestApplicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    private fun createViewModel(
        tasks: List<Task> = emptyList(),
        ideas: List<Idea> = emptyList(),
        checkLists: List<CheckList> = emptyList(),
        categories: List<Category> = emptyList(),
        categoriesEnabled: Boolean = false,
        applicationScope: CoroutineScope = defaultTestApplicationScope
    ): FlowViewModel {
        coEvery { taskRepository.getTasks() } returns flowOf(tasks)
        coEvery { ideaRepository.getIdeas() } returns flowOf(ideas)
        coEvery { checkListRepository.getLists() } returns flowOf(checkLists)
        coEvery { categoryRepository.getCategories() } returns flowOf(categories)
        coEvery { userPreferencesRepository.categoriesEnabled } returns flowOf(categoriesEnabled)
        coEvery { userPreferencesRepository.lastCategoryId } returns flowOf(null)
        return FlowViewModel(
            taskRepository, ideaRepository, checkListRepository, categoryRepository,
            userPreferencesRepository, reminderScheduler, deleteTaskUseCase,
            applicationScope
        )
    }

    // ── Initialization & Combine logic ────────────────────────────────────────

    @Test
    fun uiState_combinesAllRepositories_andFiltersCompletedTasks() = runTest {
        // GIVEN
        val mockTasks = listOf(
            Task(id = 1, title = "Pending Task", isDone = false, position = 0),
            Task(id = 2, title = "Done Task", isDone = true, position = 1)
        )
        val mockIdeas = listOf(Idea(id = 1, title = "Idea 1", position = 0, content = "Content", color = 0L))
        val mockLists = listOf(CheckList(id = 1, title = "List 1", position = 0, color = 0L))

        // WHEN
        viewModel = createViewModel(tasks = mockTasks, ideas = mockIdeas, checkLists = mockLists)

        // THEN
        viewModel.uiState.test {
            // Skip initial Loading state if it appears
            val state = awaitItem()
            val successState = if (state is FlowUiState.Loading) awaitItem() as FlowUiState.Success else state as FlowUiState.Success

            // Verify tasks are filtered
            assertEquals(1, successState.tasks.size)
            assertEquals("Pending Task", successState.tasks[0].title)

            // Verify ideas and lists are received correctly
            assertEquals(1, successState.ideas.size)
            assertEquals(1, successState.checkLists.size)
        }
    }

    // ── Deferred Deletion / Undo ──────────────────────────────────────────────

    @Test
    fun onTaskSwiped_filtersTaskFromUiState_andShowsUndoButton() = runTest {
        // GIVEN
        val t1 = Task(id = 1, title = "T1", isDone = false, position = 0)
        val t2 = Task(id = 2, title = "T2", isDone = false, position = 1)
        viewModel = createViewModel(tasks = listOf(t1, t2))

        // Wait for initial state
        viewModel.uiState.test {
            val initial = awaitItem()
            val success = if (initial is FlowUiState.Loading) awaitItem() as FlowUiState.Success else initial as FlowUiState.Success
            assertEquals(2, success.tasks.size)

            // WHEN
            viewModel.onTaskSwiped(t1)

            // THEN – task 1 is filtered out optimistically
            val afterSwipe = awaitItem() as FlowUiState.Success
            assertEquals(1, afterSwipe.tasks.size)
            assertEquals("T2", afterSwipe.tasks[0].title)
        }

        // AND undo button is visible
        viewModel.showUndoButton.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun onTaskSwiped_bumpsDeleteVersion() = runTest {
        // GIVEN
        val task = Task(id = 5, title = "Task", isDone = false, position = 0)
        viewModel = createViewModel(tasks = listOf(task))

        // Initially no versions
        assertEquals(emptyMap<Int, Int>(), viewModel.taskDeleteVersions.value)

        // WHEN
        viewModel.onTaskSwiped(task)

        // THEN
        assertEquals(mapOf(5 to 1), viewModel.taskDeleteVersions.value)

        // Swipe the same task again (if it reappears after undo)
        viewModel.undoPendingDeletions()
        viewModel.onTaskSwiped(task)

        assertEquals(mapOf(5 to 2), viewModel.taskDeleteVersions.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onTaskSwiped_deletesFromDb_afterUndoTimeout() = runTest {
        // Use a scope that shares the runTest scheduler so advanceTimeBy
        // controls the delay inside the application-scoped coroutine
        val testApplicationScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        // GIVEN
        val task = Task(id = 1, title = "T", isDone = false, position = 0)
        viewModel = createViewModel(tasks = listOf(task), applicationScope = testApplicationScope)

        // WHEN
        viewModel.onTaskSwiped(task)

        // Advance time past the undo timeout (3.5 s)
        advanceTimeBy(4_000L)

        // THEN – task should be deleted from DB
        coVerify { reminderScheduler.cancel(1) }
        coVerify { deleteTaskUseCase(task) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun undoPendingDeletions_cancelsDeleteAndRestoresTasks() = runTest {
        // Use a scope that shares the runTest scheduler so advanceTimeBy
        // controls the delay inside the application-scoped coroutine
        val testApplicationScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

        // GIVEN
        val t1 = Task(id = 1, title = "T1", isDone = false, position = 0)
        val t2 = Task(id = 2, title = "T2", isDone = false, position = 1)
        viewModel = createViewModel(tasks = listOf(t1, t2), applicationScope = testApplicationScope)

        viewModel.uiState.test {
            // Get initial state
            val initial = awaitItem()
            val startState = if (initial is FlowUiState.Loading) awaitItem() as FlowUiState.Success else initial as FlowUiState.Success
            assertEquals(2, startState.tasks.size)

            // Swipe both tasks
            viewModel.onTaskSwiped(t1)
            awaitItem() // intermediate state after t1 swiped

            viewModel.onTaskSwiped(t2)
            val afterSwipes = awaitItem() as FlowUiState.Success
            assertTrue(afterSwipes.tasks.isEmpty())
        }

        // WHEN – undo
        viewModel.undoPendingDeletions()

        // THEN – tasks reappear in uiState
        viewModel.uiState.test {
            val state = awaitItem()
            val success = if (state is FlowUiState.Loading) awaitItem() as FlowUiState.Success else state as FlowUiState.Success
            assertEquals(2, success.tasks.size)
        }

        // AND undo button is hidden
        viewModel.showUndoButton.test {
            assertFalse(awaitItem())
        }

        // AND advancing time does NOT delete (jobs were canceled)
        advanceTimeBy(4_000L)
        coVerify(exactly = 0) { deleteTaskUseCase(any()) }
    }

    @Test
    fun onTaskSwiped_supportsConsecutiveDeletions() = runTest {
        // GIVEN
        val t1 = Task(id = 1, title = "T1", isDone = false, position = 0)
        val t2 = Task(id = 2, title = "T2", isDone = false, position = 1)
        val t3 = Task(id = 3, title = "T3", isDone = false, position = 2)
        viewModel = createViewModel(tasks = listOf(t1, t2, t3))

        viewModel.uiState.test {
            val initial = awaitItem()
            val success = if (initial is FlowUiState.Loading) awaitItem() as FlowUiState.Success else initial as FlowUiState.Success
            assertEquals(3, success.tasks.size)

            // WHEN – swipe 3 tasks in quick succession
            viewModel.onTaskSwiped(t1)
            val after1 = awaitItem() as FlowUiState.Success
            assertEquals(2, after1.tasks.size)

            viewModel.onTaskSwiped(t2)
            val after2 = awaitItem() as FlowUiState.Success
            assertEquals(1, after2.tasks.size)

            viewModel.onTaskSwiped(t3)
            val afterAll = awaitItem() as FlowUiState.Success

            // THEN – all tasks filtered out
            assertTrue(afterAll.tasks.isEmpty())
        }

        // Undo button visible
        viewModel.showUndoButton.test {
            assertTrue(awaitItem())
        }

        // Undo all
        viewModel.undoPendingDeletions()

        // All 3 tasks come back
        viewModel.uiState.test {
            val state = awaitItem()
            val success = if (state is FlowUiState.Loading) awaitItem() as FlowUiState.Success else state as FlowUiState.Success
            assertEquals(3, success.tasks.size)
        }
    }

    // ── Reorder logic ─────────────────────────────────────────────────────────
    //
    // onItemReorder() takes indices into the FLAT item list (headers included),
    // so every test below locates the row it wants instead of hardcoding an
    // offset. That is the whole point of the FlowItem model.

    @Test
    fun onItemReorder_withinTasks_reordersAndPersists() = runTest {
        // GIVEN
        val t1 = Task(id = 1, title = "T1", isDone = false, position = 0)
        val t2 = Task(id = 2, title = "T2", isDone = false, position = 1)
        val t3 = Task(id = 3, title = "T3", isDone = false, position = 2)

        viewModel = createViewModel(tasks = listOf(t1, t2, t3))

        viewModel.uiState.test {
            val before = awaitSuccess()

            // WHEN - drag the first task row down to the last task slot
            val from = before.indexOfTask(1)
            val to = before.items.indexOfLast { it is FlowItem.TaskRow }
            viewModel.onItemReorder(fromIndex = from, toIndex = to)

            // THEN
            val after = awaitItem() as FlowUiState.Success
            assertEquals(listOf("T2", "T3", "T1"), after.tasks.map { it.title })
            // Positions are renumbered so the DAOs (ORDER BY position) keep the order
            assertEquals(listOf(0, 1, 2), after.tasks.map { it.position })
        }

        coVerify {
            taskRepository.updateTasksOrder(match { list -> list.map { it.id } == listOf(2, 3, 1) })
        }
    }

    @Test
    fun onItemReorder_withinIdeas_reordersAndPersists() = runTest {
        // GIVEN
        val i1 = Idea(id = 1, title = "I1", position = 0, content = "Content", color = 0L)
        val i2 = Idea(id = 2, title = "I2", position = 1, content = "Content", color = 0L)

        viewModel = createViewModel(ideas = listOf(i1, i2))

        viewModel.uiState.test {
            val before = awaitSuccess()
            // WHEN - swap the two idea cards
            viewModel.onItemReorder(
                fromIndex = before.indexOfIdea(1),
                toIndex = before.indexOfIdea(2),
            )

            // THEN
            val after = awaitItem() as FlowUiState.Success
            assertEquals(listOf("I2", "I1"), after.ideas.map { it.title })
            assertEquals(listOf(0, 1), after.ideas.map { it.position })
        }

        coVerify {
            ideaRepository.updateIdeasOrder(match { list -> list.map { it.id } == listOf(2, 1) })
        }
    }

    @Test
    fun onItemReorder_withinCheckLists_reordersAndPersists() = runTest {
        // GIVEN
        val c1 = CheckList(id = 1, title = "C1", position = 0, color = 0L)
        val c2 = CheckList(id = 2, title = "C2", position = 1, color = 0L)

        viewModel = createViewModel(checkLists = listOf(c1, c2))

        viewModel.uiState.test {
            val before = awaitSuccess()
            // WHEN - drag C2 in front of C1
            viewModel.onItemReorder(
                fromIndex = before.indexOfCheckList(2),
                toIndex = before.indexOfCheckList(1),
            )

            // THEN
            val after = awaitItem() as FlowUiState.Success
            assertEquals(listOf("C2", "C1"), after.checkLists.map { it.title })
            assertEquals(listOf(0, 1), after.checkLists.map { it.position })
        }

        coVerify {
            checkListRepository.updateCheckListsOrder(match { list -> list.map { it.id } == listOf(2, 1) })
        }
    }

    @Test
    fun onItemReorder_acrossSections_isRejected() = runTest {
        // GIVEN - both sections populated
        val idea = Idea(id = 1, title = "I1", position = 0, content = "Content", color = 0L)
        val list = CheckList(id = 2, title = "C1", position = 0, color = 0L)

        viewModel = createViewModel(ideas = listOf(idea), checkLists = listOf(list))

        viewModel.uiState.test {
            val before = awaitSuccess()
            cancelAndIgnoreRemainingEvents()

            // WHEN - try to drop the idea card onto the checklist card
            viewModel.onItemReorder(
                fromIndex = before.indexOfIdea(1),
                toIndex = before.indexOfCheckList(2),
            )

            // THEN - nothing moved and nothing was written
            val after = viewModel.uiState.value as FlowUiState.Success
            assertEquals(before.ideas, after.ideas)
            assertEquals(before.checkLists, after.checkLists)
        }

        coVerify(exactly = 0) { ideaRepository.updateIdeasOrder(any()) }
        coVerify(exactly = 0) { checkListRepository.updateCheckListsOrder(any()) }
    }

    @Test
    fun onItemReorder_ontoAHeader_isRejected() = runTest {
        // GIVEN
        val i1 = Idea(id = 1, title = "I1", position = 0, content = "Content", color = 0L)

        viewModel = createViewModel(ideas = listOf(i1))

        viewModel.uiState.test {
            val before = awaitSuccess()
            cancelAndIgnoreRemainingEvents()

            // WHEN - drop the idea card onto its own section header
            viewModel.onItemReorder(
                fromIndex = before.indexOfIdea(1),
                toIndex = before.items.indexOf(FlowItem.IdeasHeader),
            )

            // THEN
            val after = viewModel.uiState.value as FlowUiState.Success
            assertEquals(before.ideas, after.ideas)
        }

        coVerify(exactly = 0) { ideaRepository.updateIdeasOrder(any()) }
    }

    @Test
    fun onItemReorder_withOutOfBoundsIndices_isIgnored() = runTest {
        // GIVEN
        val i1 = Idea(id = 1, title = "I1", position = 0, content = "Content", color = 0L)

        viewModel = createViewModel(ideas = listOf(i1))

        viewModel.uiState.test {
            val before = awaitSuccess()
            cancelAndIgnoreRemainingEvents()

            // WHEN
            viewModel.onItemReorder(fromIndex = -1, toIndex = 99)
            viewModel.onItemReorder(fromIndex = 0, toIndex = 99)

            // THEN
            val after = viewModel.uiState.value as FlowUiState.Success
            assertEquals(before.ideas, after.ideas)
        }

        coVerify(exactly = 0) { ideaRepository.updateIdeasOrder(any()) }
    }

    // ── Banner logic ──────────────────────────────────────────────────────────

    @Test
    fun banner_is_hidden_when_permission_is_granted() = runTest {
        coEvery { reminderScheduler.canScheduleExactAlarms() } returns true
        val futureTime = System.currentTimeMillis() + 60_000L
        val task = Task(id = 1, title = "T", isDone = false, reminderTime = futureTime)

        viewModel = createViewModel(tasks = listOf(task))

        viewModel.showReminderBanner.test {
            assertFalse(awaitItem())
        }
    }

    @Test
    fun banner_is_shown_when_permission_missing_and_tasks_have_future_reminders() = runTest {
        coEvery { reminderScheduler.canScheduleExactAlarms() } returns false
        val futureTime = System.currentTimeMillis() + 60_000L
        val task = Task(id = 1, title = "T", isDone = false, reminderTime = futureTime)

        viewModel = createViewModel(tasks = listOf(task))

        viewModel.showReminderBanner.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun banner_is_hidden_when_permission_missing_but_no_future_reminders() = runTest {
        coEvery { reminderScheduler.canScheduleExactAlarms() } returns false
        val task = Task(id = 1, title = "T", isDone = false, reminderTime = null)

        viewModel = createViewModel(tasks = listOf(task))

        viewModel.showReminderBanner.test {
            assertFalse(awaitItem())
        }
    }

    @Test
    fun banner_is_shown_when_subtask_has_future_reminder_and_permission_missing() = runTest {
        coEvery { reminderScheduler.canScheduleExactAlarms() } returns false
        val futureTime = System.currentTimeMillis() + 60_000L
        val subTask = SubTask(id = "s1", title = "Sub", reminderTime = futureTime)
        val task = Task(id = 1, title = "T", isDone = false, subTasks = listOf(subTask))

        viewModel = createViewModel(tasks = listOf(task))

        viewModel.showReminderBanner.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun banner_is_hidden_when_only_past_reminders_exist_and_permission_missing() = runTest {
        coEvery { reminderScheduler.canScheduleExactAlarms() } returns false
        val pastTime = System.currentTimeMillis() - 60_000L
        val task = Task(id = 1, title = "T", isDone = false, reminderTime = pastTime)

        viewModel = createViewModel(tasks = listOf(task))

        viewModel.showReminderBanner.test {
            assertFalse(awaitItem())
        }
    }

    // ── onResume: permission grant triggers reschedule ───────────────────────

    @Test
    fun onResume_reschedules_alarms_when_permission_was_just_granted() = runTest {
        // First call: permission missing → sets wasPermissionMissing = true
        // Second call: permission granted → triggers reschedule
        coEvery { reminderScheduler.canScheduleExactAlarms() } returns false andThen true

        val futureTime = System.currentTimeMillis() + 60_000L
        val task = Task(id = 1, title = "T", isDone = false, reminderTime = futureTime)

        viewModel = createViewModel(tasks = listOf(task))

        // First onResume: sets wasPermissionMissing = true (permission still missing)
        viewModel.onResume(mockk())

        // Second onResume: permission now granted → should reschedule
        viewModel.onResume(mockk())

        // Verify rescheduleAll was called
        coVerify { reminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun onResume_does_not_reschedule_when_permission_was_already_granted() = runTest {
        coEvery { reminderScheduler.canScheduleExactAlarms() } returns true

        viewModel = createViewModel()

        viewModel.onResume(mockk())
        viewModel.onResume(mockk())

        // rescheduleAll should never be called since permission was never missing
        coVerify(exactly = 0) { reminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun onResume_does_not_reschedule_when_permission_is_still_missing() = runTest {
        coEvery { reminderScheduler.canScheduleExactAlarms() } returns false

        viewModel = createViewModel()

        viewModel.onResume(mockk())
        viewModel.onResume(mockk())

        // Permission never granted, so no reschedule
        coVerify(exactly = 0) { reminderScheduler.rescheduleAll(any()) }
    }


    // ── Category actions: createCategory ──────────────────────────────────────

    //
    // Note: createCategory now delegates to CategoryRepository.createCategory,
    // which is the single source of truth for the "max position + 1" logic
    // and the blank/reserved-name validation. These tests verify the VM
    // delegation; the validation logic itself is tested at the repository
    // level (see CategoryRepositoryImplTest).

    @Test
    fun createCategory_withValidName_delegatesToRepository() = runTest {
        viewModel = createViewModel()

        viewModel.createCategory("New list")

        coVerify { categoryRepository.createCategory("New list") }
    }

    @Test
    fun createCategory_withBlankName_stillDelegatesToRepository() = runTest {
        // The VM no longer pre-validates; the repository rejects blank names.
        viewModel = createViewModel()

        viewModel.createCategory("   ")

        coVerify { categoryRepository.createCategory("   ") }
    }
    @Test
    fun createCategory_withReservedNameGeneral_stillDelegatesToRepository() = runTest {
        // The VM no longer pre-validates; the repository rejects "General".
        viewModel = createViewModel()
        viewModel.createCategory("General")
        viewModel.createCategory("GENERAL")
        viewModel.createCategory("general")

        coVerify(exactly = 3) { categoryRepository.createCategory(any()) }
    }

    // ── Category actions: reorderCategories ───────────────────────────────────

    @Test
    fun reorderCategories_delegatesToRepositoryWithNewOrder() = runTest {
        // GIVEN — categories already stored in DB with old positions
        val stored = listOf(
            Category(id = 1, name = "A", position = 5),
            Category(id = 2, name = "B", position = 7),
            Category(id = 3, name = "C", position = 9)
        )
        viewModel = createViewModel(categories = stored)

        // The user reorders to [B, C, A]
        val reordered = listOf(stored[1], stored[2], stored[0])
        viewModel.reorderCategories(reordered)

        coVerify { categoryRepository.reorderCategories(reordered) }
    }

    // ── Category selection & filtering ─────────────────────────────────────────

    @Test
    fun selectCategory_filtersTasksIdeasAndChecklistsByCategoryId() = runTest {
        // GIVEN — categories enabled, mixed items across two categories
        val cat1 = 10
        val cat2 = 20
        val tasks = listOf(
            Task(id = 1, title = "T1", isDone = false, categoryId = cat1),
            Task(id = 2, title = "T2", isDone = false, categoryId = cat2),
            Task(id = 3, title = "T3", isDone = false, categoryId = Category.GENERAL_ID) // General
        )
        val ideas = listOf(
            Idea(id = 1, title = "I1", content = "", color = 0L, categoryId = cat1),
            Idea(id = 2, title = "I2", content = "", color = 0L, categoryId = cat2)
        )
        val lists = listOf(
            CheckList(id = 1, title = "C1", color = 0L, categoryId = cat1),
            CheckList(id = 2, title = "C2", color = 0L, categoryId = cat2)
        )
        val categories = listOf(
            Category(id = cat1, name = "Work", position = 0),
            Category(id = cat2, name = "Personal", position = 1)
        )
        viewModel = createViewModel(
            tasks = tasks,
            ideas = ideas,
            checkLists = lists,
            categories = categories,
            categoriesEnabled = true
        )

        // WHEN — select cat1
        viewModel.selectCategory(cat1)

        // THEN — only items in cat1 are emitted. We scan emissions until we find
        // the one reflecting the selection (the combine may emit intermediate
        // states while the StateFlow flips).
        viewModel.uiState.test {
            var state: FlowUiState.Success? = null
            do {
                val item = awaitItem()
                if (item is FlowUiState.Success) state = item
            } while (state == null || state.selectedCategoryId != cat1)

            assertEquals(1, state.tasks.size)
            assertEquals("T1", state.tasks[0].title)
            assertEquals(1, state.ideas.size)
            assertEquals(1, state.checkLists.size)
            assertEquals(cat1, state.selectedCategoryId)
        }
    }

    @Test
    fun selectCategory_general_showsOnlyGeneralTasks() = runTest {
        // GIVEN — categories enabled, tasks in a category AND in General.
        //
        // The "General" tab (selectedCategoryId == Category.GENERAL_ID) shows ONLY
        // tasks whose categoryId == Category.GENERAL_ID. It is NOT a "see everything"
        // view anymore (that was the old model).
        val cat1 = 10
        val tasks = listOf(
            Task(id = 1, title = "Categorized A", isDone = false, categoryId = cat1),
            Task(id = 2, title = "Categorized B", isDone = false, categoryId = cat1),
            Task(id = 3, title = "General A", isDone = false, categoryId = Category.GENERAL_ID),
            Task(id = 4, title = "General B", isDone = false, categoryId = Category.GENERAL_ID)
        )
        val categories = listOf(Category(id = cat1, name = "Work", position = 0))
        viewModel = createViewModel(
            tasks = tasks,
            categories = categories,
            categoriesEnabled = true
        )

        // First select cat1 so the next selection (General) actually changes state
        viewModel.selectCategory(cat1)

        // WHEN — select General
        viewModel.selectCategory(Category.GENERAL_ID)

        // THEN — only the General tasks are shown
        viewModel.uiState.test {
            var state: FlowUiState.Success? = null
            do {
                val item = awaitItem()
                if (item is FlowUiState.Success) state = item
            } while (state == null || state.selectedCategoryId != Category.GENERAL_ID)

            assertEquals(2, state.tasks.size)
            assertTrue(state.tasks.all { it.categoryId == Category.GENERAL_ID })
            assertEquals(Category.GENERAL_ID, state.selectedCategoryId)
        }
    }

    // ── Remember last visited category ─────────────────────────────────────────

    @Test
    fun selectCategory_persistsTheChoiceToDataStore() = runTest {
        viewModel = createViewModel(categoriesEnabled = true)

        viewModel.selectCategory(42)

        coVerify { userPreferencesRepository.saveLastCategoryId(42) }
    }

    @Test
    fun selectCategory_null_normalizesToGeneralBeforePersisting() = runTest {
        viewModel = createViewModel(categoriesEnabled = true)

        viewModel.selectCategory(null)

        // null → GENERAL_ID before persisting, so we never store null.
        coVerify { userPreferencesRepository.saveLastCategoryId(Category.GENERAL_ID) }
    }

    @Test
    fun init_restoresLastVisitedCategoryFromDataStore() = runTest {
        val cat1 = 10
        val categories = listOf(Category(id = cat1, name = "Work", position = 0))
        coEvery { taskRepository.getTasks() } returns flowOf(emptyList())
        coEvery { ideaRepository.getIdeas() } returns flowOf(emptyList())
        coEvery { checkListRepository.getLists() } returns flowOf(emptyList())
        coEvery { categoryRepository.getCategories() } returns flowOf(categories)
        coEvery { userPreferencesRepository.categoriesEnabled } returns flowOf(true)
        // The persisted last visited category is cat1.
        coEvery { userPreferencesRepository.lastCategoryId } returns flowOf(cat1)

        viewModel = FlowViewModel(
            taskRepository, ideaRepository, checkListRepository, categoryRepository,
            userPreferencesRepository, reminderScheduler, deleteTaskUseCase,
            defaultTestApplicationScope
        )

        viewModel.uiState.test {
            var state: FlowUiState.Success? = null
            do {
                val item = awaitItem()
                if (item is FlowUiState.Success) state = item
            } while (state == null || state.selectedCategoryId != cat1)

            assertEquals(cat1, state.selectedCategoryId)
        }
    }

    @Test
    fun selectedCategory_fallsBackToGeneralWhenItNoLongerExists() = runTest {
        // GIVEN — the persisted last category (99) does not exist in the DB.
        // The combine must reset to General and persist the correction.
        coEvery { taskRepository.getTasks() } returns flowOf(emptyList())
        coEvery { ideaRepository.getIdeas() } returns flowOf(emptyList())
        coEvery { checkListRepository.getLists() } returns flowOf(emptyList())
        coEvery { categoryRepository.getCategories() } returns flowOf(
            listOf(Category(id = Category.GENERAL_ID, name = "General", position = 0))
        )
        coEvery { userPreferencesRepository.categoriesEnabled } returns flowOf(true)
        coEvery { userPreferencesRepository.lastCategoryId } returns flowOf(99)

        viewModel = FlowViewModel(
            taskRepository, ideaRepository, checkListRepository, categoryRepository,
            userPreferencesRepository, reminderScheduler, deleteTaskUseCase,
            defaultTestApplicationScope
        )

        viewModel.uiState.test {
            var state: FlowUiState.Success? = null
            do {
                val item = awaitItem()
                if (item is FlowUiState.Success) state = item
            } while (state == null || state.selectedCategoryId != Category.GENERAL_ID)

            assertEquals(Category.GENERAL_ID, state.selectedCategoryId)
        }
        // The correction is persisted so we don't keep pointing at the phantom id.
        coVerify { userPreferencesRepository.saveLastCategoryId(Category.GENERAL_ID) }
    }

    // ── pendingTaskCounts (badge counts) ───────────────────────────────────────

    @Test
    fun pendingTaskCounts_groupsPendingTasksByCategory() = runTest {
        // GIVEN — categories enabled, several pending tasks across two categories
        val cat1 = 10
        val cat2 = 20
        val tasks = listOf(
            Task(id = 1, title = "T1", isDone = false, categoryId = cat1),
            Task(id = 2, title = "T2", isDone = false, categoryId = cat1),
            Task(id = 3, title = "T3", isDone = false, categoryId = cat2),
            Task(id = 4, title = "T4", isDone = true,  categoryId = cat1), // done → excluded
            Task(id = 5, title = "T5", isDone = false, categoryId = Category.GENERAL_ID)  // General
        )
        val categories = listOf(
            Category(id = cat1, name = "Work", position = 0),
            Category(id = cat2, name = "Personal", position = 1)
        )
        viewModel = createViewModel(
            tasks = tasks,
            categories = categories,
            categoriesEnabled = true
        )

        // THEN — counts include pending tasks per category AND General (GENERAL_ID)
        viewModel.uiState.test {
            val state = (awaitItem() as? FlowUiState.Success) ?: awaitItem() as FlowUiState.Success
            assertEquals(2, state.pendingTaskCounts[cat1]) // T1 + T2 (T4 is done)
            assertEquals(1, state.pendingTaskCounts[cat2]) // T3
            // General (GENERAL_ID) counts tasks in the General category — T5 here.
            assertEquals(1, state.pendingTaskCounts[Category.GENERAL_ID])
        }
    }

    @Test
    fun pendingTaskCounts_excludesTasksPendingUndoDeletion() = runTest {
        // GIVEN — a task that has been swiped-to-delete (pending undo) must NOT
        // be counted in the badge, because it's no longer visible in the list.
        val cat1 = 10
        val t1 = Task(id = 1, title = "T1", isDone = false, categoryId = cat1)
        val t2 = Task(id = 2, title = "T2", isDone = false, categoryId = cat1)
        val categories = listOf(Category(id = cat1, name = "Work", position = 0))
        viewModel = createViewModel(
            tasks = listOf(t1, t2),
            categories = categories,
            categoriesEnabled = true
        )

        // WHEN — swipe t1 to delete
        viewModel.onTaskSwiped(t1)

        // THEN — only t2 is counted. Scan emissions until we see the count
        // reflecting the swipe (the combine may emit intermediate states).
        viewModel.uiState.test {
            var state: FlowUiState.Success? = null
            do {
                val item = awaitItem()
                if (item is FlowUiState.Success) state = item
            } while (state == null || state.pendingTaskCounts[cat1] != 1)

            assertEquals(1, state.pendingTaskCounts[cat1])
        }
    }

    @Test
    fun pendingTaskCounts_isComputedEvenWhenCategoriesAreDisabled() = runTest {
        // The pendingTaskCounts map is always computed by the combine block
        // (it only filters by isDone / pendingUndo), so
        // it is populated even when categories are disabled. The UI layer is
        // responsible for ignoring the counts when categoriesEnabled == false
        // (no tabs → no badges to render).
        val cat1 = 10
        val tasks = listOf(Task(id = 1, title = "T1", isDone = false, categoryId = cat1))
        viewModel = createViewModel(tasks = tasks, categoriesEnabled = false)

        viewModel.uiState.test {
            val state = (awaitItem() as? FlowUiState.Success) ?: awaitItem() as FlowUiState.Success
            assertEquals(1, state.pendingTaskCounts[cat1])
        }
    }


    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Consumes the initial `Loading` emission when there is one. */
    private suspend fun ReceiveTurbine<FlowUiState>.awaitSuccess(): FlowUiState.Success {
        val first = awaitItem()
        return if (first is FlowUiState.Success) first else awaitItem() as FlowUiState.Success
    }

    // Locators over the FLAT item list: the reorder API speaks in item indices,
    // not in per-section indices, so tests must not assume a header offset.
    private fun FlowUiState.Success.indexOfTask(id: Int) =
        items.indexOfFirst { it is FlowItem.TaskRow && it.task.id == id }

    private fun FlowUiState.Success.indexOfIdea(id: Int) =
        items.indexOfFirst { it is FlowItem.IdeaCard && it.idea.id == id }

    private fun FlowUiState.Success.indexOfCheckList(id: Int) =
        items.indexOfFirst { it is FlowItem.CheckListCard && it.checkList.id == id }
}
