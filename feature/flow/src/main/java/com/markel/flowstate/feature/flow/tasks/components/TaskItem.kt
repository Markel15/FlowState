package com.markel.flowstate.feature.flow.tasks.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.SubTask
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.feature.tasks.R
import com.markel.flowstate.feature.flow.tasks.util.asColor
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatableTaskItem(
    task: Task,
    shape: Shape,
    onDelete: () -> Unit,
    onComplete: () -> Unit,
    onContentClick: () -> Unit
) {
    var isCheckedLocally by remember { mutableStateOf(task.isDone) }

    LaunchedEffect(isCheckedLocally) {
        if (isCheckedLocally && !task.isDone) {
            delay(250)  // Animation purpose only, the completion is delayed only to animate the checkbox giving feedback that the task was completed
            onComplete()
        }
    }

    SwipeToDeleteContainer(
        item = task,
        onDelete = {
            onDelete()
        }
    ) {
        TaskItemContent(
            title = task.title,
            description = task.description,
            subTasks = task.subTasks,
            isDone = isCheckedLocally,
            priority = task.priority,
            dueDate = task.dueDate,
            reminderTime = task.reminderTime,
            shape = shape,
            onClicked = onContentClick,
            onCheckClicked = {
                isCheckedLocally = true
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SwipeToDeleteContainer(
    item: T,
    onDelete: () -> Unit,
    content: @Composable (T) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.35f }
    )

    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = { DeleteSwipeBackground(dismissState) },
        content = { content(item) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteSwipeBackground(
    state: SwipeToDismissBoxState
) {
    val isDeleteDirection = state.dismissDirection == SwipeToDismissBoxValue.EndToStart

    val color = if (isDeleteDirection) {
        MaterialTheme.colorScheme.errorContainer.copy(
            alpha = (state.progress * 1.5f).coerceIn(0f, 1f)
        )
    } else {
        Color.Transparent
    }

    val scaleAnimationSpec: AnimationSpec<Float> = if (state.progress >= 0.35 && isDeleteDirection) {
        spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessLow
        )
    } else {
        tween(durationMillis = 200, easing = LinearOutSlowInEasing)
    }

    val scale by animateFloatAsState(
        targetValue = if (state.progress >= 0.35 && isDeleteDirection) 1.20f else 0f,
        animationSpec = scaleAnimationSpec,
        label = "iconScale"
    )
    val cornerAnim by animateDpAsState(
        targetValue = if (isDeleteDirection)
            (16.dp * state.progress.coerceIn(0f, 1f))
        else 0.dp,
        animationSpec = tween(
            durationMillis = 180,
            easing = LinearOutSlowInEasing
        ),
        label = "corners"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topEnd = cornerAnim, bottomEnd = cornerAnim))
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (isDeleteDirection) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.delete_24px),
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            )
        }
    }
}

@Composable
fun TaskItemContent(
    title: String,
    description: String = "",
    subTasks: List<SubTask> = emptyList(),
    isDone: Boolean,
    priority: Priority = Priority.NOTHING,
    dueDate: Long? = null,
    reminderTime: Long? = null,
    shape: Shape,
    onClicked: () -> Unit,
    onCheckClicked: () -> Unit
) {
    val priorityColor = priority.asColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClicked() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val borderBaseColor = if (priority == Priority.NOTHING)
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            else
                priorityColor
            val checkBgColor by animateColorAsState(
                targetValue = if (isDone) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = spring(),
                label = "check_bg"
            )
            val checkBorderColor by animateColorAsState(
                targetValue = if (isDone) MaterialTheme.colorScheme.primary else borderBaseColor,
                animationSpec = spring(),
                label = "check_border"
            )
            val checkScale by animateFloatAsState(
                targetValue = if (isDone) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "check_scale"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(19.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(checkBgColor)
                    .border(1.5.dp, checkBorderColor, RoundedCornerShape(7.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCheckClicked
                    )
            ) {
                if (checkScale > 0f) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(16.dp)
                            .scale(checkScale)
                    )
                }

            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val taskTitleStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = title,
                    style = taskTitleStyle.copy(
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        color = if (isDone)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = if (isDone){
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        } else{
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val total = subTasks.size
                val completed = subTasks.count { it.isDone }
                val hasSubtasks = total > 0 && completed < total
                val hasDate = dueDate != null
                val hasReminder = reminderTime != null

                if (hasSubtasks || hasDate || hasReminder) {

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasSubtasks) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.subtask_24px),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$completed/$total",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                        if (hasSubtasks && hasDate) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        if (hasDate) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.event_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = dueDate.let { date ->
                                        if (isDateOverdue(date)) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatDate(dueDate),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = dueDate.let { date ->
                                        if (isDateOverdue(date)) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    }
                                )
                            }
                        }
                        if ((hasSubtasks || hasDate) && hasReminder) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        if (hasReminder) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.notifications_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatReminderDateTime(reminderTime),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}