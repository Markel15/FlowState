package com.markel.flowstate.feature.flow.tasks.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.SubTask
import com.markel.flowstate.feature.tasks.R
import com.markel.flowstate.feature.flow.tasks.util.asColor
import kotlinx.coroutines.delay

@Composable
fun EditableSubTaskItem(
    subTask: SubTask,
    isExpanded: Boolean,
    itemShape: Shape = RoundedCornerShape(16.dp),
    onExpandChange: (Boolean) -> Unit,
    onUpdate: (SubTask) -> Unit,
    onCheckedChange: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editedTitle by remember(subTask, isExpanded) { mutableStateOf(subTask.title) }
    var editedDescription by remember(subTask, isExpanded) { mutableStateOf(subTask.description) }
    var editedPriority by remember(subTask, isExpanded) { mutableStateOf(subTask.priority) }
    var editedDueDate by remember(subTask, isExpanded) { mutableStateOf(subTask.dueDate) }

    val focusRequester = remember { FocusRequester() }
    val containerColor by animateColorAsState(
        targetValue = if (isExpanded)
            MaterialTheme.colorScheme.surfaceContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(),
        label = "subtask_container_color"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape),
        shape = itemShape,
        color = containerColor
    ) {
        Column {
            // Compact view (always visible)
            ListItem(
                modifier = Modifier
                    .clip(itemShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !isExpanded) {
                            onExpandChange(true)
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    val borderBaseColor = if (editedPriority == Priority.NOTHING)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else
                        editedPriority.asColor()
                    val checkBgColor by animateColorAsState(
                        targetValue = if (subTask.isDone) MaterialTheme.colorScheme.primary else Color.Transparent,
                        animationSpec = spring(),
                        label = "check_bg"
                    )
                    val checkBorderColor by animateColorAsState(
                        targetValue = if (subTask.isDone) MaterialTheme.colorScheme.primary else borderBaseColor,
                        animationSpec = spring(),
                        label = "check_border"
                    )
                    val checkScale by animateFloatAsState(
                        targetValue = if (subTask.isDone) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                        label = "check_scale"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(21.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(checkBgColor)
                            .border(1.5.dp, checkBorderColor, RoundedCornerShape(7.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCheckedChange
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
                },
                headlineContent = {
                    if (isExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (editedTitle.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.add_subtask_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            BasicTextField(
                                value = editedTitle,
                                onValueChange = { editedTitle = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Next
                                ),
                                maxLines = 3,
                                minLines = 1,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                            )
                        }
                    } else {
                        Text(
                            text = subTask.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            ),
                            textDecoration = if (subTask.isDone) TextDecoration.LineThrough else null,
                            color = if (subTask.isDone)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                trailingContent = {
                    if (isExpanded) {
                        IconButton(onClick = { onExpandChange(false) }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.close_24px),
                                contentDescription = "Cancel edit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },

                supportingContent = if (!isExpanded && (subTask.description.isNotBlank() || subTask.dueDate != null)) {
                    {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (subTask.description.isNotBlank()) {
                                Text(
                                    text = subTask.description,
                                    modifier = Modifier.padding(top = 5.dp),
                                    maxLines = 1,
                                    minLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (subTask.isDone) 0.6f else 0.9f
                                    )
                                )
                            }
                            if (subTask.dueDate != null) {
                                Text(
                                    text = formatDate(subTask.dueDate!!),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subTask.dueDate!!.let { date ->
                                        if (isDateOverdue(date)) {
                                            MaterialTheme.colorScheme.error.copy(
                                                alpha = if (subTask.isDone) 0.6f else 0.9f
                                            )
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(
                                                alpha = if (subTask.isDone) 0.6f else 0.9f
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else null
            )

            // Expanded view (editing)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            start = 56.dp,
                            end = 16.dp
                        )
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 4.dp, end = 0.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 1.dp
                    )

                    // Description
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (editedDescription.isEmpty()) {
                            Text(
                                text = stringResource(R.string.description_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }

                        BasicTextField(
                            value = editedDescription,
                            onValueChange = { editedDescription = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            minLines = 1,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Controls (priority, date, delete and send)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            IconButton(onClick = {
                                editedPriority = when (editedPriority) {
                                    Priority.NOTHING -> Priority.LOW
                                    Priority.LOW -> Priority.MEDIUM
                                    Priority.MEDIUM -> Priority.HIGH
                                    Priority.HIGH -> Priority.NOTHING
                                }
                            }) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.flag_2_24px),
                                    contentDescription = "Priority",
                                    tint = editedPriority.asColor(),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            DateSelector(
                                dueDate = editedDueDate,
                                onDueDateChange = { editedDueDate = it },
                                showLabel = true
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.delete_24px),
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            FilledIconButton(
                                onClick = {
                                    if (editedTitle.isNotBlank()) {
                                        onUpdate(
                                            subTask.copy(
                                                title = editedTitle,
                                                description = editedDescription,
                                                priority = editedPriority,
                                                dueDate = editedDueDate
                                            )
                                        )
                                        onExpandChange(false)
                                    }
                                },
                                enabled = editedTitle.isNotBlank(),
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.send),
                                    contentDescription = "Save",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (editedTitle.isNotBlank())
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(100)
            focusRequester.requestFocus()
        }
    }
}