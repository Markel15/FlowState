package com.markel.flowstate.feature.tasks.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.markel.flowstate.core.designsystem.theme.priority
import com.markel.flowstate.core.domain.Priority

@Composable
fun Priority.asColor(): Color {
    return when (this) {
        Priority.HIGH -> MaterialTheme.priority.highPriority
        Priority.MEDIUM -> MaterialTheme.priority.mediumPriority
        Priority.LOW -> MaterialTheme.priority.lowPriority
        Priority.NOTHING -> MaterialTheme.priority.noPriority
    }
}