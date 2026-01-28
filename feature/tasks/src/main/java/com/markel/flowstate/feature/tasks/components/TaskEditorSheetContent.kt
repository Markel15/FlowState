package com.markel.flowstate.feature.tasks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
fun TaskEditorSheetContent(
    task: Task?,
    onAutoUpdate: (String, String, Priority, Long?, List<SubTask>) -> Unit
) {
    val isNewTask = remember { task == null }
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var priority by remember { mutableStateOf(task?.priority ?: Priority.NOTHING) }
    var dueDate by remember { mutableStateOf(task?.dueDate) }
    val subTasks = remember {
        mutableStateListOf<SubTask>().apply {
            addAll(task?.subTasks ?: emptyList())
        }
    }

    // Checkpoints to track what was actually last saved
    var lastSavedTitle by remember { mutableStateOf(title) }
    var lastSavedDesc by remember { mutableStateOf(description) }
    var lastSavedPriority by remember { mutableStateOf(priority) }
    var lastSavedDueDate by remember { mutableStateOf(dueDate) }
    var lastSavedSubTasksHash by remember { mutableIntStateOf(subTasks.toList().hashCode()) }

    val focusRequester = remember { FocusRequester() }

    // TIME-BASED AUTOSAVE (DEBOUNCE)
    if (!isNewTask) {
        LaunchedEffect(title, description, priority, dueDate, subTasks.toList().hashCode()) {
            val currentSubTasksHash = subTasks.toList().hashCode()

            val hasChanges = title != lastSavedTitle ||
                    description != lastSavedDesc ||
                    priority != lastSavedPriority ||
                    dueDate != lastSavedDueDate ||
                    currentSubTasksHash != lastSavedSubTasksHash

            if (hasChanges && title.isNotBlank()) {
                delay(600)

                // Save
                onAutoUpdate(title, description, priority, dueDate, subTasks.toList())

                // Update references
                lastSavedTitle = title
                lastSavedDesc = description
                lastSavedPriority = priority
                lastSavedDueDate = dueDate
                lastSavedSubTasksHash = currentSubTasksHash
            }
        }
    }

    // EMERGENCY SAVE ON CLOSE (DISPOSE)
    // This runs if the user closes the sheet before the delay finishes
    DisposableEffect(Unit) {
        onDispose {
            // Only autosave on exit if it's an ALREADY EXISTING task (Editing)
            if (!isNewTask) {
                val currentSubTasksHash = subTasks.toList().hashCode()
                val hasPendingChanges = title != lastSavedTitle ||
                        description != lastSavedDesc ||
                        priority != lastSavedPriority ||
                        dueDate != lastSavedDueDate ||
                        currentSubTasksHash != lastSavedSubTasksHash

                if (hasPendingChanges && title.isNotBlank()) {
                    onAutoUpdate(title, description, priority,  dueDate, subTasks.toList())
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
    ) {
        val scrollState = rememberScrollState()
        Column (
            modifier = Modifier
                .weight(1f, fill = false)  // fill = false allows it to shrink if content is small
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {

            // TASK
            TextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        stringResource(R.string.edit_task_placeholder),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                )
            )

            TextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource(R.string.edit_task_desc_placeholder),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // SUBTASKS
            Text(
                stringResource(R.string.subtasks),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            subTasks.forEachIndexed { index, subTask ->
                SubTaskRow(
                    subTask = subTask,
                    onRemove = { subTasks.removeAt(index) },
                    onTitleChange = { newTitle ->
                        subTasks[index] = subTask.copy(title = newTitle)
                    },
                    onToggleDone = {
                        subTasks[index] = subTask.copy(isDone = !subTask.isDone)
                    }
                )
            }

            AddSubTaskRow(onAdd = { newTitle -> subTasks.add(SubTask(title = newTitle)) })

            Spacer(modifier = Modifier.height(8.dp))
        }
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = {
                    val nextPriority = when (priority) {
                        Priority.NOTHING -> Priority.LOW
                        Priority.LOW -> Priority.MEDIUM
                        Priority.MEDIUM -> Priority.HIGH
                        Priority.HIGH -> Priority.NOTHING
                    }
                    priority = nextPriority
                }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.flag_2_24px),
                        contentDescription = "Priority",
                        tint = priority.asColor(),
                        modifier = Modifier.size(24.dp)
                    )
                }
                DateSelector(
                    dueDate = dueDate,
                    onDueDateChange = { dueDate = it },
                    modifier = Modifier,
                    showLabel = true
                )
                IconButton(onClick = { /* TODO: Implement Formatting */ }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.format_color_text_24px),
                        "Format",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.95f)
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(100)
        if (isNewTask) focusRequester.requestFocus()
    }
}