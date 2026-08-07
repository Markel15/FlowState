package com.markel.flowstate.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.markel.flowstate.core.data.MainTab
import com.markel.flowstate.feature.settings.components.SettingsGroupShapes
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BottomNavConfigScreen(
    currentOrder: List<MainTab>,
    currentHidden: Set<MainTab>,
    onConfigChanged: (order: List<MainTab>, hidden: Set<MainTab>) -> Unit,
    onBack: () -> Unit
) {
    // Local mutable state initialized from the persisted values.
    val visibleTabs = remember(currentOrder, currentHidden) {
        mutableStateListOf<MainTab>().apply {
            addAll(currentOrder.filter { it !in currentHidden })
        }
    }
    val hiddenTabs = remember(currentOrder, currentHidden) {
        mutableStateListOf<MainTab>().apply {
            addAll(currentOrder.filter { it in currentHidden })
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Single LazyColumn for the whole screen — avoids nested scrolling crash
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Only allow reordering within the visible tabs section.
        // Visible tabs start after 1 header item, so offset by 1.
        val visibleStartOffset = 1
        val visibleEndExclusive = visibleStartOffset + visibleTabs.size

        if (from.index in visibleStartOffset until visibleEndExclusive &&
            to.index in visibleStartOffset until visibleEndExclusive
        ) {
            val fromVisibleIndex = from.index - visibleStartOffset
            val toVisibleIndex = to.index - visibleStartOffset
            visibleTabs.add(toVisibleIndex, visibleTabs.removeAt(fromVisibleIndex))
            val fullOrder = visibleTabs + hiddenTabs
            onConfigChanged(fullOrder, hiddenTabs.toSet())
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.bottom_nav_config_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.arrow_back_24px),
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 60.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ── Visible tabs header ────────────────────────────────
            item(key = "visible_header") {
                Text(
                    text = stringResource(R.string.bottom_nav_visible_tabs),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ── Visible tabs (reorderable) ─────────────────────────
            items(
                items = visibleTabs,
                key = { it.name }
            ) { tab ->
                val index = visibleTabs.indexOf(tab)
                ReorderableItem(reorderableState, key = tab.name) { isDragging ->
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.03f else 1f,
                        label = "tab_drag_scale"
                    )
                    val shape = when {
                        visibleTabs.size == 1 -> SettingsGroupShapes.singleItemShape
                        index == 0 -> SettingsGroupShapes.leadingItemShape
                        index == visibleTabs.lastIndex -> SettingsGroupShapes.endItemShape
                        else -> SettingsGroupShapes.middleItemShape
                    }

                    ListItem(
                        headlineContent = {
                            Text(
                                text = tabLabel(tab),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = ImageVector.vectorResource(tabIcon(tab)),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (tab.isRemovable) {
                                    Switch(
                                        checked = true,
                                        onCheckedChange = {
                                            visibleTabs.remove(tab)
                                            hiddenTabs.add(tab)
                                            val fullOrder = visibleTabs + hiddenTabs
                                            onConfigChanged(fullOrder, hiddenTabs.toSet())
                                        }
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.bottom_nav_required),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.drag_indicator_24px),
                                    contentDescription = stringResource(R.string.bottom_nav_drag_hint),
                                    modifier = Modifier.longPressDraggableHandle(
                                        interactionSource = remember { MutableInteractionSource() }
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = if (isDragging) 0.92f else 1f
                            }
                            .zIndex(if (isDragging) 1f else 0f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    )
                }
            }

            // ── Spacer between sections ────────────────────────────
            if (hiddenTabs.isNotEmpty()) {
                item(key = "section_spacer") {
                    Spacer(modifier = Modifier.padding(8.dp))
                }
            }

            // ── Hidden tabs header ─────────────────────────────────
            if (hiddenTabs.isNotEmpty()) {
                item(key = "hidden_header") {
                    Text(
                        text = stringResource(R.string.bottom_nav_hidden_tabs),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // ── Hidden tabs (non-reorderable, shown as a group) ─
                item(key = "hidden_group") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        hiddenTabs.forEachIndexed { index, tab ->
                            val shape = when {
                                hiddenTabs.size == 1 -> SettingsGroupShapes.singleItemShape
                                index == 0 -> SettingsGroupShapes.leadingItemShape
                                index == hiddenTabs.lastIndex -> SettingsGroupShapes.endItemShape
                                else -> SettingsGroupShapes.middleItemShape
                            }

                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = tabLabel(tab),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(tabIcon(tab)),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = false,
                                        onCheckedChange = {
                                            hiddenTabs.remove(tab)
                                            visibleTabs.add(tab)
                                            val fullOrder = visibleTabs + hiddenTabs
                                            onConfigChanged(fullOrder, hiddenTabs.toSet())
                                        }
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Returns a human-readable label for a [MainTab]. */
@Composable
private fun tabLabel(tab: MainTab): String = when (tab) {
    MainTab.TASKS -> stringResource(R.string.tab_flow)
    MainTab.CALENDAR -> stringResource(R.string.tab_calendar)
    MainTab.HABITS -> stringResource(R.string.tab_habits)
    MainTab.MOOD -> stringResource(R.string.tab_mood)
    MainTab.SETTINGS -> stringResource(R.string.tab_settings)
}

/** Returns the unselected icon drawable resource for a [MainTab]. */
private fun tabIcon(tab: MainTab): Int = when (tab) {
    MainTab.TASKS -> R.drawable.task_alt_24px
    MainTab.CALENDAR -> R.drawable.calendar_month_24px
    MainTab.HABITS -> R.drawable.analytics_24px
    MainTab.MOOD -> R.drawable.self_improvement_24px
    MainTab.SETTINGS -> R.drawable.settings_24px
}
