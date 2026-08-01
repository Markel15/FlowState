package com.markel.flowstate.feature.habits.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markel.flowstate.feature.habits.R
import com.markel.flowstate.core.designsystem.R as DesignR

/**
 * Expandable FAB menu for the Habits screen.
 *
 * The two entry points decide the habit type up-front, which is what lets
 * [AddHabitSheet] show a type-specific form instead of an in-dialog selector.
 *
 * The toggle icon is a single `add` glyph rotated 45° into a "close" (×), so no
 * extra close drawable is needed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HabitFabMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onBooleanHabitClick: () -> Unit,
    onNumericHabitClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { onToggle() },
                modifier = Modifier.animateFloatingActionButton(
                    visible = visible || expanded,
                    alignment = Alignment.BottomEnd,
                ),
                containerSize = ToggleFloatingActionButtonDefaults.containerSizeMedium()
            ) {
                val progress = checkedProgress

                Icon(
                    imageVector = ImageVector.vectorResource(DesignR.drawable.add_24px),
                    contentDescription = if (expanded) "Close menu" else "Add habit",
                    tint = lerp(
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.onPrimary,
                        progress
                    ),
                    modifier = Modifier
                        .size(FloatingActionButtonDefaults.MediumIconSize)
                        .graphicsLayer { rotationZ = 45f * progress } // add → ×
                )
            }
        }
    ) {
        FloatingActionButtonMenuItem(
            onClick = onNumericHabitClick,
            icon = {
                Icon(
                    ImageVector.vectorResource(R.drawable.tag_24px),
                    modifier = Modifier.size(24.dp),
                    contentDescription = null
                )
            },
            text = { Text(stringResource(R.string.habit_type_numeric), fontSize = 16.sp) }
        )
        FloatingActionButtonMenuItem(
            onClick = onBooleanHabitClick,
            icon = {
                Icon(
                    ImageVector.vectorResource(DesignR.drawable.check_24px),
                    modifier = Modifier.size(24.dp),
                    contentDescription = null
                )
            },
            text = { Text(stringResource(R.string.habit_type_boolean), fontSize = 16.sp) }
        )
    }
}