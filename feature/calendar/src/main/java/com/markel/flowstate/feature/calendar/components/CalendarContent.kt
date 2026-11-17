package com.markel.flowstate.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.OutDateStyle
import com.kizitonwose.calendar.core.atStartOfMonth
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.feature.calendar.components.calendarview.CalendarMonthHeader
import com.markel.flowstate.feature.calendar.components.calendarview.CalendarWeekdaysHeader
import com.markel.flowstate.feature.calendar.components.calendarview.ExpandableCalendarView
import com.markel.flowstate.feature.calendar.components.taskslist.DailyTasksSection
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun CalendarContent(
    tasksByDate: Map<java.time.LocalDate, List<Task>>,
    selectedDate: java.time.LocalDate,
    onDateSelected: (java.time.LocalDate) -> Unit,
    onTaskToggle: (Task) -> Unit,
    // Hoisted from CalendarScreen so that the screen can derive
    // FAB visibility from the same LazyListState that drives this list.
    listState: LazyListState,
    today: java.time.LocalDate = java.time.LocalDate.now(),
) {
    val currentMonth = remember { YearMonth.from(today) }
    val startMonth = remember { currentMonth.minusMonths(100) }
    val endMonth = remember { currentMonth.plusMonths(100) }
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }

    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var dragOffset by remember { mutableFloatStateOf(0f) }  // State for the accumulated drag
    val threshold = 100f  // Threshold to change states (pixels)
    var scrollTrigger by remember { mutableStateOf(0L) }

    val monthState = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeek,
        outDateStyle = OutDateStyle.EndOfGrid
    )

    val weekState = rememberWeekCalendarState(
        startDate = startMonth.atStartOfMonth(),
        endDate = endMonth.atEndOfMonth(),
        firstVisibleWeekDate = selectedDate,
        firstDayOfWeek = firstDayOfWeek
    )

    // Automatically navigate to the month of the selected date
    LaunchedEffect(selectedDate, isExpanded, scrollTrigger) {
        val selectedYearMonth = YearMonth.from(selectedDate)
        if (isExpanded && monthState.firstVisibleMonth.yearMonth != selectedYearMonth) {
            monthState.animateScrollToMonth(selectedYearMonth)
        }
        if (!isExpanded) {
            weekState.animateScrollToWeek(selectedDate)
        }
    }

    // NestedScrollConnection to handle the list scroll
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {

                val delta = available.y

                // If it is expanded and scroll upwards (negative delta)
                if (isExpanded && delta < 0) {
                    dragOffset += delta
                    if (dragOffset < -threshold) {
                        isExpanded = false
                        dragOffset = 0f
                    }
                    // Consume part of the scroll while close to the threshold
                    return if (abs(dragOffset) < threshold) androidx.compose.ui.geometry.Offset(0f, delta * 0.5f) else androidx.compose.ui.geometry.Offset.Zero
                }

                // If it is collapsed, the list is at the top, and we scroll downwards (positive delta)
                if (!isExpanded && delta > 0 && !listState.canScrollBackward) {
                    dragOffset += delta
                    if (dragOffset > threshold) {
                        isExpanded = true
                        dragOffset = 0f
                    }
                    // Consume downward scroll when the list is at the top
                    return androidx.compose.ui.geometry.Offset(0f, delta)
                }

                // Reset the offset if we change direction
                if ((delta > 0 && dragOffset < 0) || (delta < 0 && dragOffset > 0)) {
                    dragOffset = 0f
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Reset offset after fling
                dragOffset = 0f
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {  // So that drag is counted outside the task list
                detectVerticalDragGestures(
                    onDragEnd = {
                        // Evaluate whether we should change state on release
                        if (isExpanded && dragOffset < -threshold) {
                            isExpanded = false
                        } else if (!isExpanded && dragOffset > threshold) {
                            isExpanded = true
                        }
                        dragOffset = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        // Accumulate drag anywhere on the screen
                        if (isExpanded && dragAmount < 0) {
                            // Expanded and dragging upwards
                            dragOffset += dragAmount
                        } else if (!isExpanded && dragAmount > 0) {
                            // Collapsed and dragging downwards
                            dragOffset += dragAmount
                        } else {
                            // Change of direction, reset
                            dragOffset = 0f
                        }
                    }
                )
            }
    ){
        // MONTH HEADER
        CalendarMonthHeader(
            isExpanded = isExpanded,
            monthState = monthState,
            weekState = weekState,
            selectedDate = selectedDate,
            today = today,
            onTodayClick = {
                onDateSelected(today)
                scrollTrigger = System.currentTimeMillis()
            },
            onDateSelected = onDateSelected
        )

        // WEEKDAYS
        CalendarWeekdaysHeader()

        // CALENDAR

        ExpandableCalendarView(
            isExpanded = isExpanded,
            monthState = monthState,
            weekState = weekState,
            tasksByDate = tasksByDate,
            selectedDate = selectedDate,
            today = today,
            onDateSelected = { date ->
                // If we select a date that is already selected, force the scroll only when the date is not visible
                if (date == selectedDate && isExpanded) {
                    // This is for the edge case when a day of other month is selected but visible from a different month to still make the scroll available
                    val selectedYearMonth = YearMonth.from(selectedDate)
                    if (monthState.firstVisibleMonth.yearMonth != selectedYearMonth) {
                        scrollTrigger = System.currentTimeMillis()
                    }
                }
                onDateSelected(date)
            }
        )
        if (!isExpanded) Spacer(modifier = Modifier.height(4.dp))

        // TASKS LIST
        DailyTasksSection(
            startDate = selectedDate,
            tasksByDate = tasksByDate,
            listState = listState,
            today = today,
            onTaskToggle = onTaskToggle
        )
    }
}
