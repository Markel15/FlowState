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
 * [section] is `null` (headers) are fixed chrome and never move. Enforcing that
 * is *not* just a data rule — the grid has to keep a drag from ever targeting a
 * foreign row, see [isReorderTargetFor].
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
 * Whether a row of [this] section may be used as the swap target of the row
 * currently being dragged ([draggedSection]).
 *
 * `reorderable` has **no way to veto a move**: `onMove` returns `Unit` and the
 * library immediately assumes the list changed. Right after invoking it it
 * *predicts* the dragged row's new slot (`predictedDraggingItemOffset`) and
 * compensates the row's visual offset with that prediction while it waits for the
 * next layout pass (up to `MoveItemsLayoutInfoUpdateMaxWaitDuration`, 1 s). If the
 * move is then rejected by the ViewModel no layout pass ever disagrees with the
 * prediction, so the dragged row is left translated by a delta that does not
 * exist — the visible jump.
 *
 * The rejection therefore has to happen *before* the library chooses a target,
 * which is exactly what `ReorderableItem(enabled = …)` does: disabled keys are
 * removed from the library's `reorderableKeys` set and are never picked as a
 * target.
 *
 * A `null` [section] (chrome rows) is never a target while a drag is in flight;
 * chrome is not a `ReorderableItem` anyway. With no drag in flight
 * ([draggedSection] is `null`) every row is a valid target again, so the grid can
 * never be left locked by a drag that ended without its stop callback.
 */
internal fun FlowItem.Section?.isReorderTargetFor(draggedSection: FlowItem.Section?): Boolean =
    draggedSection == null || this == draggedSection

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
