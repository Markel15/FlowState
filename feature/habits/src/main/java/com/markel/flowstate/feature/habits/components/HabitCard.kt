package com.markel.flowstate.feature.habits.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource // Added import
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.markel.flowstate.core.domain.HabitWithStatus
import com.markel.flowstate.core.domain.isScheduledFor
import com.markel.flowstate.feature.habits.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun HabitCard(
    habitWithStatus: HabitWithStatus,
    weekEntries: Set<Long>,
    onToggleDay: (LocalDate) -> Unit,
    onDelete: () -> Unit,
    onEdit: (
        name: String,
        icon: String,
        colorArgb: Int,
        scheduledDays: Set<DayOfWeek>
    ) -> Unit,
    onNavigateToDetail: (() -> Unit)? = null
) {
    val habit = habitWithStatus.habit
    val habitColor = Color(habit.colorArgb)
    val today = LocalDate.now()
    val isScheduledToday = habit.isScheduledFor(today)
    val isCompletedToday = isScheduledToday && today.toEpochDay() in weekEntries

    // State for the WeekCalendar — starts in the current week,
    // can scroll back to the beginning of the habit
    val weekState = rememberWeekCalendarState(
        startDate = java.time.LocalDate.parse(habit.createdAt.toString()),
        endDate = today,
        firstVisibleWeekDate = today,
        firstDayOfWeek = DayOfWeek.MONDAY
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val cardBg by animateColorAsState(
        targetValue = if (isCompletedToday)
            habitColor.copy(alpha = 0.2f).compositeOver(surfaceColor)
        else
            surfaceColor,
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            ),
        label = "card_bg"
    )

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog   by remember { mutableStateOf(false) }
    var menuExpanded     by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_habit_dialog_title)) },
            text = { Text(stringResource(R.string.delete_habit_dialog_message, habit.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(stringResource(R.string.delete_habit_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.delete_habit_cancel_button)) }
            }
        )
    }

    // ── Edit sheet (reuses AddHabitSheet in edit mode) ──────────────────────
    if (showEditDialog) {
        AddHabitSheet(
            initialName = habit.name,
            initialIcon = habit.iconName,
            initialColor = habitColor,
            initialScheduledDays = habit.scheduledDays,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, icon, colorArgb, scheduledDays ->
                onEdit(name, icon, colorArgb, scheduledDays)
                showEditDialog = false
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
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
                    onClick = { onToggleDay(today) },
                    enabled = isScheduledToday
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(repeatDelayMillis = 3500)
                    )
                    if (!isScheduledToday) {
                        Text(
                            text = stringResource(R.string.habit_not_scheduled_today),
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
                                menuExpanded  = false
                                showEditDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.delete_habit_confirm_button),)
                            },
                            onClick = {
                                menuExpanded   = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }

            // ── WeekCalendar ────────────────────────
            WeekCalendar(
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                state = weekState,
                dayContent = { weekDay ->
                    val date = weekDay.date
                    val isDone = date.toEpochDay() in weekEntries
                    val isFuture = date.isAfter(today)
                    val isToday = date == today
                    val isScheduled = habit.isScheduledFor(date)

                    // Connection with adjacent days
                    val donePrev = date.minusDays(1).toEpochDay() in weekEntries
                    val doneNext = date.plusDays(1).toEpochDay() in weekEntries

                    val cornerStart by animateDpAsState(
                        targetValue = when {
                            isDone && donePrev -> 0.dp
                            else -> 20.dp
                        },
                        animationSpec = spring(stiffness = 400f),
                        label = "corner_start_${date.dayOfMonth}"
                    )
                    val cornerEnd by animateDpAsState(
                        targetValue = when {
                            isDone && doneNext -> 0.dp
                            else -> 20.dp
                        },
                        animationSpec = spring(stiffness = 400f),
                        label = "corner_end_${date.dayOfMonth}"
                    )

                    val animatedShape = RoundedCornerShape(
                        topStart = cornerStart,
                        bottomStart = cornerStart,
                        topEnd = cornerEnd,
                        bottomEnd = cornerEnd
                    )

                    val bgColor by animateColorAsState(
                        targetValue = if (isDone && !isFuture) habitColor else Color.Transparent,
                        animationSpec = spring(stiffness = 400f),
                        label = "day_bg_${date.dayOfMonth}"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(animatedShape)
                            .background(bgColor)
                            .then(
                                if (!isFuture && isScheduled) Modifier.clickable(
                                    role = Role.Button,
                                    onClick = { onToggleDay(date) }
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ){
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isDone && !isFuture -> Color.White
                                    isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    !isScheduled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    isToday -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = date.dayOfWeek
                                    .getDisplayName(java.time.format.TextStyle.SHORT, LocalLocale.current.platformLocale)
                                    .uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 10.sp,
                                color = when {
                                    isDone && !isFuture -> Color.White.copy(alpha = 0.8f)
                                    isFuture || !isScheduled ->
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                }
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}