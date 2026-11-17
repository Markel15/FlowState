package com.markel.flowstate.feature.calendar

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.designsystem.R
import com.markel.flowstate.core.designsystem.components.AnimatedUndoFab
import com.markel.flowstate.core.designsystem.ui.rememberFabVisibilityState
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.feature.calendar.components.CalendarContent
import com.markel.flowstate.feature.flow.tasks.TaskViewModel
import com.markel.flowstate.feature.flow.tasks.components.TaskCreationSheetContent
import com.markel.flowstate.feature.flow.tasks.util.HandleSystemBars
import kotlinx.coroutines.delay
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    taskViewModel: TaskViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.addObserver(viewModel)
        onDispose { lifecycleOwner.lifecycle.removeObserver(viewModel) }
    }

    val uiState by viewModel.uiState.collectAsState()
    val draft by taskViewModel.draft.collectAsStateWithLifecycle()

    var showUndoButton by remember { mutableStateOf(false) }
    var pendingUndoTask by remember { mutableStateOf<Task?>(null) }
    var showCreationSheet by remember { mutableStateOf(false) }

    // ── Scroll-aware FAB visibility ────────────────────────
    // The Calendar list has no empty case.
    val calendarListState = rememberLazyListState()
    val fabVisible by rememberFabVisibilityState(
        lazyListState = calendarListState,
    )

    LaunchedEffect(showUndoButton) {
        if (showUndoButton) {
            delay(3500L)
            showUndoButton = false
            pendingUndoTask = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        modifier = Modifier
            .fillMaxSize(),
        floatingActionButton = {
            MediumFloatingActionButton(
                onClick = {
                    // Use the selected date in the calendar by default when creating a task
                    (uiState as? CalendarUiState.Success)?.selectedDate?.let { date ->
                        val millis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
                        taskViewModel.updateDraftDueDate(millis)
                    }
                    showCreationSheet = true
                },
                modifier = Modifier.animateFloatingActionButton(
                    visible = fabVisible,
                    alignment = Alignment.BottomEnd,
                )
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.add_24px),
                    contentDescription = "Add task",
                    modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize),
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when (val state = uiState) {
                    is CalendarUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is CalendarUiState.Success -> {
                        CalendarContent(
                            tasksByDate = state.tasksByDate,
                            selectedDate = state.selectedDate,
                            today = state.today,
                            onDateSelected = viewModel::onDateSelected,
                            onTaskToggle = { task ->
                                val isTaskDisappearing = task.dueDate == null && task.isDone

                                viewModel.toggleTaskDone(task)

                                if (isTaskDisappearing) {
                                    pendingUndoTask = task
                                    showUndoButton = true

                                }
                            },
                            listState = calendarListState
                        )
                    }
                }
            }

            if (showCreationSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showCreationSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    dragHandle = null,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    HandleSystemBars(isLandscape)

                    TaskCreationSheetContent(
                        title = draft.title,
                        onTitleChange = { taskViewModel.updateDraftTitle(it) },
                        description = draft.description,
                        onDescriptionChange = { taskViewModel.updateDraftDescription(it) },
                        priority = draft.priority,
                        onPriorityChange = { taskViewModel.updateDraftPriority(it) },
                        dueDate = draft.dueDate,
                        onDueDateChange = { taskViewModel.updateDraftDueDate(it) },
                        reminderTime = draft.reminderTime,
                        onReminderTimeChange = { taskViewModel.updateDraftReminderTime(it) },
                        onSave = { _, _, _, _, _ ->
                            taskViewModel.submitDraft()
                            showCreationSheet = false
                        }
                    )
                }
            }

            AnimatedUndoFab(
                visible = showUndoButton,
                onUndoClick = {
                    pendingUndoTask?.let { task ->
                        viewModel.undoTaskToggle(task)
                        showUndoButton = false
                        pendingUndoTask = null
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
            )

        }
    }
}