package com.markel.flowstate.feature.flow.tasks.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp

/**
 * Quiet metadata strip for the task editor: due date and reminder live
 * together under the description as small OUTLINED chips (the classic M3
 * assist-chip look: no fill, subtle border) — state visible at a glance, one
 * tap to change. Unset entries show the same outlined chip whose label names
 * what they add. Wraps to a second line on narrow screens via [FlowRow].
 */
@Composable
fun TaskMetadataChips(
    dueDate: Long?,
    onDueDateChange: (Long?) -> Unit,
    reminderTime: Long?,
    onReminderTimeChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        DateSelector(
            dueDate = dueDate,
            onDueDateChange = onDueDateChange,
            showLabel = true,
            ghostChipWhenUnset = true
        )
        ReminderSelector(
            reminderTime = reminderTime,
            onReminderTimeChange = onReminderTimeChange,
            ghostChipWhenUnset = true
        )
    }
}

/**
 * Shared look for an UNSET metadata entry: transparent chip with the default
 * assist-chip outline, icon + label naming what it adds. Used by
 * [DateSelector] and [ReminderSelector] so every "add" affordance in the row
 * reads identically.
 */
@Composable
internal fun GhostMetaChip(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = ImageVector.vectorResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color.Transparent
        ),
        border = AssistChipDefaults.assistChipBorder(enabled = true),
        modifier = modifier
    )
}
