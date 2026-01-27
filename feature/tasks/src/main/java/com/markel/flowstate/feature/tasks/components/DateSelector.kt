package com.markel.flowstate.feature.tasks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.DateRange
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.markel.flowstate.feature.tasks.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelector(
    dueDate: Long?,
    onDueDateChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val mainColor = MaterialTheme.colorScheme.tertiary
    val datePickerColors = DatePickerDefaults.colors(
        selectedDayContainerColor = mainColor,
        selectedDayContentColor = MaterialTheme.colorScheme.onTertiary,
        todayDateBorderColor = mainColor,
        todayContentColor = mainColor,
        dayContentColor = MaterialTheme.colorScheme.onSurface,
    )
    val textButtonColors = ButtonDefaults.textButtonColors(
        contentColor = mainColor
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dueDate != null) {
                        TextButton(
                            onClick = {
                                showDatePicker = false
                                onDueDateChange(null)
                            },
                            colors = textButtonColors
                        ) {
                            Text(stringResource(R.string.clear_cal))
                        }
                    }

                    TextButton(
                        onClick = { showDatePicker = false },
                        colors = textButtonColors
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    TextButton(
                        onClick = {
                            showDatePicker = false
                            onDueDateChange(datePickerState.selectedDateMillis)
                        },
                        colors = textButtonColors
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = datePickerColors
            )
        }
    }

    if (dueDate != null && showLabel) {
        AssistChip(
            onClick = { showDatePicker = true },
            label = {
                Text(
                    formatDate(dueDate),
                    color = if (isDateOverdue(dueDate)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onTertiary
                    }
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Sharp.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isDateOverdue(dueDate)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onTertiary
                    }
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (isDateOverdue(dueDate)) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
            ),
            border = null,
            modifier = modifier
        )
    } else {
        IconButton(
            onClick = { showDatePicker = true },
            modifier = modifier
        ) {
            Icon(
                Icons.Sharp.DateRange,
                "Date",
                tint = if (dueDate != null) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun isDateOverdue(timestamp: Long): Boolean {
    val date = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val today = LocalDate.now()
    return date.isBefore(today)
}
@Composable
fun formatDate(timestamp: Long?): String {
    if (timestamp == null) return ""
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()

    return when(date) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> DateTimeFormatter.ofPattern("d MMM").format(date)
    }
}