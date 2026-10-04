package com.markel.flowstate.feature.flow.tasks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.designsystem.ui.TaskSharedKeys
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.designsystem.ui.sharedDetailBounds
import com.markel.flowstate.feature.flow.tasks.TaskEditorState
import com.markel.flowstate.feature.flow.tasks.TaskEditorViewModel
import com.markel.flowstate.feature.flow.tasks.taskOrNull
import com.markel.flowstate.feature.tasks.R

/**
 * Full screen for task editing.
 *
 * As a standalone navigation destination:
 * - The bottom bar automatically disappears (controlled by route in MainActivity)
 * - The Navigation back stack manages "back"
 *
 * Receives [taskId] to load the task from the ViewModel.
 */
@Composable
fun TaskEditorScreen(
    taskId: Int,
    onBack: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel()
) {
    // We load the task when the screen enters composition
    LaunchedEffect(taskId) {
        viewModel.loadTask(taskId)
    }

    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoriesEnabled by viewModel.categoriesEnabled.collectAsStateWithLifecycle()
    val generalCategoryName by viewModel.generalCategoryName.collectAsStateWithLifecycle()
    val task = editor.taskOrNull

    Scaffold(
        modifier = Modifier.sharedDetailBounds(
            key = TaskSharedKeys.container(taskId)
        ),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TaskEditorTopBar(
                priority = task?.priority ?: Priority.NOTHING,
                onPriorityChange = viewModel::updatePriority,
                isDone = task?.isDone == true,
                onComplete = viewModel::toggleDone,
                onDelete = {
                    task?.let {
                        viewModel.deleteTask(it)
                        onBack()
                    }
                },
                onBack = onBack,
                actionsEnabled = task != null
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
        ) {
            when (val state = editor) {
                TaskEditorState.Loading -> EditorLoading()
                TaskEditorState.NotFound -> EditorLoadFailure(
                    message = stringResource(R.string.task_editor_not_found),
                    onRetry = { viewModel.loadTask(taskId) }
                )
                is TaskEditorState.Error -> EditorLoadFailure(
                    message = stringResource(R.string.task_editor_load_error),
                    onRetry = { viewModel.loadTask(taskId) }
                )
                is TaskEditorState.Ready -> TaskEditorSheetContent(
                    task = state.task,
                    priority = state.task.priority,
                    dueDate = state.task.dueDate,
                    remTime = state.task.reminderTime,
                    onDueDateChange = viewModel::updateDueDate,
                    onReminderTimeChange = viewModel::updateReminderTime,
                    onAutoUpdate = { title, desc, prio, date, remTime, subTasks ->
                        viewModel.updateTask(
                            newTitle = title,
                            newDescription = desc,
                            newPriority = prio,
                            newDueDate = date,
                            newReminderTime = remTime,
                            newSubTasks = subTasks,
                        )
                    },
                    categories = categories,
                    categoriesEnabled = categoriesEnabled,
                    categoryId = state.task.categoryId,
                    onCategoryChange = viewModel::updateCategory,
                    generalCategoryName = generalCategoryName
                )
            }
        }
    }
}

@Composable
private fun EditorLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EditorLoadFailure(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}
