package com.markel.flowstate.feature.flow.checklists

import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.domain.CategoryRepository
import com.markel.flowstate.core.domain.CheckList
import com.markel.flowstate.core.domain.CheckListItem
import com.markel.flowstate.core.domain.CheckListRepository
import com.markel.flowstate.core.testing.util.MainDispatcherRule
import com.markel.flowstate.feature.flow.components.COLOR_TRANSPARENT
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChecklistViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: CheckListRepository = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private lateinit var viewModel: CheckListViewModel

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun item(id: String, text: String, isDone: Boolean = false, position: Int = 0) =
        CheckListItem(id = id, text = text, isDone = isDone, position = position)

    private fun checklist(
        id: Int = 1,
        title: String = "My List",
        items: List<CheckListItem> = emptyList()
    ) = CheckList(id = id, title = title, color = 0L, items = items)

    /**
     * Types into the hoisted title state exactly as BasicTextField(state = ...)
     * would. On-device the frame loop applies snapshots automatically; JVM tests
     * must call sendApplyNotifications so the VM's snapshotFlow collector runs.
     */
    private fun typeTitle(text: String) {
        viewModel.titleState.edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
    }

    /**
     * Types into one item's hoisted TextFieldState exactly as the row's
     * BasicTextField(state = ...) would. On-device the frame loop applies
     * snapshots automatically; JVM tests must call sendApplyNotifications so
     * the VM's snapshotFlow collector runs (see [typeTitle]).
     */
    private fun typeItemText(id: String, text: String) {
        viewModel.itemTextState(id).edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
    }

    // ── openNew ───────────────────────────────────────────────────────────────

    @Test
    fun openNew_resetsEditorState() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.openNew()

        // THEN
        viewModel.editor.test {
            val state = awaitItem()
            assertNull(state.checkList)
            assertEquals("", state.title)
            assertEquals(COLOR_TRANSPARENT, state.color)
            assertTrue(state.items.isEmpty())
        }
    }

    // ── loadForEditing ────────────────────────────────────────────────────────

    @Test
    fun loadForEditing_whenChecklistExists_populatesEditorState() = runTest {
        // GIVEN
        val items = listOf(item("a", "Buy milk"), item("b", "Buy eggs", isDone = true))
        val list = checklist(id = 5, title = "Groceries", items = items)
        coEvery { repository.getLists() } returns flowOf(listOf(list))
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.loadForEditing(5)

        // THEN
        viewModel.editor.test {
            val state = awaitItem()
            assertEquals(list, state.checkList)
            assertEquals("Groceries", state.title)
            assertEquals(2, state.items.size)
        }
    }

    @Test
    fun loadForEditing_whenChecklistDoesNotExist_keepsEmptyState() = runTest {
        // GIVEN
        coEvery { repository.getLists() } returns flowOf(emptyList())
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.loadForEditing(99)

        // THEN — state should remain untouched (no crash, no phantom data)
        viewModel.editor.test {
            val state = awaitItem()
            assertNull(state.checkList)
            assertTrue(state.items.isEmpty())
        }
    }

    // ── typeTitle / updateColor ───────────────────────────────────────────────

    @Test
    fun typeTitle_updatesStateCorrectly() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN
        typeTitle("Shopping")

        // THEN
        viewModel.editor.test {
            assertEquals("Shopping", awaitItem().title)
        }
    }

    @Test
    fun updateColor_updatesStateCorrectly() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.updateColor(0xFF123456L)

        // THEN
        viewModel.editor.test {
            assertEquals(0xFF123456L, awaitItem().color)
        }
    }

    // ── addItem ───────────────────────────────────────────────────────────────

    @Test
    fun addItem_addsEmptyItemToList_andReturnsItsId() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN
        val returnedId = viewModel.addItem()

        // THEN
        viewModel.editor.test {
            val state = awaitItem()
            assertEquals(1, state.items.size)
            assertEquals(returnedId, state.items[0].id)
            assertEquals("", state.items[0].text)
            assertEquals(false, state.items[0].isDone)
        }
    }

    @Test
    fun addItem_multipleItems_positionsAreSequential() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.addItem()
        viewModel.addItem()
        viewModel.addItem()

        // THEN
        viewModel.editor.test {
            val items = awaitItem().items
            assertEquals(3, items.size)
            assertEquals(0, items[0].position)
            assertEquals(1, items[1].position)
            assertEquals(2, items[2].position)
        }
    }

    // ── typeItemText ──────────────────────────────────────────────────────────

    @Test
    fun typeItemText_updatesCorrectItem() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        val id = viewModel.addItem()

        // WHEN
        typeItemText(id, "Milk")

        // THEN
        viewModel.editor.test {
            val state = awaitItem()
            assertEquals("Milk", state.items.first { it.id == id }.text)
        }
    }

    @Test
    fun typeItemText_doesNotAffectOtherItems() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        val id1 = viewModel.addItem()
        val id2 = viewModel.addItem()

        // WHEN
        typeItemText(id1, "Item 1 text")

        // THEN
        viewModel.editor.test {
            val items = awaitItem().items
            assertEquals("Item 1 text", items.first { it.id == id1 }.text)
            assertEquals("", items.first { it.id == id2 }.text)
        }
    }

    // ── toggleItem ────────────────────────────────────────────────────────────

    @Test
    fun toggleItem_marksItemAsDone() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        val id = viewModel.addItem()

        // WHEN
        viewModel.toggleItem(id)

        // THEN
        viewModel.editor.test {
            assertEquals(true, awaitItem().items.first { it.id == id }.isDone)
        }
    }

    @Test
    fun toggleItem_togglesBackToUndone() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        val id = viewModel.addItem()
        viewModel.toggleItem(id) // first toggle → done

        // WHEN
        viewModel.toggleItem(id) // second toggle → undone

        // THEN
        viewModel.editor.test {
            assertEquals(false, awaitItem().items.first { it.id == id }.isDone)
        }
    }

    // ── removeItem ────────────────────────────────────────────────────────────

    @Test
    fun removeItem_removesCorrectItem() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        val id1 = viewModel.addItem()
        val id2 = viewModel.addItem()

        // WHEN
        viewModel.removeItem(id1)

        // THEN
        viewModel.editor.test {
            val items = awaitItem().items
            assertEquals(1, items.size)
            assertEquals(id2, items[0].id)
        }
    }

    // ── reorderPendingItems ───────────────────────────────────────────────────

    @Test
    fun reorderPendingItems_movesItemCorrectly() = runTest {
        // GIVEN — three pending items in order A, B, C
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        val idA = viewModel.addItem()
        typeItemText(idA, "A")
        val idB = viewModel.addItem()
        typeItemText(idB, "B")
        val idC = viewModel.addItem()
        typeItemText(idC, "C")

        // WHEN — move A (index 0) to the end (index 2) → expected: B, C, A
        viewModel.reorderPendingItems(fromIndex = 0, toIndex = 2)

        // THEN
        viewModel.editor.test {
            val pending = awaitItem().items.filter { !it.isDone }
            assertEquals("B", pending[0].text)
            assertEquals("C", pending[1].text)
            assertEquals("A", pending[2].text)
        }
    }

    @Test
    fun reorderPendingItems_doesNotAffectCompletedItems() = runTest {
        // GIVEN — two pending items and one completed
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        val idA = viewModel.addItem()
        typeItemText(idA, "A")
        val idB = viewModel.addItem()
        typeItemText(idB, "B")
        val idDone = viewModel.addItem()
        typeItemText(idDone, "Done")
        viewModel.toggleItem(idDone)

        // WHEN — reorder pending items
        viewModel.reorderPendingItems(fromIndex = 0, toIndex = 1)

        // THEN — the completed item remains present and still isDone
        viewModel.editor.test {
            val state = awaitItem()
            val completed = state.items.filter { it.isDone }
            assertEquals(1, completed.size)
            assertEquals("Done", completed[0].text)
        }
    }

    @Test
    fun reorderPendingItems_updatesPositionsSequentially() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        val id1 = viewModel.addItem()
        val id2 = viewModel.addItem()
        val id3 = viewModel.addItem()

        // WHEN
        viewModel.reorderPendingItems(fromIndex = 2, toIndex = 0)

        // THEN — all positions must be 0, 1, 2 without gaps
        viewModel.editor.test {
            val positions = awaitItem().items.map { it.position }
            assertEquals(listOf(0, 1, 2), positions)
        }
    }

    // ── closeAndSave ──────────────────────────────────────────────────────────

    @Test
    fun closeAndSave_withNewChecklist_callsRepositoryUpsert() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        typeTitle("Groceries")
        val id = viewModel.addItem()
        typeItemText(id, "Milk")

        // WHEN
        viewModel.closeAndSave()

        // THEN
        coVerify {
            repository.upsertList(match { list ->
                list.title == "Groceries" && list.items.any { it.text == "Milk" }
            })
        }
    }

    @Test
    fun closeAndSave_withEmptyTitleAndNoItems_doesNotCallRepository() = runTest {
        // GIVEN — blank editor, nothing to save
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.closeAndSave()

        // THEN
        coVerify(exactly = 0) { repository.upsertList(any()) }
    }

    @Test
    fun closeAndSave_filtersOutBlankItems_beforePersisting() = runTest {
        // GIVEN — one real item and one blank (ghost) item
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        typeTitle("List")
        val idReal = viewModel.addItem()
        typeItemText(idReal, "Real item")
        viewModel.addItem() // blank item — should be discarded

        // WHEN
        viewModel.closeAndSave()

        // THEN — only the non-blank item reaches the repository
        coVerify {
            repository.upsertList(match { list ->
                list.items.size == 1 && list.items[0].text == "Real item"
            })
        }
    }

    @Test
    fun closeAndSave_withExistingChecklist_updatesItInPlace() = runTest {
        // GIVEN — editor loaded with an existing checklist
        val original = checklist(id = 7, title = "Old Title")
        coEvery { repository.getLists() } returns flowOf(listOf(original))
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.loadForEditing(7)
        typeTitle("New Title")

        // WHEN
        viewModel.closeAndSave()

        // THEN — the upsert must use the same ID with the updated title
        coVerify {
            repository.upsertList(match { list ->
                list.id == 7 && list.title == "New Title"
            })
        }
    }

    @Test
    fun closeAndSave_persistsCurrentState() = runTest {
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        typeTitle("Temp")

        // WHEN
        viewModel.closeAndSave()

        coVerify {
            repository.upsertList(match { it.title == "Temp" })
        }
    }

    // ── deleteCheckList ───────────────────────────────────────────────────────

    @Test
    fun deleteCheckList_callsRepositoryDelete() = runTest {
        // GIVEN
        val list = checklist(id = 3, title = "To delete")
        coEvery { repository.getLists() } returns flowOf(listOf(list))
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.deleteCheckList(3)

        // THEN
        coVerify { repository.deleteList(list) }
    }

    @Test
    fun deleteCheckList_whenNotFound_doesNotCallRepository() = runTest {
        // GIVEN
        coEvery { repository.getLists() } returns flowOf(emptyList())
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.deleteCheckList(99)

        // THEN
        coVerify(exactly = 0) { repository.deleteList(any()) }
    }

    /**
     * Invokes the protected [androidx.lifecycle.ViewModel.onCleared] via
     * reflection so tests can simulate Navigation3 destroying the NavEntry.
     *
     * We can't call it directly because `onCleared` is `protected`. Reflection
     * is the standard escape hatch — see nowinandroid and other AOSP samples.
     */
    private fun CheckListViewModel.invokeOnCleared() {
        val method = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(this)
    }

    /**
     * When Navigation3 pops the editor screen, the ViewModel is destroyed and
     * [CheckListViewModel.onCleared] must reset the editor state.
     *
     * This replaces the per-action reset that used to live in closeAndSave /
     * deleteCheckList before the Navigation3 migration (commit 574de92 +
     * 67bd376). Without this contract, re-opening the editor after closing it
     * would show the previous checklist's data.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onCleared_resetsEditorState() = runTest {
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        typeTitle("Persistent")
        val itemId = viewModel.addItem()
        typeItemText(itemId, "Persistent item")

        // Sanity: state has data before onCleared
        assertEquals("Persistent", viewModel.editor.value.title)
        assertTrue(viewModel.editor.value.items.isNotEmpty())

        // WHEN — Nav3 destroys the NavEntry → VM.onCleared()
        viewModel.invokeOnCleared()

        // THEN — editor state is reset to the default empty state
        viewModel.editor.test {
            val state = awaitItem()
            assertNull(state.checkList)
            assertEquals("", state.title)
            assertTrue(state.items.isEmpty())
        }
    }

    /**
     * If the autosave debounce has already fired (user paused for >800ms
     * before tapping back), [CheckListViewModel.closeAndSave] must NOT
     * trigger a duplicate insertion — it cancels the autosave job and only
     * persists an update via [persistIfNeeded].
     *
     * Guards against: a regression where `autosaveJob?.cancel()` is removed
     * from closeAndSave, causing a duplicate upsert that creates a second
     * checklist with id == 0.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun closeAndSave_afterAutosaveFired_doesNotInsertTwice() = runTest {
        coEvery { repository.upsertList(any()) } returns 42
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        typeTitle("Title")

        // First autosave fires (debounce elapses)
        advanceTimeBy(900)
        coVerify(exactly = 1) { repository.upsertList(any()) }

        // User taps back immediately after — no new edits
        viewModel.closeAndSave()
        advanceUntilIdle()

        // closeAndSave calls persistIfNeeded, which sees state.checkList != null
        // (set after the first autosave) → it does ONE more upsert with id=42
        // (an update, not a new insertion). The contract we enforce here:
        //   - Total calls = 2 (one autosave + one closeAndSave)
        //   - Exactly ONE insert with id == 0 (the first autosave creates the list)
        //   - Exactly ONE update with id == 42 (closeAndSave updates it, NOT a new insert)
        coVerify(exactly = 2) { repository.upsertList(any()) }
        coVerify(exactly = 1) {
            repository.upsertList(match { it.id == 0 })
        }
        coVerify(exactly = 1) {
            repository.upsertList(match { it.id == 42 })
        }
    }



    // ── Hoisted TextFieldStates ───────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun loadForEditing_seedsHoistedTextStates() = runTest {
        // GIVEN
        val items = listOf(item("a", "Buy milk"), item("b", "Buy eggs", isDone = true))
        val list = checklist(id = 5, title = "Groceries", items = items)
        coEvery { repository.getLists() } returns flowOf(listOf(list))
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN
        viewModel.loadForEditing(5)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        // THEN — title and every item are seeded into their hoisted states
        assertEquals("Groceries", viewModel.titleState.text.toString())
        assertEquals("Buy milk", viewModel.itemTextState("a").text.toString())
        assertEquals("Buy eggs", viewModel.itemTextState("b").text.toString())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun itemTextState_typingFlowsIntoEditorAndAutosaves() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openNew()
        val id = viewModel.addItem()

        // WHEN — type as the row's BasicTextField(state = ...) would
        viewModel.itemTextState(id).edit { replace(0, length, "Milk") }
        Snapshot.sendApplyNotifications()

        // THEN — the editor draft is updated...
        assertEquals("Milk", viewModel.editor.value.items.first { it.id == id }.text)

        // ...and the existing debounced autosave persists it
        advanceTimeBy(900)
        coVerify { repository.upsertList(match { it.items.any { i -> i.text == "Milk" } }) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun closeAndSave_persistsEditsNotYetObservedByCollectors() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openNew()
        val id = viewModel.addItem()

        // WHEN — type WITHOUT sendApplyNotifications: the snapshotFlow collectors
        // have not run yet (back pressed in the same frame as the last keystroke)
        viewModel.titleState.edit { replace(0, length, "Groceries") }
        viewModel.itemTextState(id).edit { replace(0, length, "Milk") }
        viewModel.closeAndSave()
        advanceUntilIdle()

        // THEN — closeAndSave reads the states directly, nothing is lost
        coVerify {
            repository.upsertList(match { list ->
                list.title == "Groceries" && list.items.any { it.text == "Milk" }
            })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun removeItem_stopsItsCollector() = runTest {
        // GIVEN
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openNew()
        val id = viewModel.addItem()
        val state = viewModel.itemTextState(id)
        state.edit { replace(0, length, "Before") }
        Snapshot.sendApplyNotifications()

        // WHEN — remove the item, then keep editing its old state reference
        viewModel.removeItem(id)
        state.edit { replace(0, length, "After") }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        // THEN — the removed item is not resurrected
        assertTrue(viewModel.editor.value.items.isEmpty())
    }

    @Test
    fun openNew_resetsHoistedTextStates() = runTest {
        // GIVEN — an editor with typed title and item
        viewModel = CheckListViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openNew()
        typeTitle("Old list")
        val id = viewModel.addItem()
        viewModel.itemTextState(id).edit { replace(0, length, "Old item") }
        Snapshot.sendApplyNotifications()

        // WHEN
        viewModel.openNew()
        Snapshot.sendApplyNotifications()

        // THEN — hoisted states are fresh
        assertEquals("", viewModel.titleState.text.toString())
        assertTrue(viewModel.editor.value.items.isEmpty())
        val newId = viewModel.addItem()
        assertEquals("", viewModel.itemTextState(newId).text.toString())
    }

}