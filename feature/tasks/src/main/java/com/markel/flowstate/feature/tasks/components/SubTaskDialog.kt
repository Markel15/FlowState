package com.markel.flowstate.feature.tasks.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.SubTask
import com.markel.flowstate.feature.tasks.R
import com.markel.flowstate.feature.tasks.util.asColor

// TEMPORAL FILE, NEXT UPDATE SHOULD DELETE THIS AND PROVIDE A MORE DIRECT WAY TO CREATE/UPDATE A TASK

@Composable
fun SubTaskDialog(
    subTask: SubTask?,
    onDismiss: () -> Unit,
    onSave: (SubTask) -> Unit
) {
    var title by remember { mutableStateOf(subTask?.title ?: "") }
    var description by remember { mutableStateOf(subTask?.description ?: "") }
    var priority by remember { mutableStateOf(subTask?.priority ?: Priority.NOTHING) }
    var dueDate by remember { mutableStateOf(subTask?.dueDate) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (subTask == null) stringResource(R.string.add_subtask_placeholder) else stringResource(R.string.subtask_placeholder),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.add_subtask_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata Row (Priority & Date)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Priority Toggle
                    IconButton(onClick = {
                        priority = when (priority) {
                            Priority.NOTHING -> Priority.LOW
                            Priority.LOW -> Priority.MEDIUM
                            Priority.MEDIUM -> Priority.HIGH
                            Priority.HIGH -> Priority.NOTHING
                        }
                    }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.flag_2_24px),
                            contentDescription = "Priorty",
                            tint = priority.asColor()
                        )
                    }

                    DateSelector(
                        dueDate = dueDate,
                        onDueDateChange = { dueDate = it },
                        showLabel = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(
                                    SubTask(
                                        id = subTask?.id ?: java.util.UUID.randomUUID().toString(),
                                        title = title,
                                        description = description,
                                        isDone = subTask?.isDone ?: false,
                                        priority = priority,
                                        dueDate = dueDate,
                                        position = subTask?.position ?: 0
                                    )
                                )
                            }
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}