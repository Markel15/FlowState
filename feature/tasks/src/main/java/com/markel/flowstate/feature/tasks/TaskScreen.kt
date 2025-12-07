package com.markel.flowstate.feature.tasks

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markel.flowstate.core.domain.Task

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
                TaskItem(
                    task = task,
                    onTaskClicked = { viewModel.toggleTaskDone(task) }
                )
            }
        }
    }
}

/**
 * Un Composable para un único item de la lista de tareas.
 */
@Composable
fun TaskItem(
    task: Task,
    onTaskClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTaskClicked() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.isDone,
            onCheckedChange = { onTaskClicked() }
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = task.title,
            style = if (task.isDone) {
                androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                androidx.compose.material3.MaterialTheme.typography.bodyLarge
            }
        )
    }
}