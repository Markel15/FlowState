package com.markel.flowstate.feature.habits

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.animateFloatingActionButton
import com.markel.flowstate.core.designsystem.ui.rememberFabVisibilityState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.domain.HabitType
import com.markel.flowstate.feature.habits.components.AddHabitSheet
import com.markel.flowstate.feature.habits.components.HabitCard
import com.markel.flowstate.feature.habits.components.HabitEmptyState
import com.markel.flowstate.feature.habits.components.HabitFabMenu
import com.markel.flowstate.feature.habits.components.NumericHabitCard
import com.markel.flowstate.feature.habits.details.components.HabitHeader
import com.markel.flowstate.feature.habits.details.components.MotivationalMessage
import java.time.LocalDate
import com.markel.flowstate.core.designsystem.R as DesignR
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HabitScreen(
    viewModel: HabitViewModel = hiltViewModel(),
    onNavigateToDetail: (habitId: Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        when (val state = uiState) {
            is HabitUiState.Loading -> Unit

            is HabitUiState.Success -> {
                val keywords = stringArrayResource(R.array.motivational_keywords)
                val rests = stringArrayResource(R.array.motivational_rests)
                val message = MotivationalMessage(
                    keyword = keywords[state.motivationalMessageIndex],
                    rest = rests[state.motivationalMessageIndex]
                )

                // ── Scroll-aware FAB visibility ────────────────────────
                // When there are no habits, there is no list to
                // scroll, so we force the FAB to stay visible.
                val listState = rememberLazyListState()
                val fabVisible by rememberFabVisibilityState(
                    lazyListState = listState,
                    forceVisible = state.habits.isEmpty()
                )

                // ── FAB menu state: type is chosen here, so the upsert sheet
                // renders a form specific to that type (no in-sheet selector).
                var fabMenuExpanded by remember { mutableStateOf(false) }
                var addSheetType by remember { mutableStateOf(HabitType.BOOLEAN) }

                Column(modifier = Modifier.fillMaxSize()) {
                    HabitHeader(
                        completedToday = state.completedToday,
                        totalHabits = state.totalHabits,
                        motivationalMessage = message
                    )
                    if (state.habits.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            HabitEmptyState()
                        }
                    } else {
                        val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                            viewModel.onReorder(from.index, to.index)
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.habits, key = { it.habit.id }) { habitWithStatus ->
                                ReorderableItem(reorderableState, key = habitWithStatus.habit.id) { isDragging ->

                                    val scale by animateFloatAsState(
                                        targetValue = if (isDragging) 1.05f else 1.0f,
                                        label = "drag_scale"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .longPressDraggableHandle(
                                                interactionSource = remember { MutableInteractionSource() }
                                            )
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                                alpha = if (isDragging) 0.9f else 1.0f
                                            }
                                            .zIndex(if (isDragging) 1f else 0f)
                                    ) {

                                        when (habitWithStatus.habit.habitType) {
                                            HabitType.BOOLEAN -> {
                                                HabitCard(
                                                    habitWithStatus = habitWithStatus,
                                                    weekEntries = state.weekEntriesByHabit[habitWithStatus.habit.id]
                                                        ?: emptySet(),
                                                    onToggleDay = { date ->
                                                        viewModel.toggleBooleanHabitOnDate(
                                                            habitWithStatus.habit.id,
                                                            date
                                                        )
                                                    },
                                                    onDelete = {
                                                        viewModel.deleteHabit(
                                                            habitWithStatus.habit
                                                        )
                                                    },
                                                    onEdit = { name, icon, colorArgb ->
                                                        viewModel.editHabit(
                                                            habitWithStatus.habit,
                                                            name,
                                                            icon,
                                                            colorArgb
                                                        )
                                                    },
                                                    onNavigateToDetail = {
                                                        onNavigateToDetail(
                                                            habitWithStatus.habit.id
                                                        )
                                                    }
                                                )
                                            }

                                            HabitType.NUMERIC -> {
                                                NumericHabitCard(
                                                    habitWithStatus = habitWithStatus,
                                                    allEntries = state.numericEntriesByHabit[habitWithStatus.habit.id]
                                                        ?: emptyList(),
                                                    onIncrementToday = {
                                                        viewModel.incrementNumericHabit(
                                                            habitId = habitWithStatus.habit.id,
                                                            date = LocalDate.now(),
                                                            currentValue = habitWithStatus.todayValue,
                                                            step = habitWithStatus.habit.step
                                                        )
                                                    },
                                                    onDecrementToday = {
                                                        viewModel.decrementNumericHabit(
                                                            habitId = habitWithStatus.habit.id,
                                                            date = LocalDate.now(),
                                                            currentValue = habitWithStatus.todayValue,
                                                            step = habitWithStatus.habit.step
                                                        )
                                                    },
                                                    onSetValue = { date, value ->
                                                        if (value != null) {
                                                            viewModel.setNumericValue(
                                                                habitId = habitWithStatus.habit.id,
                                                                date = date,
                                                                value = value
                                                            )
                                                        } else {
                                                            viewModel.deleteNumericEntry(
                                                                habitId = habitWithStatus.habit.id,
                                                                date = date
                                                            )
                                                        }
                                                    },
                                                    onDelete = {
                                                        viewModel.deleteHabit(
                                                            habitWithStatus.habit
                                                        )
                                                    },
                                                    onEdit = { name, icon, colorArgb, unit, targetValue, step ->
                                                        viewModel.editHabit(
                                                            habitWithStatus.habit,
                                                            name,
                                                            icon,
                                                            colorArgb,
                                                            unit,
                                                            targetValue,
                                                            step
                                                        )
                                                    },
                                                    onNavigateToDetail = {
                                                        onNavigateToDetail(
                                                            habitWithStatus.habit.id
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.showAddDialog) {
                    AddHabitSheet(
                        initialHabitType = addSheetType,
                        onDismiss = { viewModel.hideAddDialog() },
                        onConfirm = { name, icon, color, habitType, unit, targetValue, step ->
                            viewModel.addHabit(name, icon, color, habitType, unit, targetValue, step)
                        }
                    )
                }

                HabitFabMenu(
                    expanded = fabMenuExpanded,
                    onToggle = { fabMenuExpanded = !fabMenuExpanded },
                    onBooleanHabitClick = {
                        addSheetType = HabitType.BOOLEAN
                        fabMenuExpanded = false
                        viewModel.showAddDialog()
                    },
                    onNumericHabitClick = {
                        addSheetType = HabitType.NUMERIC
                        fabMenuExpanded = false
                        viewModel.showAddDialog()
                    },
                    visible = fabVisible,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .zIndex(1f)
                )
            }
        }
    }
}