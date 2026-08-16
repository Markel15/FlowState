package com.markel.flowstate.feature.flow.tasks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.feature.tasks.R
import com.markel.flowstate.feature.flow.tasks.util.asColor
import com.markel.flowstate.feature.flow.tasks.util.flagIconRes

/**
 * Editor top bar, deliberately quiet: navigate back, the priority flag
 * (tap to cycle, tinted with the priority color) and an overflow menu for
 * task lifecycle actions (complete / delete). Due date and reminder live in
 * the metadata chips row under the description (see TaskMetadataChips).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorTopBar(
    priority: Priority,
    onPriorityChange: (Priority) -> Unit,
    isDone: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.arrow_back_24px),
                    contentDescription = "Close"
                )
            }
        },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val nextPriority = when (priority) {
                        Priority.NOTHING -> Priority.LOW
                        Priority.LOW -> Priority.MEDIUM
                        Priority.MEDIUM -> Priority.HIGH
                        Priority.HIGH -> Priority.NOTHING
                    }
                    onPriorityChange(nextPriority)
                }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(priority.flagIconRes()),
                        contentDescription = "Priority",
                        tint = priority.asColor(),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.more_vert_24px),
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isDone) stringResource(R.string.mark_pending)
                                    else stringResource(R.string.mark_completed)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onComplete()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_task)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.delete_24px),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}