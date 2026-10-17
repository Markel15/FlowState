package com.markel.flowstate.feature.flow

import com.markel.flowstate.core.domain.CheckList
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.core.domain.Task

/**
 * Flat item model for the Flow screen.
 *
 * Every row the grid renders — including the section headers — is one of these.
 * That makes the index reported by the `reorderable` library (`from.index` /
 * `to.index`) map 1:1 onto `items[index]`, which removes the hardcoded
 * `headerOffset` arithmetic that `SectionedFlowView` needs while headers are
 * emitted as independent `item { }` blocks.
 *
 * Reordering is scoped to a single [Section]: a move is only valid when the
 * dragged item and the target item share the same non-null [section]. Items whose
 * [section] is `null` (headers) are fixed chrome and never move.
 *
 * Adding a future item type (drawings, recordings, …) means adding a variant
 * here plus its card composable — the grid, the reorder state and the span
 * logic stay untouched. That is what keeps a future single combined list cheap.
 */
sealed interface FlowItem {

    /** The reorderable scope an item belongs to. */
    enum class Section { TASKS, CHECKLISTS, IDEAS }

    /** Section this item may be reordered within, or `null` for fixed chrome. */
    val section: Section? get() = null

    /**
     * `true` when the item must span **every** column of the grid
     * (`StaggeredGridItemSpan.FullLine`) instead of occupying a single one.
     *
     * Note this is not "fill the available width" — every item does that. It is
     * "take all N columns instead of 1". Only the two card types opt out.
     */
    val spanAllColumns: Boolean get() = true

    // ── Tasks ─────────────────────────────────────────────────────────────────

    data object TasksHeader : FlowItem

    data class TaskRow(val task: Task) : FlowItem {
        override val section: Section? get() = Section.TASKS
    }

    // ── CheckLists ────────────────────────────────────────────────────────────

    data object CheckListsHeader : FlowItem

    data class CheckListCard(val checkList: CheckList) : FlowItem {
        override val section: Section? get() = Section.CHECKLISTS
        override val spanAllColumns: Boolean get() = false
    }

    // ── Ideas ─────────────────────────────────────────────────────────────────

    data object IdeasHeader : FlowItem

    data class IdeaCard(val idea: Idea) : FlowItem {
        override val section: Section? get() = Section.IDEAS
        override val spanAllColumns: Boolean get() = false
    }

    // ── Chrome ────────────────────────────────────────────────────────────────

    /**
     * Empty room after the last section so the FAB never covers content.
     */
    data object BottomSpacer : FlowItem
}

/**
 * Stable key for an item, used by the grid's `itemsIndexed`.
 *
 * Must be unique across **all** [FlowItem] variants: a collision between, say, a
 * task and an idea would make Compose reuse the wrong composable and the wrong
 * `SwipeToDismissBoxState`.
 *
 * [taskDeleteVersions] is folded into the task key on purpose. Bumping it after
 * an undo gives the item a brand-new key, so Compose creates a fresh composable
 * with a fresh `SwipeToDismissBoxState` instead of restoring the stale
 * `EndToStart` saved state that would immediately re-trigger the deletion.
 */
internal fun FlowItem.stableKey(taskDeleteVersions: Map<Int, Int> = emptyMap()): Any =
    when (this) {
        FlowItem.TasksHeader -> "tasks_header"
        is FlowItem.TaskRow -> Pair(task.id, taskDeleteVersions[task.id] ?: 0)
        FlowItem.CheckListsHeader -> "checklists_header"
        is FlowItem.CheckListCard -> "cl_${checkList.id}"
        FlowItem.IdeasHeader -> "ideas_header"
        is FlowItem.IdeaCard -> "idea_${idea.id}"
        FlowItem.BottomSpacer -> "bottom_spacer"
    }
