package com.markel.flowstate.feature.flow.tasks.util

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.markel.flowstate.core.designsystem.theme.priority
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.feature.tasks.R

@Composable
fun Priority.asColor(): Color {
    return when (this) {
        Priority.HIGH -> MaterialTheme.priority.highPriority
        Priority.MEDIUM -> MaterialTheme.priority.mediumPriority
        Priority.LOW -> MaterialTheme.priority.lowPriority
        Priority.NOTHING -> MaterialTheme.priority.noPriority
    }
}

/**
 * The flag icon for a [Priority]: outlined when there is no priority (the neutral,
 * "empty" affordance) and filled once a real priority is set. Filled-when-active is
 * the same convention the bottom navigation already uses (iconSelectedRes vs iconRes),
 * and it gives priority a shape channel on top of the color one.
 *
 * The filled glyph covers the exact same silhouette as the outlined one, so toggling
 * between states never shifts geometry.
 */
@DrawableRes
fun Priority.flagIconRes(): Int =
    if (this == Priority.NOTHING) R.drawable.flag_2_24px else R.drawable.flag_2_filled_24px