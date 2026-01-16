package com.markel.flowstate.feature.tasks

import android.R.attr.translationZ
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
import androidx.compose.material.icons.sharp.Create
import androidx.compose.material.icons.sharp.DateRange
import androidx.compose.material.icons.sharp.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import com.markel.flowstate.core.domain.Task
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
                                            .draggableHandle(
                                                interactionSource = remember { MutableInteractionSource() }
                                            )
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
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
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                TaskEditorSheetContent(
                    task = taskToEdit,
                    onSave = { title, desc ->
                        if (taskToEdit == null) viewModel.addTask(title, desc)
                        else viewModel.updateTask(taskToEdit!!, title, desc)
                        showSheet = false
                    },
                    onCancel = { showSheet = false }
                )
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
fun TaskEditorSheetContent(
    task: Task?,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding() // Respeta la barra de navegación del sistema
            .imePadding() // Sube el contenido cuando sale el teclado
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // Título
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

        // Descripción
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

        // --- BARRA DE ACCIONES ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* TODO: Prioridad */ }) {
                Icon(Icons.Sharp.MoreVert, "Prioridad", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { /* TODO: Fecha */ }) {
                Icon(Icons.Sharp.DateRange, "Fecha", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { /* TODO: Formato de texto */ }) {
                Icon(Icons.Sharp.Create, "Formato", tint = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onCancel) {
                Text("Cancelar")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { if (title.isNotBlank()) onSave(title, description) },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                Text(if (task == null) "Crear" else "Listo")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    LaunchedEffect(Unit) {
        delay(100) // Esperar a que el sheet suba un poco
        if (task == null) focusRequester.requestFocus()
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
                isDone = isChecked,
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
    isDone: Boolean,
    onClicked: () -> Unit,
    onCheckClicked: () -> Unit
) {
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
            Icon(
                imageVector = if (isDone) ImageVector.vectorResource(R.drawable.radio_button_checked_24px) else ImageVector.vectorResource(
                    R.drawable.radio_button_unchecked_24px
                ),
                contentDescription = null,
                tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.4f
                ),
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .toggleable(
                        value = isDone,
                        onValueChange = { onCheckClicked() },
                        role = Role.Checkbox
                    )
            )
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
            }
        }
    }
}