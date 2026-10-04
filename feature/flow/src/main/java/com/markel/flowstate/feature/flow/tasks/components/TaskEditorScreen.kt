package com.markel.flowstate.feature.flow.tasks.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.designsystem.ui.TaskSharedKeys
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.designsystem.ui.sharedDetailBounds
import com.markel.flowstate.feature.flow.tasks.TaskEditorViewModel
import com.markel.flowstate.feature.flow.tasks.components.TaskEditorSheetContent
import com.markel.flowstate.feature.flow.tasks.components.TaskEditorTopBar

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

    Scaffold(
        modifier = Modifier.sharedDetailBounds(
            key = TaskSharedKeys.container(taskId)
        ),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TaskEditorTopBar(
                priority = editor.task?.priority ?: Priority.NOTHING,
                onPriorityChange = { viewModel.updatePriority(it) },
                isDone = editor.task?.isDone == true,
                onComplete = { viewModel.toggleDone() },
                onDelete = {
                    viewModel.deleteTask(editor.task!!)
                    onBack()
                },
                onBack = onBack
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
            editor.task?.let { task ->
                TaskEditorSheetContent(
                    task = task,
                    priority = task.priority,
                    dueDate = task.dueDate,
                    remTime = task.reminderTime,
                    onDueDateChange = { viewModel.updateDueDate(it) },
                    onReminderTimeChange = { viewModel.updateReminderTime(it) },
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
                    categoryId = task.categoryId,
                    onCategoryChange = { viewModel.updateCategory(it) },
                    generalCategoryName = generalCategoryName
                )
            }
        }
    }
}
