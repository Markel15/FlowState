package com.markel.flowstate.feature.flow.ideas

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.core.domain.CategoryRepository
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.core.domain.IdeaRepository
import com.markel.flowstate.feature.flow.components.COLOR_TRANSPARENT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdeaEditorState(
    val idea: Idea? = null,
    val title: String = "",
    val color: Long = COLOR_TRANSPARENT, // default: no background color
    val categoryId: Int? = Category.GENERAL_ID
)

@OptIn(FlowPreview::class)
@HiltViewModel
class IdeaEditorViewModel @Inject constructor(
    private val ideaRepository: IdeaRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val _editor = MutableStateFlow(IdeaEditorState())
    val editor: StateFlow<IdeaEditorState> = _editor.asStateFlow()

    /**
     * Editing state for the idea's body text, hoisted into the ViewModel as recommended
     * by Google's state-based text field migration guide. The UI binds it directly to
     * BasicTextField(state = contentState); there is no String <-> TextFieldState bridge.
     * Text changes are observed via snapshotFlow in [autosaveJob].
     */
    val contentState = TextFieldState()

    /**
     * Handle to the debounced autosave collector so it can be canceled in
     * [closeAndSave]. Without this, the collector would fire again after the
     * user taps back.
     */
    private var autosaveJob: Job? = null

    /** User categories, exposed so the editor can populate the category selector. */
    val categories: StateFlow<List<Category>> = categoryRepository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether category tabs are enabled — the selector is only shown when true. */
    val categoriesEnabled: StateFlow<Boolean> = userPreferencesRepository.categoriesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val generalCategoryName: StateFlow<String?> = userPreferencesRepository.generalCategoryName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Autosave: debounce of 800ms over BOTH sources of editor changes:
        //  • _editor: title, color, category (drop(1) skips the initial state)
        //  • contentState.text: body edits (drop(1) skips the initial echo)
        // persistIfNeeded always reads the latest values of both.
        autosaveJob = viewModelScope.launch {
            merge(
                _editor.drop(1).map { },
                snapshotFlow { contentState.text.toString() }.drop(1).map { }
            )
                .debounce(800)
                .collect { persistIfNeeded(_editor.value) }
        }
    }

    // ── Open / Close ──────────────────────────────────────────────────────────

    /** Opens the overlay to CREATE a new blank idea. */
    fun openNew(categoryId: Int? = Category.GENERAL_ID) {
        _editor.value = IdeaEditorState(categoryId = categoryId)
        contentState.clearText()
    }

    /** Opens the overlay to EDIT an existing idea. */
    fun openExisting(idea: Idea) {
        _editor.value = IdeaEditorState(
            idea = idea,
            title = idea.title,
            color = idea.color,
            categoryId = idea.categoryId
        )
        contentState.setTextAndPlaceCursorAtEnd(idea.content)
    }

    /**
     * Loads an idea by ID from the repository.
     * Used when navigating (IdeaEditorScreen) with only the ID.
     */
    fun loadIdeaForEditing(ideaId: Int) {
        viewModelScope.launch {
            val idea = ideaRepository.getIdeaById(ideaId)
            if (idea != null) openExisting(idea)
        }
    }

    /**
     * Persists the current editor state and cancels the debounced autosave so it
     * doesn't fire a duplicate upsert.
     */
    fun closeAndSave() {
        autosaveJob?.cancel()
        viewModelScope.launch {
            persistIfNeeded(_editor.value)
        }
    }

    /**
     * Called by the framework when the ViewModel is no longer used (after the
     * NavEntry — and therefore the exit animation — is destroyed). This is the
     * safe moment to reset the editor state without causing a visual flicker.
     */
    override fun onCleared() {
        super.onCleared()
        _editor.value = IdeaEditorState()
    }

    /** Discards changes without saving (e.g. user explicitly cancels (not implemented yet)). */
    fun closeWithoutSaving() {
        _editor.value = IdeaEditorState()
        contentState.clearText()
    }

    // ── Field updates ─────────────────────────────────────────────────────────

    fun updateTitle(value: String) = _editor.update { it.copy(title = value) }

    fun updateColor(color: Long) = _editor.update { it.copy(color = color) }

    /**
     * Moves the idea being edited to a different category.
     *
     * Pass [Category.GENERAL_ID] to move the idea to the default (General)
     * category. The change is reflected in the editor state immediately and
     * persisted through the existing autosave flow (debounced [persistIfNeeded]).
     */
    fun updateCategory(categoryId: Int?) = _editor.update { it.copy(categoryId = categoryId ?: Category.GENERAL_ID) }

    fun deleteIdea(ideaId: Int) {
        viewModelScope.launch {
            val idea = ideaRepository.getIdeaById(ideaId) ?: return@launch
            ideaRepository.deleteIdea(idea)
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun persistIfNeeded(state: IdeaEditorState) {
        // Body text comes from the hoisted TextFieldState (single source of truth).
        val content = contentState.text.toString()
        val existing = state.idea
        if (existing != null) {
            ideaRepository.upsertIdea(
                existing.copy(
                    title = state.title,
                    content = content,
                    color = state.color,
                    categoryId = state.categoryId
                )
            )
        } else if (state.title.isNotBlank() || content.isNotBlank()) {
            val insertedId = ideaRepository.upsertIdea(
                Idea(
                    title = state.title,
                    content = content,
                    color = state.color,
                    createdAt = System.currentTimeMillis(),
                    categoryId = state.categoryId
                )
            )
            // Save the reference with the ID so subsequent autosaves don't create duplicates (existing != null)
            _editor.update { it.copy(idea = Idea(
                id = insertedId.toInt(),
                title = state.title,
                content = content,
                color = state.color,
                createdAt = System.currentTimeMillis(),
                categoryId = state.categoryId
            )) }
        }
    }

}