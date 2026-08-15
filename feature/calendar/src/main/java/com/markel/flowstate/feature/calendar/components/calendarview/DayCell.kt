package com.markel.flowstate.feature.calendar.components.calendarview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DayCell(
    day: CalendarDay,
    isSelected: Boolean,
    hasTasks: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val isCurrentMonth = day.position == DayPosition.MonthDate

    // Only show selection circle if the day is in the current month AND selected
    val showSelection = isSelected && isCurrentMonth

    // The "today" emphasis only applies inside the current month.
    val isTodayInMonth = isToday && isCurrentMonth

    val textColor = when {
        showSelection -> MaterialTheme.colorScheme.onPrimary
        isTodayInMonth -> MaterialTheme.colorScheme.onSurfaceVariant
        isCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }
    val shape = MaterialShapes.Pill.toShape()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(39.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(
                color = when {
                    showSelection -> MaterialTheme.colorScheme.primary
                    isTodayInMonth -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                },
                shape = shape
            )
            .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {

            Text(
                modifier = Modifier.align(Alignment.Center),
                text = day.date.dayOfMonth.toString(),
                color = textColor,
                fontSize = 16.sp,
                fontWeight = if (showSelection || isTodayInMonth) FontWeight.Bold else FontWeight.Normal
            )

            // Dot if there are tasks
            if (hasTasks && isCurrentMonth) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(if (showSelection) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}