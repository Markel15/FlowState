package com.markel.flowstate.feature.flow.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.feature.tasks.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * A dismissable bottom sheet that lets the user reorder categories and access
 * per-category actions.
 *
 * Behavior:
 *  - Drag the [drag_indicator] handle to reorder. The new order is reported
 *    through [onReorder] on every drop and the caller is responsible for
 *    persisting it (typically `FlowViewModel.reorderCategories`).
 *  - Tap the row (or the trailing [arrow_forward] icon) to select that
 *    category: fires [onCategorySelected] with the category id. The caller
 *    is responsible for both updating the selected category AND dismissing
 *    the sheet.
 *  - All categories are reorderable, including General (id =
 *    [Category.GENERAL_ID]). The caller passes the full list.
 *  - The sheet is fully dismissable: drag-down, scrim tap, or back gesture.
 *
 * The local list is the source of truth while the sheet is open: we
 * optimistically reorder the in-memory copy on every drag and notify the
 * caller, so the list feels responsive even if the DB write is slow. We also
 * re-sync from [categories] whenever the caller's list changes (e.g. when a
 * category is deleted from the settings screen).
 *
 * @param categories current categories (including General, ordered by position).
 * @param onReorder  invoked with the new full ordered list after every drop.
 * @param onCategorySelected invoked when the user taps a row / arrow.
 * @param onDismiss  invoked when the sheet is dismissed by the user.
 * @param generalTabName user-customized display name for the General category,
 *   or null to use the localized default. Honored when rendering the General
 *   row, so a renamed General shows up correctly here too (not just in the
 *   tab row and the Settings screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderCategoriesSheet(
    categories: List<Category>,
    onReorder: (List<Category>) -> Unit,
    onCategorySelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    generalTabName: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local mutable copy so the drag feels instantaneous. Re-synced from the
    // caller's [categories] whenever that reference changes (e.g. after
    // deleted) — see the LaunchedEffect below.
    var localCategories by remember { mutableStateOf(categories) }
    LaunchedEffect(categories) {
        if (localCategories.map { it.id } != categories.map { it.id }) {
            localCategories = categories
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val current = localCategories.toMutableList()
        if (from.index !in current.indices || to.index !in current.indices) return@rememberReorderableLazyListState
        val moved = current.removeAt(from.index)
        current.add(to.index, moved)
        localCategories = current
        onReorder(current)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        // ── Title ───────────────
        Text(
            text = stringResource(R.string.categories_reorder_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(items = localCategories, key = { it.id }) { category ->
                ReorderableItem(reorderableState, key = category.id) { isDragging ->
                    // Create the handle modifier inside the proper scope
                    val dragHandleModifier = Modifier.longPressDraggableHandle(
                        interactionSource = remember { MutableInteractionSource() }
                    )
                    val displayName = if (category.id == Category.GENERAL_ID) {
                        generalTabName?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.category_general)
                    } else {
                        category.name
                    }
                    ReorderableCategoryRow(
                        name = displayName,
                        isDragging = isDragging,
                        onClick = { onCategorySelected(category.id) },
                        dragHandleModifier = dragHandleModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderableCategoryRow(
    name: String,
    isDragging: Boolean,
    onClick: () -> Unit,
    dragHandleModifier: Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Tapping anywhere on the row triggers the "edit" action.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = if (isDragging) 1.03f else 1f
                scaleY = if (isDragging) 1.03f else 1f
                alpha = if (isDragging) 0.92f else 1f
            }
            .zIndex(if (isDragging) 1f else 0f)
    ) {
        // Drag handle (long-press to start dragging, mirrors the Flow grid)
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.drag_handle_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .size(28.dp)
                .then(dragHandleModifier)
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        // Right arrow → open detail
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.arrow_circle_right_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
