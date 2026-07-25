package com.markel.flowstate.feature.flow.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.feature.tasks.R

/**
 * Sentinel id used for the trailing "+ New category" tab.
 *
 * It must never collide with a real [com.markel.flowstate.core.domain.Category.id]
 * (Room ids start at 1), so [Int.MIN_VALUE] is safe.
 */
private const val NEW_CATEGORY_TAB_ID = Int.MIN_VALUE

/**
 * Primary scrollable tab row for categories
 * Tabs change ONLY by click (no swipe) to avoid conflict with swipe-to-delete.
 *
 * The row always renders, in this order:
 *  1. One tab per category (the first is always "General" — id = [Category.GENERAL_ID] —
 *     which shows items in the default category)
 *  2. "+ New category" trailing tab (text-only, opens the creation dialog)
 *
 * General is a real row in the `categories` table (id=1, position=0), so it
 * appears naturally as the first element of the list — no manual prepending.
 * Its display name can be overridden by the user via [generalTabName]; if that
 * is null/blank, the localized default [R.string.category_general] is used.
 * Long-pressing any tab (except "+ New") fires [onCategoryLongPress], which the
 * caller typically uses to open the "Reorder categories" sheet.
 *
 * Each user-category tab also renders a small badge with the number of pending
 * (not-done) tasks in that category, taken from [pendingTaskCounts]. The
 * "+ New category" tab never shows a badge — it is an action, not a category.
 *
 * @param pendingTaskCounts map of categoryId → pending task count. Entries with
 *   a count of 0 (or missing keys) render no badge.
 *
 * @param onAddCategoryClick invoked when the trailing "+ New category" tab is pressed.
 * @param onCategoryLongPress invoked when any non-"+ New" tab is long-pressed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryTabRow(
    categories: List<Category>,
    selectedCategoryId: Int?,
    onCategorySelected: (Int?) -> Unit,
    onAddCategoryClick: () -> Unit,
    onCategoryLongPress: () -> Unit,
    pendingTaskCounts: Map<Int?, Int> = emptyMap(),
    generalTabName: String? = null
) {
    // Build the list of tabs: user categories (General is first, id=GENERAL_ID) + "New"
    val tabItems = remember(categories) {
        buildList {
            categories.forEach { cat ->
                add(cat.id to cat.name)
            }
            // Trailing "+ New category" action tab — never selectable
            add(NEW_CATEGORY_TAB_ID to null)
        }
    }

    val selectedIndex = tabItems.indexOfFirst { it.first == selectedCategoryId }
        .coerceAtLeast(0)

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 8.dp,
        minTabWidth = 52.dp,
        divider = {}, // No divider
        indicator = {
            // Only draw the indicator when the selected tab is a real category
            // (i.e. not the trailing "+ New category" action tab).
            if (selectedIndex in tabItems.indices && tabItems[selectedIndex].first != NEW_CATEGORY_TAB_ID) {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        selectedTabIndex = selectedIndex,
                        matchContentSize = true
                    ),
                    height = 3.dp,
                    width = Dp.Unspecified,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                )
            }
        }
    ) {
        tabItems.forEachIndexed { index, (catId, name) ->
            val isNewCategoryTab = catId == NEW_CATEGORY_TAB_ID
            val isSelected = !isNewCategoryTab && selectedIndex == index

            val interactionSource = remember { MutableInteractionSource() }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            val clickAction: (() -> Unit)? = if (isNewCategoryTab) {
                onAddCategoryClick
            } else {
                { onCategorySelected(catId) }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .wrapContentWidth()
                    .height(46.dp)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        onClick = { clickAction?.invoke() },
                        onLongClick = if (!isNewCategoryTab) {
                            { onCategoryLongPress() }
                        } else null
                    )
                    .padding(horizontal = 16.dp)
            ) {
                // Text tab: General (uses DataStore-overridable name) OR user category OR "+ New category"
                val tabLabel = when {
                    isNewCategoryTab -> stringResource(R.string.categories_trail)
                    catId == Category.GENERAL_ID -> generalTabName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.category_general)
                    else -> name!!
                }
                val pendingCount = if (!isNewCategoryTab) {
                    pendingTaskCounts[catId] ?: 0
                } else 0

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tabLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                    if (pendingCount > 0 && !isSelected) {
                        Spacer(modifier = Modifier.size(6.dp))
                        PendingCountBadge(
                            count = pendingCount,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Small pill-shaped badge that shows a pending-task count next to a category
 * tab label. Styled after Google Tasks: subtle background, monospace-ish
 * number, no border.
 *
 * Rendered only when [count] > 0 — callers are responsible for that check
 * (avoids composing an empty box).
 */
@Composable
private fun PendingCountBadge(
    count: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(color = containerColor, shape = CircleShape)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

