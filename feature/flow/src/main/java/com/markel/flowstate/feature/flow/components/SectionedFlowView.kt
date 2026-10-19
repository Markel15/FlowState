package com.markel.flowstate.feature.flow.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.markel.flowstate.core.domain.CheckList
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.feature.flow.FlowItem
import com.markel.flowstate.feature.flow.FlowUiState
import com.markel.flowstate.feature.flow.isReorderTargetFor
import com.markel.flowstate.feature.flow.stableKey
import com.markel.flowstate.feature.flow.tasks.components.AnimatableTaskItem
import com.markel.flowstate.feature.flow.tasks.components.EmptyStateView
import com.markel.flowstate.feature.flow.tasks.components.ReminderPermissionBanner
import com.markel.flowstate.feature.tasks.R
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyStaggeredGridState
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

/** Minimum card width. ~390dp screens → 2 columns; tablets/landscape → 3–4. */
private const val GRID_MIN_COLUMN_DP = 170
private const val GRID_SPACING_DP = 10
private const val GRID_EDGE_DP = 12
/** Top breathing room, applied to the first row instead of the container (see below). */
private const val GRID_TOP_DP = 8
/** Extra inset for headers*/
private const val ROW_EXTRA_INSET_DP = 8
/** Gap above a section header when another section precedes it. */
private const val SECTION_GAP_DP = 24
/** Gap between a section header and the first row or card it introduces. */
private const val SECTION_HEADER_BOTTOM_DP = 12
/** Bottom room for the FAB, rendered as [FlowItem.BottomSpacer] (see below). */
private const val GRID_BOTTOM_DP = 96
/**
 * Vertical gap between grid cards (checklists / ideas).
 *
 * The grid itself uses `verticalItemSpacing = 0` because that value applies to
 * *every* row — including the task rows, which must stay tightly grouped
 * Cards get this gap via bottom padding instead.
 */
private const val CARD_VERTICAL_SPACING_DP = 9

/**
 * Main view for the Flow screen: a single staggered grid.
 *
 * Layout:
 *  - Tasks      → full-width rows using [AnimatableTaskItem] (swipe-to-dismiss)
 *  - Ideas      → cards, one per column, height driven by their content
 *  - CheckLists → cards, one per column, height driven by their content
 *
 * Everything (headers included) is emitted from one [FlowItem] list, which is
 * what lets the index reported by `reorderable` map 1:1 onto `items[index]`.
 * Full-width rows opt in via [FlowItem.spanAllColumns]; cards take a single lane.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionedFlowView(
    uiState: FlowUiState,
    onScrolled: () -> Unit,
    onItemReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onReorderCommit: () -> Unit,
    // Task callbacks
    onTaskClick: (Task) -> Unit,
    onTaskDelete: (Task) -> Unit,
    onTaskToggle: (Task) -> Unit,
    // Idea / CheckList callbacks
    onIdeaClick: (Idea) -> Unit,
    onCheckListClick: (CheckList) -> Unit,
    showPermissionBanner: Boolean,
    modifier: Modifier = Modifier,
    // Tracks per-task swipe-to-delete versions so that undo restores a fresh
    // SwipeToDismissBoxState instead of the stale EndToStart saved state.
    taskDeleteVersions: Map<Int, Int> = emptyMap(),
    // Hoisted from the caller (FlowScreen) so that the screen can derive
    // FAB visibility from the same state that drives this grid.
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    if (uiState !is FlowUiState.Success) return

    if (uiState.items.isEmpty()) {
        EmptyStateView(modifier = modifier.fillMaxSize())
        return
    }

    val reorderState = rememberReorderableLazyStaggeredGridState(
        gridState,
        scrollThresholdPadding = WindowInsets.systemBars.asPaddingValues(),
    ) { from, to -> onItemReorder(from.index, to.index) }

    // Section the current drag started in; null while nothing is being dragged.
    // It gates `ReorderableItem.enabled` so the library can never request a
    // cross-section swap, which it cannot undo. See [isReorderTargetFor].
    var draggedSection by remember { mutableStateOf<FlowItem.Section?>(null) }

    val isAnyItemDragging = reorderState.isAnyItemDragging
    LaunchedEffect(isAnyItemDragging) {
        if (!isAnyItemDragging) {
            draggedSection = null
            onReorderCommit()  // let the ViewModel persist the order in one go.
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 90
        }.collect { scrolled -> if (scrolled) onScrolled() }
    }

    // Index of each task inside its own section, used to keep the rounded outer
    // corners of the first / last / only task row.
    val taskPositions = remember(uiState.tasks) {
        uiState.tasks.mapIndexed { index, task -> task.id to index }.toMap()
    }

    val itemPlacementSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 520f,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )
    val itemFadeInSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
    val itemFadeOutSpec = tween<Float>(durationMillis = 130, easing = FastOutLinearInEasing)

    Column(modifier = modifier.fillMaxSize()) {
        // ── The banner is pinned above the grid, not an item of it ───────────────
        ReminderPermissionBanner(
            visible = showPermissionBanner,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp),
        )

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(minSize = GRID_MIN_COLUMN_DP.dp),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // ── Keep the *main-axis* (top / bottom) content padding at 0 ─────────────
            contentPadding = PaddingValues(
                start = GRID_EDGE_DP.dp,
                end = GRID_EDGE_DP.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING_DP.dp),
            verticalItemSpacing = 0.dp,
        ) {
            itemsIndexed(
                items = uiState.items,
                key = { _, item -> item.stableKey(taskDeleteVersions) },
                span = { _, item ->
                    if (item.spanAllColumns) StaggeredGridItemSpan.FullLine
                    else StaggeredGridItemSpan.SingleLane
                },
            ) { index, item ->
                val animateItemModifier = Modifier.animateItem(
                    fadeInSpec = itemFadeInSpec,
                    placementSpec = itemPlacementSpec,
                    fadeOutSpec = itemFadeOutSpec
                )

                // Top gap of the whole grid, applied to its first row (always a header)
                // instead of to contentPadding. See the comment on contentPadding above.
                val topInsetModifier =
                    if (index == 0) Modifier.padding(top = GRID_TOP_DP.dp) else Modifier
                val itemModifier = animateItemModifier.then(topInsetModifier)

                // Room above a header that may have another section above it.
                val sectionTopPadding = if (index == 0) 8.dp else SECTION_GAP_DP.dp

                when (item) {
                    FlowItem.TasksHeader -> SectionHeader(
                        title = stringResource(R.string.tasks_m),
                        modifier = itemModifier
                            .padding(horizontal = ROW_EXTRA_INSET_DP.dp, vertical = 8.dp)
                    )

                    FlowItem.IdeasHeader -> SectionHeader(
                        title = stringResource(R.string.ideas_notes_m),
                        modifier = itemModifier
                            .padding(start = ROW_EXTRA_INSET_DP.dp, end= ROW_EXTRA_INSET_DP.dp, top = sectionTopPadding, bottom = SECTION_HEADER_BOTTOM_DP.dp)
                    )

                    FlowItem.CheckListsHeader -> SectionHeader(
                        title = stringResource(R.string.checklists_m),
                        modifier = itemModifier
                            .padding(start = ROW_EXTRA_INSET_DP.dp, end= ROW_EXTRA_INSET_DP.dp,  top = sectionTopPadding, bottom = SECTION_HEADER_BOTTOM_DP.dp)
                    )

                    is FlowItem.TaskRow -> {
                        val taskIndex = taskPositions[item.task.id] ?: 0
                        TaskRowContent(
                            reorderState = reorderState,
                            key = item.stableKey(taskDeleteVersions),
                            animateItemModifier = itemModifier,
                            section = FlowItem.Section.TASKS,
                            draggedSection = draggedSection,
                            onDraggedSectionChanged = { draggedSection = it },
                            task = item.task,
                            shape = taskRowShape(taskIndex, uiState.tasks.size),
                            onDelete = { onTaskDelete(item.task) },
                            onComplete = { onTaskToggle(item.task) },
                            onContentClick = { onTaskClick(item.task) },
                        )
                    }

                    is FlowItem.CheckListCard -> ReorderableCard(
                        reorderState = reorderState,
                        key = item.stableKey(taskDeleteVersions),
                        animateItemModifier = itemModifier,
                        section = FlowItem.Section.CHECKLISTS,
                        draggedSection = draggedSection,
                        onDraggedSectionChanged = { draggedSection = it },
                    ) {
                        CheckListGridCard(
                            checkList = item.checkList,
                            onClick = { onCheckListClick(item.checkList) },
                            modifier = Modifier.padding(bottom = CARD_VERTICAL_SPACING_DP.dp)
                        )
                    }

                    is FlowItem.IdeaCard -> ReorderableCard(
                        reorderState = reorderState,
                        key = item.stableKey(taskDeleteVersions),
                        animateItemModifier = itemModifier,
                        section = FlowItem.Section.IDEAS,
                        draggedSection = draggedSection,
                        onDraggedSectionChanged = { draggedSection = it },
                    ) {
                        IdeaGridCard(
                            idea = item.idea,
                            onClick = { onIdeaClick(item.idea) },
                            modifier = Modifier.padding(bottom = CARD_VERTICAL_SPACING_DP.dp)
                        )
                    }

                    // No animateItem: an empty spacer has nothing to animate, and
                    // keeping it out of the animator makes the bottom bound stable.
                    FlowItem.BottomSpacer -> Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(GRID_BOTTOM_DP.dp)
                    )
                }
            }
        }
    }
}

/** Outer-corner rounding for the tasks block */
private fun taskRowShape(index: Int, size: Int) = when {
    size == 1 -> RoundedCornerShape(16.dp)
    index == 0 -> RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp
    )
    index == size - 1 -> RoundedCornerShape(
        topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp
    )
    else -> RoundedCornerShape(4.dp)
}

// ── Reorderable rows ──────────────────────────────────────────────────────────

/**
 * Shared shell for every draggable row: long-press anywhere on the item starts
 * the drag, and the item lifts, scales and fades slightly while it is held.
 *
 * [section] / [draggedSection] keep the drag inside its own section: a row is
 * only handed to the library as a swap target while the dragged row comes from
 * the same section. [onDraggedSectionChanged] publishes the section on drag start
 * and clears it on drag stop.
 */
@Composable
private fun LazyStaggeredGridItemScope.ReorderableCard(
    reorderState: ReorderableLazyStaggeredGridState,
    key: Any,
    animateItemModifier: Modifier,
    section: FlowItem.Section,
    draggedSection: FlowItem.Section?,
    onDraggedSectionChanged: (FlowItem.Section?) -> Unit,
    content: @Composable ReorderableCollectionItemScope.(isDragging: Boolean) -> Unit,
) {
    ReorderableItem(
        state = reorderState,
        key = key,
        enabled = section.isReorderTargetFor(draggedSection),
        animateItemModifier = animateItemModifier,
    ) { isDragging ->
        val collectionScope = this
        val scale by animateFloatAsState(
            targetValue = if (isDragging) 1.04f else 1f,
            label = "grid_drag_scale"
        )
        Box(
            modifier = Modifier
                .longPressDraggableHandle(
                    interactionSource = remember { MutableInteractionSource() },
                    onDragStarted = { onDraggedSectionChanged(section) },
                    onDragStopped = { onDraggedSectionChanged(null) },
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (isDragging) 0.92f else 1f
                }
                .zIndex(if (isDragging) 1f else 0f)
        ) {
            collectionScope.content(isDragging)
        }
    }
}

@Composable
private fun LazyStaggeredGridItemScope.TaskRowContent(
    reorderState: ReorderableLazyStaggeredGridState,
    key: Any,
    animateItemModifier: Modifier,
    section: FlowItem.Section,
    draggedSection: FlowItem.Section?,
    onDraggedSectionChanged: (FlowItem.Section?) -> Unit,
    task: Task,
    shape: RoundedCornerShape,
    onDelete: () -> Unit,
    onComplete: () -> Unit,
    onContentClick: () -> Unit,
) {
    ReorderableCard(
        reorderState = reorderState,
        key = key,
        animateItemModifier = animateItemModifier,
        section = section,
        draggedSection = draggedSection,
        onDraggedSectionChanged = onDraggedSectionChanged,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ROW_EXTRA_INSET_DP.dp, vertical = 1.dp)
        ) {
            AnimatableTaskItem(
                task = task,
                shape = shape,
                onDelete = onDelete,
                onComplete = onComplete,
                onContentClick = onContentClick,
            )
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}