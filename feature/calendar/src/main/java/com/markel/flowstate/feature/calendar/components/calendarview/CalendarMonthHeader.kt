package com.markel.flowstate.feature.calendar.components.calendarview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.CalendarState
import com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState
import com.markel.flowstate.feature.calendar.R
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

@Composable
fun CalendarMonthHeader(
    isExpanded: Boolean,
    monthState: CalendarState,
    weekState: WeekCalendarState,
    selectedDate: java.time.LocalDate,
    onTodayClick: () -> Unit,
    onDateSelected: (java.time.LocalDate) -> Unit,
    today: java.time.LocalDate = java.time.LocalDate.now()
    ) {

    var isPickerExpanded by remember { mutableStateOf(false) }
    var scrollTriggerMonth by remember { mutableStateOf<YearMonth?>(null) }
    var scrollTriggerStamp by remember { mutableStateOf(0L) }

    val displayMonth = if (isExpanded) {
        monthState.firstVisibleMonth.yearMonth
    } else {
        // In the week view: if the selected day is visible, show that month
        // Else, show the one of the visible week (when scrolling)
        val selectedYearMonth = YearMonth.from(selectedDate)
        val visibleWeekYearMonth = YearMonth.from(weekState.firstVisibleWeek.days.first().date)
        if (weekState.firstVisibleWeek.days.any { it.date == selectedDate }) selectedYearMonth else visibleWeekYearMonth  // Check if is selected in the visible week
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isPickerExpanded = !isPickerExpanded }
                    .padding(4.dp)
            ) {
                val monthTitle = displayMonth.month
                    .getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale)
                    .replaceFirstChar { it.uppercase() }

                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            style = MaterialTheme.typography.headlineMedium.toSpanStyle().copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.35).sp,
                                fontStyle = FontStyle.Italic,
                                fontSize = 34.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            append(monthTitle)
                        }
                        append("  ")
                        withStyle(
                            style = MaterialTheme.typography.displaySmall.toSpanStyle().copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        ) { append(displayMonth.year.toString()) }
                    },
                    lineHeight = 36.sp
                )
                Icon(
                    imageVector = ImageVector.vectorResource(if (isPickerExpanded) R.drawable.arrow_drop_up_24px else R.drawable.arrow_drop_down_24px),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Bottom)
                )
            }

            // Today button to center the selected day in the calendar to today
            IconButton(
                onClick = {
                    scrollTriggerMonth = YearMonth.from(today)
                    scrollTriggerStamp = System.currentTimeMillis()
                    onTodayClick()
                }
            ) {
                TodayIcon(
                    outlineColor = MaterialTheme.colorScheme.onSurface,
                    dotColor = MaterialTheme.colorScheme.primary
                )
            }
        }
        AnimatedVisibility(visible = isPickerExpanded) {
            MonthPicker(
                currentMonth = displayMonth,
                scrollTrigger = scrollTriggerMonth to scrollTriggerStamp,
                onMonthClick = { targetMonth ->
                    val firstDay = targetMonth.atDay(1)

                    // Select the first day of the month. This triggers the LaunchedEffect in the CalendarContent that animates the scrolling.
                    onDateSelected(firstDay)
                }
            )
        }
    }
}