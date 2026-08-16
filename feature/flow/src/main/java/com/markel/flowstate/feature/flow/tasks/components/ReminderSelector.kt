package com.markel.flowstate.feature.flow.tasks.components

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.markel.flowstate.feature.tasks.R
import com.markel.flowstate.feature.flow.tasks.util.HandleSystemBars
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Reminder selector: lets the user pick a date AND a time, producing a single
 * epoch-millis value suitable for scheduling an exact alarm.
 *
 * The component is fully independent of DateSelector (which handles due dates
 * and only needs day precision). This one uses a two-step dialog flow:
 *   1. DatePickerDialog  →  picks the day
 *   2. TimePickerDialog  →  picks the hour/minute
 *
 * Displays a subtle icon button when no reminder is set, or an AssistChip
 * (styled differently from the due-date chip) when a reminder is active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSelector(
    reminderTime: Long?,
    onReminderTimeChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    ghostChipWhenUnset: Boolean = false,
    filled: Boolean = false
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Intermediate: store the chosen day while waiting for the time step.
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }

    var waitingForPermissionResult by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        if (waitingForPermissionResult) {
            showDatePicker = true
            waitingForPermissionResult = false
        }
    }

    fun openReminderPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                waitingForPermissionResult = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        showDatePicker = true
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val accentColor = MaterialTheme.colorScheme.tertiary
    val textButtonColors = ButtonDefaults.textButtonColors(contentColor = accentColor)

    // ── Step 1: Date picker ───────────────────────────────────────────────────
    if (showDatePicker) {
        val initialMillis = reminderTime ?: System.currentTimeMillis()

        val selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val selectedDate = Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneId.of("UTC"))
                    .toLocalDate()
                return !selectedDate.isBefore(LocalDate.now())
            }
        }

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            initialDisplayMode = if (isLandscape) DisplayMode.Input else DisplayMode.Picker,
            selectableDates = selectableDates
        )
        val datePickerColors = DatePickerDefaults.colors(
            selectedDayContainerColor = accentColor,
            selectedDayContentColor = MaterialTheme.colorScheme.onTertiary,
            todayDateBorderColor = accentColor,
            todayContentColor = accentColor
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (reminderTime != null) {
                        TextButton(
                            onClick = {
                                showDatePicker = false
                                onReminderTimeChange(null)
                            },
                            colors = textButtonColors
                        ) { Text(stringResource(R.string.clear_cal)) }
                    }

                    TextButton(
                        onClick = { showDatePicker = false },
                        colors = textButtonColors
                    ) { Text(stringResource(R.string.cancel)) }

                    TextButton(
                        onClick = {
                            showDatePicker = false
                            datePickerState.selectedDateMillis?.let { millis ->
                                pendingDate = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.of("UTC"))
                                    .toLocalDate()
                                showTimePicker = true
                            }
                        },
                        colors = textButtonColors
                    ) { Text(stringResource(R.string.next)) }
                }
            }
        ) {
            HandleSystemBars(isLandscape)
            DatePicker(state = datePickerState, colors = datePickerColors)
        }
    }

    // ── Step 2: Time picker ───────────────────────────────────────────────────
    if (showTimePicker) {
        // Pre-fill with existing reminder time or a default (09:00)
        val existingTime = reminderTime?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
        } ?: LocalTime.of(9, 0)

        val is24Hour = DateFormat.is24HourFormat(context)

        val timePickerState = rememberTimePickerState(
            initialHour = existingTime.hour,
            initialMinute = existingTime.minute,
            is24Hour = is24Hour
        )

        // Toggle between clock face (Picker) and text input (Keyboard) mode
        var isKeyboardMode by remember { mutableStateOf(false) }

        val timeAccentColor = MaterialTheme.colorScheme.tertiary
        val onTimeAccentColor = MaterialTheme.colorScheme.onTertiary

        val timePickerColors = TimePickerDefaults.colors(
            // Clock dial
            clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
            selectorColor = timeAccentColor,
            clockDialSelectedContentColor = onTimeAccentColor,
            clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            // Hour/minute chips at the top
            timeSelectorSelectedContainerColor = timeAccentColor,
            timeSelectorSelectedContentColor = onTimeAccentColor,
            timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
            // AM/PM period selector
            periodSelectorSelectedContainerColor = timeAccentColor,
            periodSelectorSelectedContentColor = onTimeAccentColor,
            periodSelectorUnselectedContainerColor = Color.Transparent,
            periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
            periodSelectorBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isKeyboardMode = !isKeyboardMode }
                    ) {
                        Icon(
                            imageVector = if (isKeyboardMode) {
                                ImageVector.vectorResource(R.drawable.schedule_24px)
                            } else {
                                ImageVector.vectorResource(R.drawable.keyboard_alt_24px)
                            },
                            contentDescription = if (isKeyboardMode) {
                                "Switch to clock"
                            } else {
                                "Switch to keyboard"
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = { showTimePicker = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = timeAccentColor)
                    ) { Text(stringResource(R.string.cancel)) }

                    val pastTimeErrorMsg = stringResource(R.string.reminder_past_time_error)
                    TextButton(
                        onClick = {
                            pendingDate?.let { date ->
                                val millis = ZonedDateTime.of(
                                    date,
                                    LocalTime.of(timePickerState.hour, timePickerState.minute),
                                    ZoneId.systemDefault()
                                ).toInstant().toEpochMilli()
                                if (millis <= System.currentTimeMillis()) {
                                    Toast.makeText(
                                        context,
                                        pastTimeErrorMsg,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    showTimePicker = false
                                    onReminderTimeChange(millis)
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = timeAccentColor)
                    ) { Text(stringResource(R.string.ok)) }
                }
            },
            title = { Text(stringResource(R.string.reminder_time_title)) },
            text = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    if (isKeyboardMode) {
                        TimeInput(
                            state = timePickerState,
                            colors = timePickerColors
                        )
                    } else {
                        TimePicker(
                            state = timePickerState,
                            colors = timePickerColors
                        )
                    }
                }
            }
        )
    }

    // ── Trigger UI ────────────────────────────────────────────────────────────
    // Same dual style as DateSelector: outlined (editor metadata strip) or
    // filled (capture contexts — creation sheet).
    if (reminderTime != null) {
        val contentColor = if (filled) MaterialTheme.colorScheme.onTertiary
        else MaterialTheme.colorScheme.tertiary
        AssistChip(
            onClick = { openReminderPicker() },
            label = {
                Text(
                    formatReminderDateTime(reminderTime),
                    color = contentColor
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.notifications_active_24px),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (filled) MaterialTheme.colorScheme.tertiary
                else Color.Transparent
            ),
            border = if (filled) null else AssistChipDefaults.assistChipBorder(enabled = true),
            modifier = modifier
        )
    } else if (ghostChipWhenUnset) {
        GhostMetaChip(
            label = stringResource(R.string.task_meta_add_reminder),
            iconRes = R.drawable.notification_add_24px,
            onClick = { openReminderPicker() },
            modifier = modifier
        )
    } else {
        IconButton(
            onClick = { openReminderPicker() },
            modifier = modifier
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.notification_add_24px),
                contentDescription = "Notification",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Formats the reminder timestamp as a human-readable string.
 * Examples: "Today 09:00", "Tomorrow 14:30", "15 Jun 08:00"
 */
@Composable
fun formatReminderDateTime(timestamp: Long): String {
    val zdt = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    val date = zdt.toLocalDate()
    val time = zdt.toLocalTime()
    val today = LocalDate.now()

    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
    val timeStr = DateTimeFormatter.ofPattern(timePattern).format(time)
    val dateStr = when (date) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> DateTimeFormatter.ofPattern("d MMM").format(date)
    }
    return "$dateStr $timeStr"
}
