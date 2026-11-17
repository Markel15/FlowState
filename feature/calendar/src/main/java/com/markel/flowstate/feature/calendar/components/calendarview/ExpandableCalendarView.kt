package com.markel.flowstate.feature.calendar.components.calendarview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.kizitonwose.calendar.compose.CalendarState
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState
import com.markel.flowstate.core.domain.Task
import java.time.LocalDate

@Composable
fun ExpandableCalendarView(
    isExpanded: Boolean,
    monthState: CalendarState,
    weekState: WeekCalendarState,
    tasksByDate: Map<LocalDate, List<Task>>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    today: LocalDate = LocalDate.now()
) {
    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically(
            animationSpec = tween(durationMillis = 250),
            expandFrom = Alignment.Top
        ),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 250),
            shrinkTowards = Alignment.Top
        )
    ) {
        HorizontalCalendar(
            state = monthState,
            dayContent = { day ->
                val tasksForDay = tasksByDate[day.date] ?: emptyList()
                val isSelected = selectedDate == day.date

                DayCell(
                    day = day,
                    isSelected = isSelected,
                    hasTasks = tasksForDay.isNotEmpty(),
                    isToday = day.date == today,
                    onClick = { onDateSelected(day.date) }
                )
            }
        )
    }
    // Week view
    AnimatedVisibility(
        visible = !isExpanded,
        enter = expandVertically(
            animationSpec = tween(durationMillis = 250),
            expandFrom = Alignment.Top
        ),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 250),
            shrinkTowards = Alignment.Top
        )
    ) {
        WeekCalendar(
            state = weekState,
            dayContent = { day ->
                val tasksForDay = tasksByDate[day.date] ?: emptyList()
                val isSelected = selectedDate == day.date
                WeekDayCell(
                    day = day,
                    isSelected = isSelected,
                    hasTasks = tasksForDay.isNotEmpty(),
                    isToday = day.date == today,
                    onClick = { onDateSelected(day.date) }
                )
            }
        )
    }
}