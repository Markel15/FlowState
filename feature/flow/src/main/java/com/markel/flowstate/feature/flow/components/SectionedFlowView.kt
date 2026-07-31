package com.markel.flowstate.feature.flow.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.markel.flowstate.core.domain.CheckList
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.feature.flow.FlowUiState
import com.markel.flowstate.feature.flow.tasks.components.AnimatableTaskItem
import com.markel.flowstate.feature.flow.tasks.components.EmptyStateView
import com.markel.flowstate.feature.flow.tasks.components.ReminderPermissionBanner
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.markel.flowstate.feature.tasks.R
import kotlinx.coroutines.flow.StateFlow

/**
 * Main sectioned view for the Flow screen.
 *
 * Layout:
 *  - Tasks    → vertical reorderable list using [AnimatableTaskItem]
 *  - Lists    → horizontal reorderable carousel using [CheckListGridCard]
 *  - Ideas    → horizontal reorderable carousel using [IdeaGridCard] (taller cards)
 *
 * Future cross-section reordering:
 *  Replace per-section reorderable states with a unified drag state that tracks
 *  the source section and exposes a combined drop target across LazyList instances.
 */
@Composable
fun SectionedFlowView(
    uiState: FlowUiState,
    onScrolled: () -> Unit,
    // Task callbacks
    onTaskClick: (Task) -> Unit,
    onTaskDelete: (Task) -> Unit,
    onTaskToggle: (Task) -> Unit,
    onTaskReorder: (from: Int, to: Int) -> Unit,
    // Idea callbacks
    onIdeaClick: (Idea) -> Unit,
    onIdeaReorder: (from: Int, to: Int) -> Unit,
    // CheckList callbacks
    onCheckListClick: (CheckList) -> Unit,
    onCheckListReorder: (from: Int, to: Int) -> Unit,
    showPermissionBanner : Boolean,
    modifier: Modifier = Modifier,
    // Tracks per-task swipe-to-delete versions so that undo restores a fresh
    // SwipeToDismissBoxState instead of the stale EndToStart saved state.
    taskDeleteVersions: Map<Int, Int> = emptyMap(),
    categoriesEnabled: Boolean = false,
    // Hoisted from the caller (FlowScreen) so that the screen can derive
    // FAB visibility from the same LazyListState that drives this list.
    // See FlowScreen.kt → derivedStateOf { firstVisibleItemIndex == 0 && ... }
    outerListState: LazyListState = rememberLazyListState(),
) {
    if (uiState !is FlowUiState.Success) return

    // Empty state when all lists are empty
    val allEmpty = uiState.tasks.isEmpty()
            && uiState.checkLists.isEmpty()
            && uiState.ideas.isEmpty()

    if (allEmpty) {
        EmptyStateView(modifier = modifier.fillMaxSize())
        return
    }

    val reorderableState = rememberReorderableLazyListState(outerListState) { from, to ->
        val headerOffset = 2
        val fromTaskIndex = from.index - headerOffset
        val toTaskIndex = to.index - headerOffset

        if (fromTaskIndex in uiState.tasks.indices && toTaskIndex in uiState.tasks.indices) {
            onTaskReorder(fromTaskIndex, toTaskIndex)
        }
    }

    LaunchedEffect(outerListState) {
        snapshotFlow {
            outerListState.firstVisibleItemIndex > 0 || outerListState.firstVisibleItemScrollOffset > 90
        }.collect { scrolled -> if (scrolled) onScrolled() }
    }

    LazyColumn(
        state = outerListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // ── Tasks ──────────────────────────────────────────────────────────
        if (uiState.tasks.isNotEmpty()) {
            item(key = "tasks_header") {
                SectionHeader(
                    title = stringResource(R.string.tasks_m),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            item {
                ReminderPermissionBanner(showPermissionBanner)
            }
            itemsIndexed(
                items = uiState.tasks,
                key = { _, task ->
                    // Include the delete version in the key so that after an undo the LazyColumn creates a fresh composable
                    Pair(task.id, taskDeleteVersions[task.id] ?: 0)
                }
            ) { index, task ->
                val itemKey = Pair(task.id, taskDeleteVersions[task.id] ?: 0)
                val itemShape = when {
                    uiState.tasks.size == 1 -> RoundedCornerShape(16.dp)
                    index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                    index == uiState.tasks.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                    else -> RoundedCornerShape(4.dp)
                }
                ReorderableItem(
                    reorderableState,
                    key = itemKey,
                    animateItemModifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 520f
                        ),
                        fadeOutSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)
                    )
                ) { isDragging ->
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.03f else 1f,
                        label = "task_drag_scale"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 1.dp)
                            .longPressDraggableHandle(
                                interactionSource = remember { MutableInteractionSource() }
                            )
                            .graphicsLayer {
                                scaleX = scale; scaleY = scale
                                alpha = if (isDragging) 0.92f else 1f
                            }
                            .zIndex(if (isDragging) 1f else 0f)
                    ) {
                        AnimatableTaskItem(
                            task = task,
                            shape = itemShape,
                            onDelete = { onTaskDelete(task) },
                            onComplete = { onTaskToggle(task) },
                            onContentClick = { onTaskClick(task) }
                        )
                    }
                }
            }
        }

        // ── CheckLists ─────────────────────────────────────────────────────
        if (uiState.checkLists.isNotEmpty()) {
            item(key = "checklists_header") {
                SectionHeader(
                    title = stringResource(R.string.checklists_m),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
                )
            }
            item(key = "checklists_carousel") {
                ReorderableCarousel(
                    items = uiState.checkLists,
                    key = { it.id },
                    onReorder = onCheckListReorder
                ) { checkList ->
                    CheckListGridCard(
                        checkList = checkList,
                        onClick = { onCheckListClick(checkList) },
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        }

        // ── Ideas ──────────────────────────────────────────────────────────
        if (uiState.ideas.isNotEmpty()) {
            item(key = "ideas_header") {
                SectionHeader(
                    title = stringResource(R.string.ideas_notes_m),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
                )
            }
            item(key = "ideas_carousel") {
                ReorderableCarousel(
                    items = uiState.ideas,
                    key = { it.id },
                    onReorder = onIdeaReorder
                ) { idea ->
                    // Taller modifier to visually differentiate from checklist cards
                    IdeaGridCard(
                        idea = idea,
                        onClick = { onIdeaClick(idea) },
                        modifier = Modifier
                            .width(200.dp)
                            .height(160.dp)
                    )
                }
            }
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