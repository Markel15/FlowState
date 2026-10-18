package com.markel.flowstate.feature.flow

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.core.domain.CategoryRepository
import com.markel.flowstate.core.domain.CheckList
import com.markel.flowstate.core.domain.CheckListRepository
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.core.domain.IdeaRepository
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskRepository
import com.markel.flowstate.core.domain.usecase.tasks.DeleteTaskUseCase
import com.markel.flowstate.core.notifications.ReminderScheduler
import com.markel.flowstate.core.notifications.buildAlarmItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FlowScreen coordinator.
 *
 * Responsibilities:
 *  - Control cross-screen UI state (isGridView)
 *  - Combine Tasks, Ideas and CheckLists into a sectioned UiState
 *  - Optimistic reordering per section with DB persistence on drag end
 *
 * Future extensibility note:
 *  Cross-section reordering (e.g. Task → Idea conversion) can be added by
 *  introducing a shared reorder method that mutates multiple local state flows
 *  and calls the corresponding repository operations in a single transaction.
*/
@HiltViewModel
class FlowViewModel @Inject constructor(
    // Repositories required for the unified grid
    private val taskRepository: TaskRepository,
    private val ideaRepository: IdeaRepository,
    private val checkListRepository: CheckListRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val applicationScope: CoroutineScope

) : ViewModel(), DefaultLifecycleObserver {

    // ── Optimistic local state ─────────────────────────────────
    private val _uiState = MutableStateFlow<FlowUiState>(FlowUiState.Loading)
    val uiState: StateFlow<FlowUiState> = _uiState

    // ── Banner ────────────────────────────────────────────────────────────────

    // Backed by a MutableStateFlow so we can force a re-evaluation on resume without waiting for a DB emission
    private val _bannerVisible = MutableStateFlow(false)
    val showReminderBanner: StateFlow<Boolean> = _bannerVisible

    // Tracks whether the permission was missing the last time we checked
    // When it flips from false → true (user just granted it), we reschedule
    private var wasPermissionMissing = false

    // ── Deferred deletion / Undo ──────────────────────────────────────────────

    /** Tasks that have been swiped-to-dismiss but not yet permanently deleted. */
    private val _pendingUndoTasks = MutableStateFlow<Map<Int, Task>>(emptyMap())

    /**
     * Ids of tasks whose undo countdown expired and were deleted from the DB.
     *
     * They keep being filtered out of the UI (together with [_pendingUndoTasks])
     * until a DB emission confirms the task is really gone; the combine below
     * then drops the id (self-healing).
     *
     */
    private val _committedDeleteIds = MutableStateFlow<Set<Int>>(emptySet())

    /** Whether the undo FAB should be visible – true while any deletion is pending. */
    val showUndoButton: StateFlow<Boolean> = _pendingUndoTasks
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Per-task monotonic counter incremented each time the user swipes-to-delete.
     * Used as part of the `LazyColumn` item key so that, after an undo, Compose
     * creates a **fresh** composable with a fresh `SwipeToDismissBoxState` instead
     * of restoring the stale `EndToStart` saved-state that would immediately
     * trigger `onDelete()` again.
     */
    private val _taskDeleteVersions = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val taskDeleteVersions: StateFlow<Map<Int, Int>> = _taskDeleteVersions

    /**
     * One [Job] per pending task, representing the 3.5 s countdown to permanent
     * deletion.  When the job completes the task is removed from the DB; when the
     * user taps undo the job is canceled.
     */
    private val deleteJobs = mutableMapOf<Int, Job>()

    private val _selectedCategoryId = MutableStateFlow<Int?>(Category.GENERAL_ID)
    val generalCategoryName: StateFlow<String?> = userPreferencesRepository.generalCategoryName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Tracks whether we have already applied the persisted "last visited
     * category" from DataStore. We only want to apply it ONCE at startup —
     * after that, the user's in-session selections take over, and we just
     * persist them as they happen.
     */
    private var lastCategoryRestored = false

    // ── Init: combine repos + pending-filter ──────────────────────────────────
    init {
        // Restore the last visited category DataStory free once at startup.
        viewModelScope.launch {
            userPreferencesRepository.lastCategoryId.collect { saved ->
                if (!lastCategoryRestored && saved != null) {
                    _selectedCategoryId.value = saved
                    lastCategoryRestored = true
                }
            }
        }

        viewModelScope.launch {
            val coreDataFlow = combine(
                taskRepository.getTasks(),
                ideaRepository.getIdeas(),
                checkListRepository.getLists(),
                categoryRepository.getCategories()
            ) { tasks, ideas, lists, categories ->
                // Combine these into a temporary data class
                CoreData(tasks, ideas, lists, categories)
            }
            // Combine like this to not lose type-safe navigation
            combine(
                coreDataFlow,
                userPreferencesRepository.categoriesEnabled,
                _pendingUndoTasks,
                _committedDeleteIds,
                _selectedCategoryId
            ) { coreData, categoriesEnabled, pendingMap, committedIds, selectedCategoryId ->

                // Ids hidden from the list: still-undoable deletions plus the
                // committed ones whose absence the DB has not confirmed yet.
                val pendingIds = pendingMap.keys + committedIds

                // Self-heal committed deletions: as soon as this emission's task
                // list no longer contains the id, it can be dropped.
                val dbTaskIds = coreData.tasks.mapTo(HashSet()) { it.id }
                val confirmedGone = committedIds.filterNot { it in dbTaskIds }
                if (confirmedGone.isNotEmpty()) {
                    _committedDeleteIds.update { it - confirmedGone.toSet() }
                }

                // If the persisted/selected category no longer exists, fall back to General
                // and persist the correction so we don't keep pointing at a phantom id.
                val validSelectedId = if (categoriesEnabled && selectedCategoryId != null) {
                    val exists = coreData.categories.any { it.id == selectedCategoryId }
                    if (!exists) {
                        Category.GENERAL_ID.also {
                            viewModelScope.launch {
                                userPreferencesRepository.saveLastCategoryId(it)
                            }
                        }
                    } else {
                        selectedCategoryId
                    }
                } else {
                    selectedCategoryId
                }
                // Keep the local StateFlow in sync with the correction so the
                // tab row indicator lands on General too.
                if (validSelectedId != selectedCategoryId) {
                    _selectedCategoryId.value = validSelectedId
                }

                // Filter tasks: exclude done + pending deletions, then filter by category if enabled
                val filteredTasks = coreData.tasks
                    .filter { !it.isDone && it.id !in pendingIds }
                    .let { filtered ->
                        if (categoriesEnabled) {
                            filtered.filter { it.categoryId == validSelectedId }
                        } else filtered
                    }

                // Filter ideas by category if enabled
                val filteredIdeas = coreData.ideas.let { list ->
                    if (categoriesEnabled) {
                        list.filter { it.categoryId == validSelectedId }
                    } else list
                }

                // Filter checklists by category if enabled
                val filteredLists = coreData.lists.let { list ->
                    if (categoriesEnabled) {
                        list.filter { it.categoryId == selectedCategoryId }
                    } else list
                }

                // When categories are enabled but no category is selected, default to General (GENERAL_ID).
                // When categories are disabled, the selectedCategoryId is irrelevant (no tab row is shown),
                // but we still report GENERAL_ID so any consumer that reads it gets a valid id.
                val effectiveSelectedId = if (categoriesEnabled) validSelectedId else Category.GENERAL_ID

                // Per-category pending task counts for the tab badges.
                val pendingTaskCounts = coreData.tasks
                    .asSequence()
                    .filter { !it.isDone && it.id !in pendingIds}
                    .groupingBy { it.categoryId }
                    .eachCount()

                FlowUiState.Success(
                    tasks = filteredTasks,
                    ideas = filteredIdeas,
                    checkLists = filteredLists,
                    categories = coreData.categories,
                    selectedCategoryId = effectiveSelectedId,
                    categoriesEnabled = categoriesEnabled,
                    pendingTaskCounts = pendingTaskCounts
                )
            }.collect { state ->
                _uiState.value = state
                refreshBanner()
            }
        }
    }

    // ── Category actions ──────────────────────────────────────────────────────

    /**
     * Creates a new user category from the FlowScreen "+ New category" tab.
     * Delegates to [CategoryRepository.createCategory], which is the single
     * source of truth for the "max position + 1" logic and the reserved-name
     * guard. The new category is NOT selected automatically.
     */
    fun createCategory(name: String) {
        viewModelScope.launch {
            categoryRepository.createCategory(name)
        }
    }

    fun selectCategory(id: Int?) {
        // null is treated as General — normalize so we never persist null.
        val effective = id ?: Category.GENERAL_ID
        _selectedCategoryId.value = effective
        viewModelScope.launch {
            userPreferencesRepository.saveLastCategoryId(effective)
        }
    }

    /**
     * Persists a new order for the user categories. Mirrors the logic in
     * settings:
     * Persists a new order for the categories.
     *
     * Delegates to [CategoryRepository.reorderCategories], which is the single
     * source of truth for the `mapIndexed` re-positioning logic.
     *
     * Called from the FlowScreen "Reorder categories" sheet.
     */
    fun reorderCategories(categories: List<Category>) {
        viewModelScope.launch {
            categoryRepository.reorderCategories(categories)
        }
    }

    // ── Deferred deletion API ─────────────────────────────────────────────────

    /**
     * Called when the user swipes a task to dismiss it.
     *
     * The task is **not** deleted from the DB immediately. Instead, it is added
     * to [_pendingUndoTasks] which causes the `combine` above to filter it out
     * of the UI state optimistically.  A per-task countdown of 3500ms
     * starts; if it expires the task is permanently deleted and its id moves to
     * [_committedDeleteIds], which keeps filtering it until the DB emission
     * confirms the deletion.
     * The user may cancel the countdown by pressing the undo FAB
     * ([undoPendingDeletions]).
     */
    fun onTaskSwiped(task: Task) {
        // Add to the pending map – the combine will filter it out of uiState
        _pendingUndoTasks.update { it + (task.id to task) }

        // Bump the delete version so the LazyColumn key changes.
        // This prevents rememberSaveable from restoring the old
        // SwipeToDismissBoxState (EndToStart) when the task is
        // un-filtered after an undo.
        _taskDeleteVersions.update { it + (task.id to (it[task.id] ?: 0) + 1) }

        // Cancel any previous countdown for this same task (defensive)
        deleteJobs[task.id]?.cancel()

        // Start the countdown to permanent deletion
        deleteJobs[task.id] = applicationScope.launch {
            delay(3500L)
            // Time's up – permanently delete from DB
            reminderScheduler.cancel(task.id)
            deleteTaskUseCase(task)
            _committedDeleteIds.update { it + task.id }
            _pendingUndoTasks.update { it - task.id }
            deleteJobs.remove(task.id)
        }
    }

    /**
     * Called when the user taps the undo FAB.
     *
     * Cancels **all** pending deletion countdowns and restores every task
     * that was waiting to be deleted.  The tasks are still in the DB, so
     * simply removing them from [_pendingUndoTasks] makes them reappear in
     * the UI state automatically.
     *
     * Deletions whose countdown already expired (ids in [_committedDeleteIds])
     * are **not** restored — they are gone from the DB and no longer undoable;
     * their ids stay in the filter set until the combine self-heals them.
     */
    fun undoPendingDeletions() {
        // Cancel every pending countdown
        deleteJobs.values.forEach { it.cancel() }
        deleteJobs.clear()
        // Clear the pending map – tasks reappear in uiState via the combine
        _pendingUndoTasks.update { emptyMap() }
    }

    // ── Lifecycle: called every time the app comes to the foreground ──────────

    override fun onResume(owner: LifecycleOwner) {
        val permissionNowGranted = reminderScheduler.canScheduleExactAlarms()

        if (wasPermissionMissing && permissionNowGranted) {
            // Permission was just granted — reschedule everything that was pending
            viewModelScope.launch {
                val items = buildAlarmItems(taskRepository)
                reminderScheduler.rescheduleAll(items)

            }
        }
        refreshBanner()
    }

    // ── Reorder ────────────────────────────────────────────────────────

    /**
     * Sections whose order changed since the last [commitReorder].
     *
     * A drag is a burst of [onItemReorder] calls, one per slot crossed, and each
     * write renumbers and persists the *whole* section. Holding the write back
     * until the finger lifts turns that burst into a single write.
     */
    private val pendingReorderSections = mutableSetOf<FlowItem.Section>()

    /**
     * Applies a drag between two rows of the flat [FlowItem] list.
     *
     * The move is only applied when both rows belong to the same non-null
     * [FlowItem.Section]: headers and the reminder banner are fixed chrome, and
     * a card may not jump between different Items sections.
     *
     * This guard is a data-safety net, **not** the mechanism that keeps the drag
     * inside its section. `reorderable` gets no answer back from `onMove`, so a
     * move rejected here leaves the library compensating the dragged row against
     * a slot that never materializes (the row visibly jumps and then follows the
     * finger from the wrong anchor). `SectionedFlowView` prevents that by
     * disabling foreign rows as swap targets for the duration of the drag; a
     * rejected call here should therefore be unreachable, and any future
     * rejection needs the same treatment on the view side.
     *
     * Note this is called on every item crossing during a drag, so it must stay
     * cheap: it only reorders rows in memory. The database write is deferred to
     * [commitReorder], which the grid calls once when the drag ends.
     */
    fun onItemReorder(fromIndex: Int, toIndex: Int) {
        val state = _uiState.value as? FlowUiState.Success ?: return
        val items = state.items.toMutableList()

        val from = items.getOrNull(fromIndex) ?: return
        val to = items.getOrNull(toIndex) ?: return

        val section = from.section ?: return
        if (section != to.section) return

        val moved = items.removeAt(fromIndex)
        items.add(toIndex, moved)

        val updated = state.withItems(items)

        // `position` is the column the DAOs sort by, so the reordered section has
        // to be renumbered 0..n-1 before it is written. Without this the next
        // database emission (ORDER BY position ASC) would restore the previous
        // order and the card would visibly snap back to where it started.
        val renumbered = when (section) {
            FlowItem.Section.TASKS -> updated.copy(
                tasks = updated.tasks.mapIndexed { index, task -> task.copy(position = index) }
            )
            FlowItem.Section.CHECKLISTS -> updated.copy(
                checkLists = updated.checkLists.mapIndexed { index, list -> list.copy(position = index) }
            )
            FlowItem.Section.IDEAS -> updated.copy(
                ideas = updated.ideas.mapIndexed { index, idea -> idea.copy(position = index) }
            )
        }

        _uiState.value = renumbered

        // The database is written once, when the drag ends: see [commitReorder].
        pendingReorderSections += section
    }

    /**
     * Persists every section whose order changed since the last commit.
     *
     * Called by the grid when the drag gesture ends. While the finger is down
     * [onItemReorder] only moves rows in the UI state, so a drag across N slots
     * costs **one** write of the section instead of N.
     *
     * Safe to call unconditionally — it returns immediately when nothing moved,
     * which is the case on the effect's very first run (before any drag).
     *
     * The order is lost if the process dies mid-drag; that is deliberate, since
     * a partially dragged list is not worth a write per crossing.
     */
    fun commitReorder() {
        if (pendingReorderSections.isEmpty()) return
        val state = _uiState.value as? FlowUiState.Success ?: return

        val sections = pendingReorderSections.toSet()
        pendingReorderSections.clear()

        // `renumbered` already fixed the positions, so the lists can go as they are.
        viewModelScope.launch {
            sections.forEach { section ->
                when (section) {
                    FlowItem.Section.TASKS -> taskRepository.updateTasksOrder(state.tasks)
                    FlowItem.Section.CHECKLISTS -> checkListRepository.updateCheckListsOrder(state.checkLists)
                    FlowItem.Section.IDEAS -> ideaRepository.updateIdeasOrder(state.ideas)
                }
            }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun refreshBanner() {
        val canSchedule = reminderScheduler.canScheduleExactAlarms()
        wasPermissionMissing = !canSchedule

        viewModelScope.launch {
            val tasks = (uiState.value as? FlowUiState.Success)?.tasks
                ?: return@launch  // still loading — init block will call refreshBanner again via first DB emit

            val now = System.currentTimeMillis()
            val hasAnyFutureReminder = tasks.any { task ->
                (task.reminderTime?.let { it > now } == true) ||
                        task.subTasks.any { sub -> sub.reminderTime?.let { it > now } == true }
            }
            _bannerVisible.value = hasAnyFutureReminder && !canSchedule
        }
    }
}

data class CoreData(
    val tasks: List<Task>,
    val ideas: List<Idea>,
    val lists: List<CheckList>,
    val categories: List<Category>
)