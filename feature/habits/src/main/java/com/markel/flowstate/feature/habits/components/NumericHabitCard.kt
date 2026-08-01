package com.markel.flowstate.feature.habits.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markel.flowstate.core.domain.HabitWithStatus
import com.markel.flowstate.feature.habits.R
import com.markel.flowstate.core.designsystem.R as DesignR
import java.time.DayOfWeek
import java.time.LocalDate
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.times
import com.markel.flowstate.core.domain.HabitNumericEntry
import com.markel.flowstate.feature.habits.util.formatFloat
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

@Composable
fun NumericHabitCard(
    habitWithStatus: HabitWithStatus,
    allEntries: List<HabitNumericEntry>,
    onIncrementToday: () -> Unit,
    onDecrementToday: () -> Unit,
    onSetValue: (LocalDate, Float?) -> Unit,
    onDelete: () -> Unit,
    onEdit: (name: String, icon: String, colorArgb: Int, unit: String?, targetValue: Float?, step: Float?) -> Unit,
    onNavigateToDetail: (() -> Unit)? = null
) {
    val habit = habitWithStatus.habit
    val habitColor = Color(habit.colorArgb)
    val today = LocalDate.now()
    val scope = rememberCoroutineScope()
    var weekOffset by remember { mutableIntStateOf(0) }  // O if actual week, -1 if is last week
    val weekStart = remember(weekOffset) {
        today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset.toLong())
    }

    // navigation limits
    val creationWeekStart = remember(habit.createdAt) {
        habit.createdAt.with(DayOfWeek.MONDAY)
    }
    val canGoBack = weekStart > creationWeekStart
    val canGoForward = weekOffset < 0  // don't allow going forward to the future

    // Build the visible week entries
    val entriesByDay = remember(allEntries) {
        allEntries.associateBy { it.date }
    }
    val currentWeekValues = remember(entriesByDay, weekStart) {
        (0L..6L).map { offset ->
            entriesByDay[weekStart.plusDays(offset)]?.value
        }
    }

    // selectedDate resets to today (if is this week) or monday when changing week view
    var selectedDate by remember(weekOffset) {
        mutableStateOf(if (weekOffset == 0) today else weekStart)
    }

    val selectedDayIndex = remember(selectedDate, weekStart) {
        ChronoUnit.DAYS.between(weekStart, selectedDate).toInt().coerceIn(0, 6)
    }

    val selectedValue = if (selectedDate.isAfter(today)) {
        null
    } else {
        currentWeekValues.getOrNull(selectedDayIndex)
    } ?: 0f

    val targetValue = habit.targetValue
    val isCompletedToday = habitWithStatus.isCompletedToday

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val cardBg by animateColorAsState(
        targetValue = if (isCompletedToday)
            habitColor.copy(alpha = 0.2f).compositeOver(surfaceColor)
        else
            surfaceColor,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "card_bg"
    )

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showInputDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 60f

    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_habit_dialog_title)) },
            text = { Text(stringResource(R.string.delete_habit_dialog_message, habit.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(
                        stringResource(R.string.delete_habit_confirm_button),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.delete_habit_cancel_button))
                }
            }
        )
    }

    // Edition sheet
    if (showEditDialog) {
        AddHabitSheet(
            initialName = habit.name,
            initialIcon = habit.iconName,
            initialColor = habitColor,
            initialHabitType = habit.habitType,
            initialUnit = habit.unit,
            initialTargetValue = habit.targetValue,
            initialStep = habit.step,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, icon, colorArgb, _, unit, target, step ->
                onEdit(name, icon, colorArgb, unit, target, step)
                showEditDialog = false
            }
        )
    }

    // Input dialog
    if (showInputDialog) {
        val currentValueForDialog = currentWeekValues.getOrNull(selectedDayIndex)
        NumericInputDialog(
            habitName = habit.name,
            unit = habit.unit,
            currentValue = currentValueForDialog,
            onDismiss = { showInputDialog = false },
            onConfirm = { value ->
                onSetValue(selectedDate, value)
                showInputDialog = false
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Top row ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = onNavigateToDetail != null,
                        onClick = { onNavigateToDetail?.invoke() }
                    )
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MorphingCheckButton(
                    isCompleted = isCompletedToday,
                    color = habitColor,
                    iconName = habit.iconName,
                    onClick = { } // Nothing to do here yet, only used as an indicator
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (targetValue != null) {
                        Text(
                            text = stringResource(
                                R.string.habit_target_preview,
                                formatFloat(targetValue),
                                habit.unit ?: ""
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Text(
                            "···",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 18.sp
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.habit_menu_edit)) },
                            onClick = {
                                menuExpanded = false
                                showEditDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_habit_confirm_button)) },
                            onClick = {
                                menuExpanded = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }

            // ── Week Bar graphic ──────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .pointerInput(canGoBack, canGoForward) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragAccumulator = 0f },
                            onDragEnd = { dragAccumulator = 0f },
                            onDragCancel = { dragAccumulator = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragAccumulator += dragAmount
                                when {
                                    dragAccumulator < -swipeThreshold && canGoForward -> {
                                        weekOffset++
                                        dragAccumulator = 0f
                                    }
                                    dragAccumulator > swipeThreshold && canGoBack -> {
                                        weekOffset--
                                        dragAccumulator = 0f
                                    }
                                }
                            }
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Calculate the max value for reference
                val maxWeekValue = currentWeekValues.filterNotNull().maxOrNull() ?: 1f
                val scaleReference = when {
                    targetValue != null -> maxOf(targetValue, maxWeekValue)
                    else -> maxWeekValue.coerceAtLeast(1f)
                }

                currentWeekValues.forEachIndexed { index, value ->
                    val date = weekStart.plusDays(index.toLong())
                    val isFuture = date.isAfter(today)
                    val isToday = date == today
                    val isSelected = date == selectedDate

                    NumericWeekBar(
                        value = if (isFuture) null else value,
                        targetValue = targetValue,
                        scaleReference = scaleReference,
                        color = habitColor,
                        date = date,
                        isToday = isToday,
                        isFuture = isFuture,
                        isSelected = isSelected,
                        onClick = {
                            if (!isFuture) {
                                selectedDate = date
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Controls ──────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                // Actual value (clickable to edit)
                Surface(
                    onClick = { showInputDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .height(42.dp)
                        .width(120.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = formatFloat(selectedValue),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (habit.unit != null) {
                            Text(
                                text = " ${habit.unit}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        Icon(
                            imageVector = ImageVector.vectorResource(DesignR.drawable.edit_24px),
                            contentDescription = "Edit value",
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ── Decrement button ──────────────────────────────────
                    val decScale = remember { Animatable(1f) }
                    val decShape = RoundedCornerShape(
                        topStart = CornerSize(50),
                        bottomStart = CornerSize(50),
                        topEnd = CornerSize(8.dp),
                        bottomEnd = CornerSize(8.dp)
                    )

                    FilledTonalIconButton(
                        onClick = {
                            scope.launch {
                                decScale.snapTo(0.88f)
                                decScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.20f,
                                        stiffness = 200f
                                    )
                                )
                            }
                            if (selectedDate == today) {
                                onDecrementToday()
                            } else {
                                val currentVal = currentWeekValues.getOrNull(selectedDayIndex) ?: 0f
                                val newVal = maxOf(0f, currentVal - habit.step)
                                onSetValue(selectedDate, if (newVal > 0f) newVal else null)
                            }
                        },
                        enabled = selectedValue > 0,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = habitColor.copy(alpha = 0.60f),
                            contentColor = habitColor
                        ),
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = decScale.value
                                scaleY = decScale.value
                                shape = decShape
                            }
                            .size(width = 46.dp, height = 42.dp),
                        shape = decShape
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(DesignR.drawable.remove_24px),
                            contentDescription = "Decrement",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // ── Increment button ──────────────────────────────────
                    val incScale = remember { Animatable(1f) }
                    val incShape = RoundedCornerShape(
                        topStart = CornerSize(8.dp),
                        bottomStart = CornerSize(8.dp),
                        topEnd = CornerSize(50),
                        bottomEnd = CornerSize(50)
                    )

                    FilledTonalIconButton(
                        onClick = {
                            scope.launch {
                                incScale.snapTo(0.88f)
                                incScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.20f,
                                        stiffness = 200f
                                    )
                                )
                            }
                            if (selectedDate == today) {
                                onIncrementToday()
                            } else {
                                val currentVal = currentWeekValues.getOrNull(selectedDayIndex) ?: 0f
                                val newVal = currentVal + habit.step
                                onSetValue(selectedDate, newVal)
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = habitColor.copy(alpha = 0.60f),
                            contentColor = habitColor
                        ),
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = incScale.value
                                scaleY = incScale.value
                                shape = incShape
                            }
                            .size(width = 46.dp, height = 42.dp),
                        shape = incShape
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.add_24px),
                            contentDescription = "Increment",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

        }
    }
}