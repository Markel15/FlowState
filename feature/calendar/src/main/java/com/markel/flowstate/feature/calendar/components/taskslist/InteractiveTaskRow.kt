package com.markel.flowstate.feature.calendar.components.taskslist

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.feature.calendar.R
import com.markel.flowstate.feature.flow.tasks.util.asColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CARD_SHAPE = RoundedCornerShape(14.dp)
private val ACCENT_WIDTH = 3.dp


@Composable
fun InteractiveTaskRow(
    task: Task,
    onToggle: () -> Unit,
    today: LocalDate = LocalDate.now()
) {
    val priorityColor: Color? = if (task.priority == Priority.NOTHING) null
    else task.priority.asColor()

    val cardModifier = if (priorityColor != null)
    {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(CARD_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .drawBehind {
                drawRect(
                    color = priorityColor,
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(ACCENT_WIDTH.toPx(), size.height)
                )
            }
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(CARD_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    }
    Box(modifier = cardModifier)
     {
         Row(
             modifier = Modifier.padding(
                 start = if (priorityColor != null) ACCENT_WIDTH + 14.dp else 16.dp,
                 end = 16.dp,
                 top = 14.dp,
                 bottom = 14.dp
             ),
             verticalAlignment = Alignment.CenterVertically
         )   {
             // Interactive checkbox
             val checkBgColor by animateColorAsState(
                 targetValue = if (task.isDone) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                 animationSpec = spring(),
                 label = "check_bg"
             )
             val checkBorderColor by animateColorAsState(
                 targetValue = if (task.isDone)
                     MaterialTheme.colorScheme.primary
                 else
                     MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                 animationSpec = spring(),
                 label = "check_border"
             )
             val checkScale by animateFloatAsState(
                 targetValue = if (task.isDone) 1f else 0f,
                 animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                 label = "check_scale"
             )
             Box(
                 contentAlignment = Alignment.Center,
                 modifier = Modifier
                     .size(26.dp)
                     .clip(RoundedCornerShape(7.dp))
                     .background(checkBgColor)
                     .border(1.5.dp, checkBorderColor, RoundedCornerShape(7.dp))
                     .clickable { onToggle() }
             ) {
                 if (checkScale > 0f) {
                     Icon(
                         imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                         contentDescription = if (task.isDone) "Mark as incomplete" else "Mark as complete",
                         tint = androidx.compose.ui.graphics.Color.White,
                         modifier = Modifier
                             .size(16.dp)
                             .scale(checkScale)
                     )
                 }
             }

             Spacer(modifier = Modifier.width(12.dp))

             Column(modifier = Modifier.weight(1f)) {
                 Text(
                     text = task.title,
                     style = MaterialTheme.typography.bodyMedium.copy(
                         textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                         color = if (task.isDone)
                             MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                         else
                             MaterialTheme.colorScheme.onSurface
                     ),
                     maxLines = 2,
                     overflow = TextOverflow.Ellipsis
                 )

                 // Show description preview if exists
                 if (task.description.isNotBlank()) {
                     Spacer(modifier = Modifier.height(4.dp))
                     Text(
                         text = task.description,
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                             alpha = if (task.isDone) 0.4f else 0.7f
                         ),
                         maxLines = 1,
                         overflow = TextOverflow.Ellipsis
                     )
                 }

                 // Show indicators for subtasks and reminders
                 val hasSubtasks = task.subTasks.isNotEmpty()
                 val hasReminder = task.reminderTime != null

                 if (hasSubtasks || hasReminder) {
                     Spacer(modifier = Modifier.height(4.dp))
                     val completed = task.subTasks.count { it.isDone }
                     val total = task.subTasks.size
                     Row(verticalAlignment = Alignment.CenterVertically) {
                         if(hasSubtasks) {
                             Icon(
                                 imageVector = ImageVector.vectorResource(id = R.drawable.subtask_24px),
                                 contentDescription = null,
                                 modifier = Modifier.size(12.dp),
                                 tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                             )
                             Spacer(modifier = Modifier.width(4.dp))
                             Text(
                                 text = "$completed/$total",
                                 style = MaterialTheme.typography.labelSmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                             )
                         }
                         if (hasSubtasks && hasReminder) {
                             Spacer(modifier = Modifier.width(10.dp))
                         }
                         if (hasReminder) {
                             Icon(
                                 imageVector = ImageVector.vectorResource(R.drawable.notifications_24px),
                                 contentDescription = null,
                                 modifier = Modifier.size(12.dp),
                                 tint = MaterialTheme.colorScheme.tertiary
                             )
                             Spacer(modifier = Modifier.width(4.dp))
                             Text(
                                 text = formatCalendarReminderTime(task.reminderTime, today),
                                 style = MaterialTheme.typography.labelSmall,
                                 color = MaterialTheme.colorScheme.tertiary
                             )
                         }
                     }
                 }
             }
         }
    }
}

@Composable
private fun formatCalendarReminderTime(timestamp: Long?, today: LocalDate = LocalDate.now()): String {
    if (timestamp == null) return ""
    val context = LocalContext.current
    val zdt = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    val date = zdt.toLocalDate()
    val time = zdt.toLocalTime()

    val is24Hour = DateFormat.is24HourFormat(context)
    val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
    val timeStr = DateTimeFormatter.ofPattern(timePattern).format(time)
    val dateStr = when (date) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> DateTimeFormatter.ofPattern("d MMM").format(date)
    }
    return "$dateStr $timeStr"
}