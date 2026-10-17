package com.markel.flowstate.feature.flow

import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.core.domain.CheckList
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.core.domain.Task

sealed interface FlowUiState {
    data object Loading : FlowUiState
    data class Success(
        val tasks: List<Task>,
        val ideas: List<Idea>,
        val checkLists: List<CheckList>,
        val categories: List<Category> = emptyList(),
        val selectedCategoryId: Int? = Category.GENERAL_ID,
        val categoriesEnabled: Boolean = false,
        val pendingTaskCounts: Map<Int?, Int> = emptyMap()
    ) : FlowUiState {

        /**
         * Flat, render-ready list consumed by `SectionedFlowView`.
         */
        val items: List<FlowItem> = buildList {
            if (tasks.isNotEmpty()) {
                add(FlowItem.TasksHeader)
                tasks.forEach { add(FlowItem.TaskRow(it)) }
            }
            if (checkLists.isNotEmpty()) {
                add(FlowItem.CheckListsHeader)
                checkLists.forEach { add(FlowItem.CheckListCard(it)) }
            }
            if (ideas.isNotEmpty()) {
                add(FlowItem.IdeasHeader)
                ideas.forEach { add(FlowItem.IdeaCard(it)) }
            }
            // Only when there is something to scroll: an empty screen shows
            // EmptyStateView and must keep `items.isEmpty()` true.
            if (isNotEmpty()) add(FlowItem.BottomSpacer)
        }
    }
}

/**
 * Rebuilds a [FlowUiState.Success] from an already-reordered [items] list.
 *
 * Used by the reorder callback: a drag hands back a shuffled `List<FlowItem>`,
 * and this projects it back onto the three typed lists. `filterIsInstance`
 * preserves the relative order of each section and the assignment order
 * (tasks → checkLists → ideas) matches how [FlowUiState.Success.items] is built,
 * so the resulting `items` mirrors the list that was passed in.
 *
 * Chrome items (headers) are dropped — they are re-emitted by `items` in their
 * canonical position. As a side effect a cross-section move (which the
 * caller rejects anyway) degrades safely instead of corrupting a typed list.
 */
internal fun FlowUiState.Success.withItems(items: List<FlowItem>): FlowUiState.Success = copy(
    tasks = items.filterIsInstance<FlowItem.TaskRow>().map { it.task },
    checkLists = items.filterIsInstance<FlowItem.CheckListCard>().map { it.checkList },
    ideas = items.filterIsInstance<FlowItem.IdeaCard>().map { it.idea },
)
