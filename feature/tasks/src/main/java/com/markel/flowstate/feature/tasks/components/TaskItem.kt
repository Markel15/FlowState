package com.markel.flowstate.feature.tasks.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import com.markel.flowstate.feature.tasks.util.asColor
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatableTaskItem(
    task: Task,
    onDelete: () -> Unit,
    onComplete: () -> Unit,
    onContentClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(true) }
    var isChecked by remember { mutableStateOf(task.isDone) }
    var isDeleted by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            delay(300)
            if (isDeleted) {
                onDelete()
            } else {
                onComplete()
            }
        }
    }

    val exitTransition = if (isDeleted) {
        // DELETE CASE: Slide to the left
        slideOutHorizontally(
            targetOffsetX = { -it / 2 },
            animationSpec = tween(300, easing = LinearOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(200)) +
                shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    shrinkTowards = Alignment.Top
                )
    } else {
        // COMPLETE CASE: Fade out + Smooth shrink
        fadeOut(
            animationSpec = tween(350)
        ) + shrinkVertically(
            animationSpec = tween(400)
        )
    }

    AnimatedVisibility(
        visible = isVisible,
        exit = exitTransition
    ) {
        SwipeToDeleteContainer(
            item = task,
            onDelete = {
                isDeleted = true
                isVisible = false
            })
        {
            TaskItemContent(
                title = task.title,
                description = task.description,
                subTasks = task.subTasks,
                isDone = isChecked,
                priority = task.priority,
                dueDate = task.dueDate,
                onClicked = onContentClick,
                onCheckClicked = {
                    isChecked = true
                    isDeleted = false
                    isVisible = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SwipeToDeleteContainer(
    item: T,
    onDelete: () -> Unit,
    content: @Composable (T) -> Unit
) {
    val threshold = 0.35f
    lateinit var dismissState: SwipeToDismissBoxState
    dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && dismissState.progress >= threshold) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
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
                imageVector = Icons.Default.Delete,
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
    onClicked: () -> Unit,
    onCheckClicked: () -> Unit
) {
    val priorityColor = priority.asColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClicked() }
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = if (isDone) ImageVector.vectorResource(R.drawable.radio_button_checked_24px) else ImageVector.vectorResource(
                        R.drawable.radio_button_unchecked_24px
                    ),
                    contentDescription = null,
                    tint = if (isDone) MaterialTheme.colorScheme.tertiary else if (priority == Priority.NOTHING) MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.4f
                    ) else priorityColor,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .toggleable(
                            value = isDone,
                            onValueChange = { onCheckClicked() },
                            role = Role.Checkbox
                        )
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                val taskTitleStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    lineHeight = 20.sp
                )
                Text(
                    text = title,
                    style = taskTitleStyle.copy(
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        color = if (isDone)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (description.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
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
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val total = subTasks.size
                val completed = subTasks.count { it.isDone }
                val hasSubtasks = total > 0 && completed < total
                val hasDate = dueDate != null

                if (hasSubtasks || hasDate) {

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
                                            MaterialTheme.colorScheme.tertiary
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
                                            MaterialTheme.colorScheme.tertiary
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.4.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}