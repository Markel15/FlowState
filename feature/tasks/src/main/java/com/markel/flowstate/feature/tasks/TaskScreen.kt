package com.markel.flowstate.feature.tasks

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markel.flowstate.core.domain.Task
import kotlinx.coroutines.delay

/**
 * La pantalla (Composable) que muestra la lista de tareas.
 */
@Composable
fun TaskScreen(viewModel: TaskViewModel) {

    // Recolectamos el estado (la lista de tareas) del ViewModel
    // de una forma segura para el ciclo de vida (Lifecycle)
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()

    // Estado local para el campo de texto de nueva tarea
    var newTaskTitle by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Mis Tareas", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)

        // --- Input para nuevas tareas ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTaskTitle,
                onValueChange = { newTaskTitle = it },
                label = { Text("Nueva tarea...") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                viewModel.addTask(newTaskTitle)
                newTaskTitle = "" // Limpia el campo
            }) {
                Text("Añadir")
            }
        }

        // --- Lista de tareas ---
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(tasks, key = { it.id }) { task ->
                AnimatableTaskItem(
                    task = task,
                    onDelete = { viewModel.deleteTask(task) },
                    onComplete = { viewModel.toggleTaskDone(task) }
                )
            }
        }
    }
}

/**
 * Un Composable para un único item de la lista de tareas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatableTaskItem(
    task: Task,
    onDelete: () -> Unit,
    onComplete: () -> Unit
) {
    // Estado local: ¿Es visible este item?
    var isVisible by remember { mutableStateOf(true) }

    // Estado local: ¿Está marcado como hecho (para tacharlo visualmente antes de que desaparezca)?
    var isChecked by remember { mutableStateOf(task.isDone) }

    // Efecto secundario: Cuando 'isVisible' cambia a false...
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            // Esperamos a que termine la animación
            delay(400)
            // Llamamos al ViewModel para actualizar la DB
            onComplete()
        }
    }

    // AnimatedVisibility maneja el fadeOut (opacidad) y shrinkVertically (colapso de altura)
    AnimatedVisibility(
        visible = isVisible,  // cuando visible cambie a false, se aplicará la animación de salida definida (exit)
        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(400))
    ) {
        // Envolvemos en SwipeToDelete
        SwipeToDeleteContainer(
            item = task,
            onDelete = onDelete
        ) {
            TaskItemContent(
                title = task.title,
                isDone = isChecked,
                onClicked = {
                    // Tachamos visualmente al instante
                    isChecked = !isChecked
                    // Iniciamos la animación de salida (esto dispara el LaunchedEffect de arriba)
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
            if (value == SwipeToDismissBoxValue.EndToStart &&
                dismissState.progress >= threshold  // Evita que un fling rápido cuente como eliminación
            ) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    val shape = RoundedCornerShape(12.dp)

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
                    .clip(shape)
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
    isDone: Boolean,
    onClicked: () -> Unit
) {
    Card(
        onClick = { onClicked() },
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
                imageVector = if (isDone) ImageVector.vectorResource(R.drawable.radio_button_checked_24px) else ImageVector.vectorResource(R.drawable.radio_button_unchecked_24px),
                contentDescription = null,
                tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = if (isDone) {
                    MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}