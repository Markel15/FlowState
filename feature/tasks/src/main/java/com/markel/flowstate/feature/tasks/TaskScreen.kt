package com.markel.flowstate.feature.tasks

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.domain.Task
import kotlinx.coroutines.delay
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
    // ESTADO PARA LA EDICIÓN/CREACIÓN
    // Si es null, estamos creando. Si tiene una tarea, estamos editando.
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !showDialog,
                    enter = scaleIn(),
                    exit = scaleOut()
                ) {
                    ExpandableFabMenu(
                        expanded = isFabExpanded,
                        onToggle = { isFabExpanded = !isFabExpanded },
                        onTaskClick = { isFabExpanded = false; taskToEdit=null; showDialog = true },
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
                                AnimatableTaskItem(
                                    task = task,
                                    onDelete = { viewModel.deleteTask(task) },
                                    onComplete = { viewModel.toggleTaskDone(task) },
                                    onContentClick = {
                                        taskToEdit = task // Modo EDITAR
                                        showDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        TaskFormDialog(
            isVisible = showDialog,
            taskToEdit = taskToEdit, // Pasamos la tarea si existe
            onDismiss = { showDialog = false },
            onSave = { title, description ->
                if (taskToEdit == null) {
                    // Crear nueva
                    viewModel.addTask(title, description)
                } else {
                    // Actualizar existente
                    viewModel.updateTask(taskToEdit!!, title, description)
                }
                showDialog = false
            }
        )
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

/**
 * Un diálogo personalizado que simula nacer desde el FAB (Esquina inferior derecha).
 */
@Composable
fun TaskFormDialog(
    isVisible: Boolean,
    taskToEdit: Task?,  // Null = Crear, Task = Editar
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    // Usamos AnimatedVisibility para controlar la entrada/salida fluida
    AnimatedVisibility(
        visible = isVisible,
        // La animación que escala desde la esquina inferior derecha (donde estaba el FAB)
        enter = fadeIn(animationSpec = tween(200)) +
                scaleIn(
                    initialScale = 0.1f,
                    transformOrigin = TransformOrigin(0.9f, 0.9f), // Origen: Abajo-Derecha
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ),
        exit = fadeOut(animationSpec = tween(200)) +
                scaleOut(
                    targetScale = 0.1f,
                    transformOrigin = TransformOrigin(0.9f, 0.9f),
                    animationSpec = tween(250, easing = LinearOutSlowInEasing)
                )
    ) {
        // Fondo semitransparente (Scrim) que cierra al pulsar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // Contenido de la tarjeta (evitamos que el click en la tarjeta cierre el diálogo)
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f) // Ancho de la tarjeta
                    .wrapContentHeight()
                    .clickable(enabled = false) {}, // Absorbe clicks
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                TaskFormContent(
                    initialTitle = taskToEdit?.title ?: "",
                    initialDescription = taskToEdit?.description ?: "",
                    isEditing = taskToEdit != null,
                    onCancel = onDismiss,
                    onSave = onSave
                )
            }
        }
    }
}

@Composable
fun TaskFormContent(
    initialTitle: String,
    initialDescription: String,
    isEditing: Boolean,
    onCancel: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(initialTitle, initialDescription) {
        title = initialTitle
        description = initialDescription
    }

    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = if (isEditing) "Editar Tarea" else "Nueva Tarea",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text("¿Qué tienes en mente?") },
            singleLine = false, // Permite multilínea si la idea es larga
            maxLines = 3,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Añadir detalles o descripción...") },
            maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (title.isNotBlank()) onSave(title, description)
            })
        )

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancelar")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { if (title.isNotBlank()) onSave(title,description) },
                enabled = title.isNotBlank()
            ) {
                Text("Guardar")
            }
        }
    }

    // Solicitar foco automáticamente
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
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
        )
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
        backgroundContent = {
            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Borrar",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = { content(item) }
    )
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
            IconButton(onClick = onCheckClicked) {
                Icon(
                    imageVector = if (isDone) ImageVector.vectorResource(R.drawable.radio_button_checked_24px) else ImageVector.vectorResource(
                        R.drawable.radio_button_unchecked_24px
                    ),
                    contentDescription = null,
                    tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.4f
                    ),
                    modifier = Modifier.size(24.dp)
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
            }
        }
    }
}