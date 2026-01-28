package com.markel.flowstate.feature.tasks

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.feature.tasks.components.AnimatableTaskItem
import com.markel.flowstate.feature.tasks.components.DynamicHeader
import com.markel.flowstate.feature.tasks.components.EmptyStateView
import com.markel.flowstate.feature.tasks.components.ExpandableFabMenu
import com.markel.flowstate.feature.tasks.components.TaskCreationSheetContent
import com.markel.flowstate.feature.tasks.components.TaskEditorSheetContent
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // This state survives screen rotations but resets when the app is closed
    var hasScrolledOnce by rememberSaveable { mutableStateOf(false) }

    // Detect scroll only to activate the flag the first time
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 90 }
            .collect { scrolled ->
                if (scrolled && !hasScrolledOnce) {
                    hasScrolledOnce = true
                }
            }
    }

    var isFabExpanded by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var draftTitle by rememberSaveable { mutableStateOf("") }
    var draftDescription by rememberSaveable { mutableStateOf("") }
    var draftPriority by rememberSaveable { mutableStateOf(Priority.NOTHING)}
    var draftDueDate by rememberSaveable { mutableStateOf<Long?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !showSheet,
                    enter = scaleIn(),
                    exit = scaleOut()
                ) {
                    ExpandableFabMenu(
                        expanded = isFabExpanded,
                        onToggle = { isFabExpanded = !isFabExpanded },
                        onTaskClick = {
                            isFabExpanded = false; taskToEdit = null; showSheet = true
                        },
                        onIdeaClick = { isFabExpanded = false }
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {

                DynamicHeader(isMinimized = hasScrolledOnce)
                when (val state = uiState) {
                    is TasksUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            // Empty because it loads too fast to show a loading spinner or skeleton list
                        }
                    }
                    is TasksUiState.Success -> {
                        if (state.tasks.isEmpty()) {
                            EmptyStateView()
                        }
                        else {
                            val reorderableState =
                                rememberReorderableLazyListState(listState) { from, to ->
                                    viewModel.onReorder(from.index, to.index)
                                }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
                            ) {
                                items(state.tasks, key = { it.id }) { task ->
                                    ReorderableItem(reorderableState, key = task.id) { isDragging ->
                                        val scale by animateFloatAsState(
                                            targetValue = if (isDragging) 1.05f else 1.0f,
                                            label = "drag_scale"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .longPressDraggableHandle(
                                                    interactionSource = remember { MutableInteractionSource() }
                                                )
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                    alpha = if (isDragging) 0.9f else 1.0f
                                                }
                                                .zIndex(if (isDragging) 1f else 0f)
                                        ) {
                                            AnimatableTaskItem(
                                                task = task,
                                                onDelete = { viewModel.deleteTask(task) },
                                                onComplete = { viewModel.toggleTaskDone(task) },
                                                onContentClick = {
                                                    taskToEdit = task // EDIT Mode
                                                    showSheet = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                dragHandle = { if (taskToEdit == null) null else Spacer(modifier = Modifier.height(28.dp)) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = if (taskToEdit == null) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                if (taskToEdit == null) {
                    // --- CREATION MODE ---
                    TaskCreationSheetContent(
                        title = draftTitle,
                        onTitleChange = { draftTitle = it },
                        description = draftDescription,
                        onDescriptionChange = { draftDescription = it },
                        priority = draftPriority,
                        onPriorityChange = { draftPriority = it },
                        dueDate = draftDueDate,
                        onDueDateChange = { draftDueDate = it },
                        onSave = { title, desc, prio, date ->
                            viewModel.addTask(title, desc, prio, date, emptyList())
                            draftTitle = ""
                            draftDescription = ""
                            draftPriority = Priority.NOTHING
                            draftDueDate = null
                            showSheet = false
                        }
                    )
                }
                else {
                    // --- EDITION MODE ---
                    TaskEditorSheetContent(
                        task = taskToEdit,
                        onAutoUpdate = { title, desc, priority, dueDate, subTasks ->
                            viewModel.updateTask(taskToEdit!!, title, desc, priority, dueDate, subTasks)
                        }
                    )
                }
            }
        }
    }
}