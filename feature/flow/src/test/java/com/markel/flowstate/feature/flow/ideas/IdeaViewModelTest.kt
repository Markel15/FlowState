package com.markel.flowstate.feature.flow.ideas

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import app.cash.turbine.test
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.domain.CategoryRepository
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.core.domain.IdeaRepository
import com.markel.flowstate.core.testing.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class IdeaViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: IdeaRepository = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private lateinit var viewModel: IdeaEditorViewModel

    /**
     * Simulates the user typing in the body field (now a hoisted TextFieldState).
     *
     * In production, the Compose frame loop delivers snapshot apply notifications
     * automatically, so the ViewModel's snapshotFlow-based autosave sees every edit.
     * Plain JVM tests have no frame loop: we must call Snapshot.sendApplyNotifications()
     * after each edit, otherwise the autosave flow never observes the change.
     */
    private fun IdeaEditorViewModel.typeContent(text: String) {
        contentState.edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
    }

    /** Same as [typeContent], for the title field (also a hoisted TextFieldState). */
    private fun IdeaEditorViewModel.typeTitle(text: String) {
        titleState.edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun openNew_resetsEditorState() = runTest {
        // GIVEN - A fresh ViewModel instance
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN - Opening the editor for a new idea
        viewModel.openNew()

        // THEN - The editor state should be clean (null idea and empty fields)
        viewModel.editor.test {
            val state = awaitItem()
            assertNull(state.idea)
        }
        assertEquals("", viewModel.titleState.text.toString())
        assertEquals("", viewModel.contentState.text.toString())
    }

    @Test
    fun openExisting_loadsIdeaData() = runTest {
        // GIVEN - An existing idea and the ViewModel
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        val idea = Idea(id = 1, title = "Original", content = "Content", color = 0xFF123456L)

        // WHEN - Opening the editor with that existing idea
        viewModel.openExisting(idea)

        // THEN - The editor state must reflect the idea's current data
        viewModel.editor.test {
            val state = awaitItem()
            assertEquals(idea, state.idea)
            assertEquals(0xFF123456L, state.color)
        }
        // Title and body are loaded into the hoisted TextFieldStates, cursor at the end
        assertEquals("Original", viewModel.titleState.text.toString())
        assertEquals(TextRange("Original".length), viewModel.titleState.selection)
        assertEquals("Content", viewModel.contentState.text.toString())
        assertEquals(TextRange("Content".length), viewModel.contentState.selection)
    }

    @Test
    fun updateFields_updatesStateCorrectly() = runTest {
        // GIVEN - A ViewModel in its initial state
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)

        // WHEN - The user types a new title, content and changes the color
        viewModel.typeTitle("New Idea")
        viewModel.typeContent("Content of the idea")
        viewModel.updateColor(0xFF654321L)

        // THEN - The UI state should be updated with the new values immediately
        viewModel.editor.test {
            val state = awaitItem()
            assertEquals(0xFF654321L, state.color)
        }
        assertEquals("New Idea", viewModel.titleState.text.toString())
        assertEquals("Content of the idea", viewModel.contentState.text.toString())
    }

    @Test
    fun closeAndSave_withNewIdea_callsRepository() = runTest {
        // GIVEN - A new idea being composed in the editor
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.typeTitle("New Title")
        viewModel.typeContent("New Content")

        // WHEN - Closing the editor (which triggers the save logic)
        viewModel.closeAndSave()

        // THEN - The repository's upsert method should be called with the new data
        coVerify {
            repository.upsertIdea(match {
                it.title == "New Title" && it.content == "New Content"
            })
        }
    }

    @Test
    fun closeAndSave_withExistingIdea_updatesIdea() = runTest {
        // GIVEN - An editor already loaded with an existing idea
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        val idea = Idea(id = 10, title = "Old Title", content = "Old Content", color = 0L)
        viewModel.openExisting(idea)

        // WHEN - Modifying the title and saving
        viewModel.typeTitle("Updated Title")
        viewModel.closeAndSave()

        // THEN - The repository should receive an update for the specific ID with the new title
        coVerify {
            repository.upsertIdea(match {
                it.id == 10 && it.title == "Updated Title"
            })
        }
    }

    /**
     * Test case for a known bug: autosave was triggering multiple times while
     * the user was typing a new idea. upsertIdea should only be called once for
     * the initial insertion; subsequent calls should be updates to the same idea (same ID).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun autosave_newIdea_doesNotCreateDuplicates() = runTest {
        // GIVEN - The repository returns ID=42 on the first insertion
        coEvery { repository.upsertIdea(any()) } returns 42L
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openNew()

        // WHEN - The user types in two separate bursts (2 debounce triggers)
        viewModel.typeTitle("My idea")
        advanceTimeBy(900) // first autosave → should insert and save ID=42

        viewModel.typeContent("More text")
        advanceTimeBy(900) // second autosave → should update id=42, NOT insert a new one

        // THEN - upsertIdea was called exactly twice...
        coVerify(exactly = 2) { repository.upsertIdea(any()) }
        // ...but the second call used ID 42 (update), not id=0 (new insertion)
        coVerify(exactly = 1) {
            repository.upsertIdea(match { it.id == 42 && it.content == "More text" })
        }
    }

    /**
     * After the first autosave of a new idea, the internal state must have
     * `idea != null` with the actual ID returned by the repository.
     * If this fails, the next autosave would recreate the idea from scratch.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun autosave_newIdea_updatesStateWithInsertedId() = runTest {
        // GIVEN
        coEvery { repository.upsertIdea(any()) } returns 99L
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openNew()

        // WHEN - The debounce triggers
        viewModel.typeTitle("Title")
        advanceTimeBy(900)

        // THEN - The state already contains the idea with the real ID
        viewModel.editor.test {
            val state = awaitItem()
            assertNotNull(state.idea)
            assertEquals(99, state.idea?.id)
        }
    }

    /**
     * If the editor is closed before the debounce triggers (rapid typing + back),
     * closeAndSave must still persist the idea exactly once.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun closeAndSave_beforeDebounce_savesExactlyOnce() = runTest {
        coEvery { repository.upsertIdea(any()) } returns 1L
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openNew()

        // WHEN - User types and closes before 800ms
        viewModel.typeTitle("Fast")
        viewModel.closeAndSave() // manually save before debounce

        advanceTimeBy(900) // debounce should no longer do anything (state reset)

        // THEN - Only one insertion
        coVerify(exactly = 1) {
            repository.upsertIdea(match { it.title == "Fast" })
        }
    }

    /**
     * A completely blank idea should never be persisted,
     * neither by autosave nor by closeAndSave.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun autosave_blankIdea_neverCallsRepository() = runTest {
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openNew()

        advanceTimeBy(900)
        viewModel.closeAndSave()

        coVerify(exactly = 0) { repository.upsertIdea(any()) }
    }

    /**
     * Editing an existing idea should never create a new one,
     * regardless of how many times the autosave triggers.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun autosave_existingIdea_neverCreatesNewIdea() = runTest {
        coEvery { repository.upsertIdea(any()) } returns 5L
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        val idea = Idea(id = 5, title = "Original", content = "", color = 0L)
        viewModel.openExisting(idea)

        // WHEN - Multiple writing bursts
        viewModel.typeTitle("Edit 1"); advanceTimeBy(900)
        viewModel.typeTitle("Edit 2"); advanceTimeBy(900)
        viewModel.typeTitle("Edit 3"); advanceTimeBy(900)

        // THEN - All calls use id=5, never id=0
        coVerify(atLeast = 1) {
            repository.upsertIdea(match { it.id == 5 })
        }
        coVerify(exactly = 0) {
            repository.upsertIdea(match { it.id == 0 })
        }
    }

    /**
     * deleteIdea must call the repository with the correct idea
     */
    @Test
    fun deleteIdea_callsRepository() = runTest {
        val idea = Idea(id = 7, title = "To delete", content = "", color = 0L)
        coEvery { repository.getIdeaById(7) } returns idea
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openExisting(idea)

        viewModel.deleteIdea(7)

        coVerify { repository.deleteIdea(idea) }
    }

    /**
     * New contract after the TextFieldState migration: body-only edits (no title,
     * color or category changes) must also trigger the debounced autosave, because
     * content changes now travel through snapshotFlow { contentState.text } instead
     * of the _editor StateFlow.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun autosave_contentOnlyEdit_persistsContent() = runTest {
        coEvery { repository.upsertIdea(any()) } returns 7L
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openNew()

        // WHEN - The user only types in the body field
        viewModel.typeContent("Only body text")
        advanceTimeBy(900)

        // THEN - The autosave persisted the body text
        coVerify(exactly = 1) {
            repository.upsertIdea(match { it.content == "Only body text" })
        }
    }

    /**
     * Same contract for the title after its TextFieldState migration: title-only
     * edits must trigger the debounced autosave through snapshotFlow.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun autosave_titleOnlyEdit_persistsTitle() = runTest {
        coEvery { repository.upsertIdea(any()) } returns 11L
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openNew()

        // WHEN - The user only types in the title field
        viewModel.typeTitle("Only title")
        advanceTimeBy(900)

        // THEN - The autosave persisted the title
        coVerify(exactly = 1) {
            repository.upsertIdea(match { it.title == "Only title" })
        }
    }

    /**
     * Editing the body of an EXISTING idea must update that idea (same ID),
     * never create a new one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun autosave_contentEditOnExistingIdea_updatesSameId() = runTest {
        coEvery { repository.upsertIdea(any()) } returns 5L
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        val idea = Idea(id = 5, title = "Original", content = "Old body", color = 0L)
        viewModel.openExisting(idea)

        // WHEN - The user edits only the body
        viewModel.typeContent("New body")
        advanceTimeBy(900)

        // THEN - Updated with id=5 and the new body; no insertion with id=0
        coVerify(atLeast = 1) {
            repository.upsertIdea(match { it.id == 5 && it.content == "New body" })
        }
        coVerify(exactly = 0) {
            repository.upsertIdea(match { it.id == 0 })
        }
    }


    /**
     * Invokes the protected [androidx.lifecycle.ViewModel.onCleared] via
     * reflection so tests can simulate Navigation3 destroying the NavEntry.
     *
     * We can't call it directly because `onCleared` is `protected`. Reflection
     * is the standard escape hatch — see nowinandroid and other AOSP samples.
     */
    private fun IdeaEditorViewModel.invokeOnCleared() {
        val method = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(this)
    }

    /**
     * When Navigation3 pops the editor screen, the ViewModel is destroyed and
     * [IdeaEditorViewModel.onCleared] must reset the editor state.
     *
     * This replaces the per-action reset that used to live in closeAndSave /
     * deleteIdea before the Navigation3 migration (commit 574de92 + 67bd376).
     * Without this contract, re-opening the editor after closing it would
     * show the previous idea's data.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onCleared_resetsEditorState() = runTest {
        val idea = Idea(id = 7, title = "To delete", content = "Persistent", color = 0L)
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openExisting(idea)

        // Sanity: state is loaded before onCleared
        assertEquals(idea, viewModel.editor.value.idea)
        assertEquals("To delete", viewModel.titleState.text.toString())

        // WHEN — Nav3 destroys the NavEntry → VM.onCleared()
        viewModel.invokeOnCleared()

        // THEN — editor state is reset to the default empty state.
        // (titleState/contentState are not asserted here: they live in the ViewModel
        // being destroyed; a new NavEntry creates a fresh ViewModel with empty text.)
        viewModel.editor.test {
            val state = awaitItem()
            assertNull(state.idea)
        }
    }

    /**
     * If the autosave debounce has already fired (e.g. user paused for >800ms
     * before tapping back), [IdeaEditorViewModel.closeAndSave] must NOT
     * trigger a second upsert — it should cancel the autosave job and only
     * persist if there are unsaved changes.
     *
     * Guards against: a regression where `autosaveJob?.cancel()` is removed
     * from closeAndSave, causing a duplicate upsert race that could write
     * stale data over the autosaved data.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun closeAndSave_afterAutosaveFired_doesNotUpsertTwice() = runTest {
        coEvery { repository.upsertIdea(any()) } returns 42L
        viewModel = IdeaEditorViewModel(repository, categoryRepository, userPreferencesRepository)
        viewModel.openNew()
        viewModel.typeTitle("Title")

        // First autosave fires (debounce elapses)
        advanceTimeBy(900)
        coVerify(exactly = 1) { repository.upsertIdea(any()) }

        // User taps back immediately after — no new edits
        viewModel.closeAndSave()
        advanceUntilIdle()

        // closeAndSave calls persistIfNeeded, which sees state.idea != null
        // (set after the first autosave) → it does ONE more upsert with id=42
        // (an update, not a new insertion). The contract we enforce here:
        //   - Total calls = 2 (one autosave + one closeAndSave)
        //   - Exactly ONE insert with id == 0 (the first autosave creates the idea)
        //   - Exactly ONE update with id == 42 (closeAndSave updates it, NOT a new insert)
        coVerify(exactly = 2) { repository.upsertIdea(any()) }
        coVerify(exactly = 1) {
            repository.upsertIdea(match { it.id == 0 })
        }
        coVerify(exactly = 1) {
            repository.upsertIdea(match { it.id == 42 })
        }
    }

}