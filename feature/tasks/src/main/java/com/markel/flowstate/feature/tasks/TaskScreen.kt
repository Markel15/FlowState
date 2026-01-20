package com.markel.flowstate.feature.tasks

import android.R.attr.translationZ
import android.graphics.BlurMaskFilter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.sharp.Create
import androidx.compose.material.icons.sharp.DateRange
import androidx.compose.material.icons.sharp.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.SubTask
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.designsystem.theme.priority
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Este estado sobrevive a rotaciones de pantalla, pero se reinicia al cerrar la app
    var hasScrolledOnce by rememberSaveable { mutableStateOf(false) }

    // Detectamos el scroll solo para activar el flag la primera vez
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
                            // Vacío porque tarda demasiado poco como para poner círculo de carga o una lista "fantasma"
                        }
                    }
                    is TasksUiState.Success -> {
                        val listState = rememberLazyListState()
                        val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                            viewModel.onReorder(from.index, to.index)
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
                        ) {
                            if (state.tasks.isEmpty()) {
                                item { EmptyStateView() }
                            }
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
                                                taskToEdit = task // Modo EDITAR
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

        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                dragHandle = if (taskToEdit == null) null else { { BottomSheetDefaults.DragHandle() } },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = if (taskToEdit == null) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                if (taskToEdit == null) {
                    // --- MODO CREACIÓN ---
                    TaskCreationSheetContent(
                        title = draftTitle,
                        onTitleChange = { draftTitle = it },
                        description = draftDescription,
                        onDescriptionChange = { draftDescription = it },
                        priority = draftPriority,
                        onPriorityChange = { draftPriority = it },
                        onSave = { title, desc, prio ->
                            viewModel.addTask(title, desc, prio, emptyList())
                            draftTitle = ""
                            draftDescription = ""
                            draftPriority = Priority.NOTHING
                            showSheet = false
                        }
                    )
                }
                else {
                    // --- MODO EDICIÓN ---
                    TaskEditorSheetContent(
                        task = taskToEdit,
                        onAutoUpdate = { title, desc, priority, subTasks ->
                            viewModel.updateTask(taskToEdit!!, title, desc, priority, subTasks)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicHeader(isMinimized: Boolean) {
    val greeting = when (LocalTime.now().hour) {
        in 5..12 -> "Buenos días"
        in 13..20 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    val dateText = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale("es", "ES"))
        .format(java.time.LocalDate.now())
        .uppercase()

    val headerHeight by animateDpAsState(
        targetValue = if (isMinimized) 40.dp else 80.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "height"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            // El mensaje solo se renderiza si no se ha minimizado
            if (!isMinimized) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                )
            }

            // La fecha se desliza hacia la izquierda/arriba de forma fluida
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .align(if (isMinimized) Alignment.CenterStart else Alignment.BottomEnd)
                    .animateContentSize() // Suaviza el cambio de alineación
            )
        }

        if (isMinimized) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun TaskCreationSheetContent(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    priority: Priority,
    onPriorityChange: (Priority) -> Unit,
    onSave: (String, String, Priority) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        // Título
        TextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text("Nueva tarea...", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))) },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
        )

        // Descripción
        TextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Descripción (opcional)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            minLines = 1,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Barra de acciones
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Iconos de utilidad a la izquierda
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { }) { Icon(Icons.Sharp.DateRange, "Fecha", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = {
                    val nextPriority = when(priority) {
                        Priority.NOTHING -> Priority.LOW
                        Priority.LOW -> Priority.MEDIUM
                        Priority.MEDIUM -> Priority.HIGH
                        Priority.HIGH -> Priority.NOTHING
                    }
                    onPriorityChange(nextPriority)
                }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.flag_2_24px),
                        contentDescription = "Prioridad",
                        tint = getPriorityColor(priority)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Botón de Enviar
            FilledIconButton(
                onClick = { if (title.isNotBlank()) onSave(title, description, priority) },
                enabled = title.isNotBlank(),
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.send),
                    contentDescription = "Crear tarea",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
}

@Composable
fun TaskEditorSheetContent(
    task: Task?,
    onAutoUpdate: (String, String, Priority, List<SubTask>) -> Unit
) {
    val isNewTask = remember { task == null }
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var priority by remember { mutableStateOf(task?.priority ?: Priority.NOTHING) }
    val subTasks = remember {
        mutableStateListOf<SubTask>().apply {
            addAll(task?.subTasks ?: emptyList())
        }
    }

    // Checkpoints para saber qué es lo último que se guardó realmente
    var lastSavedTitle by remember { mutableStateOf(title) }
    var lastSavedDesc by remember { mutableStateOf(description) }
    var lastSavedPriority by remember { mutableStateOf(priority) }
    var lastSavedSubTasksHash by remember { mutableIntStateOf(subTasks.toList().hashCode()) }

    val focusRequester = remember { FocusRequester() }

    // AUTOGUARDADO POR TIEMPO (DEBOUNCE)
    if (!isNewTask) {
        LaunchedEffect(title, description, priority, subTasks.toList().hashCode()) {
            val currentSubTasksHash = subTasks.toList().hashCode()

            val hasChanges = title != lastSavedTitle ||
                    description != lastSavedDesc ||
                    priority != lastSavedPriority ||
                    currentSubTasksHash != lastSavedSubTasksHash

            if (hasChanges && title.isNotBlank()) {
                delay(600)

                // Guardamos
                onAutoUpdate(title, description, priority, subTasks.toList())

                // Actualizamos referencias
                lastSavedTitle = title
                lastSavedDesc = description
                lastSavedPriority = priority
                lastSavedSubTasksHash = currentSubTasksHash
            }
        }
    }

    // GUARDADO DE EMERGENCIA AL CERRAR (DISPOSE)
    // Esto se ejecuta si el usuario cierra el sheet antes de que termine el delay
    DisposableEffect(Unit) {
        onDispose {
            // Solo autoguardamos al salir si es una tarea YA EXISTENTE (Edición)
            if (!isNewTask) {
                val currentSubTasksHash = subTasks.toList().hashCode()
                val hasPendingChanges = title != lastSavedTitle ||
                        description != lastSavedDesc ||
                        priority != lastSavedPriority ||
                        currentSubTasksHash != lastSavedSubTasksHash

                if (hasPendingChanges && title.isNotBlank()) {
                    onAutoUpdate(title, description, priority, subTasks.toList())
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // TAREA
        TextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text("¿Qué hay que hacer?", style = MaterialTheme.typography.headlineSmall) },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
        )

        TextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Añadir detalles...", style = MaterialTheme.typography.bodyLarge) },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            minLines = 4,
            maxLines = 10,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        Spacer(modifier = Modifier.height(16.dp))

        // SUBTAREAS
        Text(
            "SUBTAREAS",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        subTasks.forEachIndexed { index, subTask ->
            SubTaskRow(
                subTask = subTask,
                onRemove = { subTasks.removeAt(index) },
                onTitleChange = { newTitle -> subTasks[index] = subTask.copy(title = newTitle) },
                onToggleDone = {
                    subTasks[index] = subTask.copy(isDone = !subTask.isDone)
                }
            )
        }

        AddSubTaskRow(onAdd = { newTitle -> subTasks.add(SubTask(title = newTitle)) })

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                val nextPriority = when(priority) {
                    Priority.NOTHING -> Priority.LOW
                    Priority.LOW -> Priority.MEDIUM
                    Priority.MEDIUM -> Priority.HIGH
                    Priority.HIGH -> Priority.NOTHING
                }
                priority = nextPriority
            }) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.flag_2_24px),
                    contentDescription = "Prioridad",
                    tint = getPriorityColor(priority)
                )
            }
            IconButton(onClick = { /* TODO: Implementar Fecha */ }) { Icon(Icons.Sharp.DateRange, "Fecha") }
            IconButton(onClick = { /* TODO: Implementar Formato */ }) { Icon(Icons.Sharp.Create, "Formato") }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    LaunchedEffect(Unit) {
        delay(100)
        if (isNewTask) focusRequester.requestFocus()
    }
}

// ---------------------------------------------
// Componentes Auxiliares
// ---------------------------------------------

@Composable
fun ExpandableFabMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onTaskClick: () -> Unit,
    onIdeaClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 135f else 0f, label = "rotation"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FabOption("Idea", ImageVector.vectorResource(R.drawable.lightbulb_24px), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, onIdeaClick)
                FabOption("Tarea", Icons.Default.Check, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, onTaskClick)
            }
        }

        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, "Menu", modifier = Modifier.rotate(rotation))
        }
    }
}

@Composable
fun FabOption(text: String, icon: ImageVector, containerColor: Color, contentColor: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
        }
        SmallFloatingActionButton(onClick = onClick, containerColor = containerColor, contentColor = contentColor) {
            Icon(icon, text)
        }
    }
}

@Composable
fun EmptyStateView() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Check, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
        Text("Todo limpio", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
        // CASO BORRAR: Desplazamiento a la izquierda
        slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(300)
        ) + shrinkVertically(
            animationSpec = tween(durationMillis = 280, delayMillis = 100),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(200))
    } else {
        // CASO COMPLETAR: Desvanecer + Contraer suave
        fadeOut(
            animationSpec = tween(300)
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
    val direction = state.dismissDirection
    val isDeleteDirection = direction == SwipeToDismissBoxValue.EndToStart

    val color = if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
        MaterialTheme.colorScheme.errorContainer
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 30.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (isDeleteDirection) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Borrar",
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
    onClicked: () -> Unit,
    onCheckClicked: () -> Unit
) {
    val priorityColor = getPriorityColor(priority)
    Card(
        onClick = onClicked,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        if (priority != Priority.NOTHING && !isDone) {
                            drawIntoCanvas { canvas ->
                                val paint = Paint().asFrameworkPaint()
                                paint.color = priorityColor.copy(alpha = 0.25f).toArgb()
                                paint.maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.INNER)
                                canvas.nativeCanvas.drawCircle(
                                    size.width / 2f,
                                    size.height / 2f,
                                    size.width / 2.2f,
                                    paint
                                )
                            }
                        }
                    }
            ) {
                Icon(
                    imageVector = if (isDone) ImageVector.vectorResource(R.drawable.radio_button_checked_24px) else ImageVector.vectorResource(
                        R.drawable.radio_button_unchecked_24px
                    ),
                    contentDescription = null,
                    tint = if (isDone) MaterialTheme.colorScheme.primary else if (priority == Priority.NOTHING) MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.4f
                    ) else priorityColor,
                    modifier = Modifier
                        .size(24.dp)
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
                    fontSize = 15.sp,
                    lineHeight = 19.sp
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

                if (total > 0 && completed < total) {

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer { alpha = 0.72f }
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.subtask_24px),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$completed/$total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubTaskRow(
    subTask: SubTask,
    onRemove: () -> Unit,
    onTitleChange: (String) -> Unit,
    onToggleDone: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        // Checkbox para completar subtarea
        Icon(
            imageVector = if (subTask.isDone)
                ImageVector.vectorResource(R.drawable.radio_button_checked_24px)
            else
                ImageVector.vectorResource(R.drawable.radio_button_unchecked_24px),
            contentDescription = null,
            tint = if (subTask.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable { onToggleDone() }
                .padding(2.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        TextField(
            value = subTask.title,
            onValueChange = onTitleChange,
            modifier = Modifier.weight(1f),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (subTask.isDone) TextDecoration.LineThrough else null,
                color = if (subTask.isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true,
            placeholder = { Text("Subtarea...") }
        )

        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Borrar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AddSubTaskRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Añadir subtarea...", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (text.isNotBlank()) { onAdd(text); text = "" }
            })
        )
        if (text.isNotBlank()) {
            IconButton(onClick = { onAdd(text); text = "" }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Añadir", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun getPriorityColor(priority: Priority): Color {
    return when (priority) {
        Priority.HIGH -> MaterialTheme.priority.highPriority
        Priority.MEDIUM -> MaterialTheme.priority.mediumPriority
        Priority.LOW -> MaterialTheme.priority.lowPriority
        Priority.NOTHING -> MaterialTheme.priority.noPriority
    }
}