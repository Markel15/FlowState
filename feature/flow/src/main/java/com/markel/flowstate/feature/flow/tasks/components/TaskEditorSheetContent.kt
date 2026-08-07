package com.markel.flowstate.feature.flow.tasks.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.SubTask
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.feature.flow.components.CategorySelectorSheet
import com.markel.flowstate.feature.tasks.R
import kotlinx.coroutines.delay
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TaskEditorSheetContent(
    task: Task?,
    priority: Priority, // Get it from the parent
    dueDate: Long?,
    remTime: Long?,
    onAutoUpdate: (String, String, Priority, Long?, Long?, List<SubTask>) -> Unit,
    onDueDateChange: (Long?) -> Unit = {},
    onReminderTimeChange: (Long?) -> Unit = {},
    categories: List<Category> = emptyList(),
    categoriesEnabled: Boolean = false,
    categoryId: Int? = null,
    onCategoryChange: (Int?) -> Unit = {},
    generalCategoryName: String? = null
) {
    val isNewTask = remember { task == null }
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    val subTasks = remember {
        mutableStateListOf<SubTask>().apply {
            addAll(task?.subTasks ?: emptyList())
        }
    }

    // Track which subtask is expanded for inline editing
    var expandedSubTaskId by remember { mutableStateOf<String?>(null) }

    // Controls whether the completed subtasks section is expanded
    var completedExpanded by remember { mutableStateOf(false) }

    // States to show the creation sheet when creating a subtask
    var showCreationSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // State to show the category selector sheet
    var showCategorySelector by remember { mutableStateOf(false) }
    val defaultGeneralName = stringResource(R.string.category_general)
    val generalName = generalCategoryName?.takeIf { it.isNotBlank() } ?: defaultGeneralName
    // Draft states for the new subtask
    var draftSubTitle by rememberSaveable { mutableStateOf("") }
    var draftSubDescription by rememberSaveable { mutableStateOf("") }
    var draftSubPriority by rememberSaveable { mutableStateOf(Priority.NOTHING) }
    var draftSubDueDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftSubReminder by rememberSaveable { mutableStateOf<Long?>(null) }

    // Checkpoints to track what was actually last saved
    var lastSavedTitle by remember { mutableStateOf(title) }
    var lastSavedDesc by remember { mutableStateOf(description) }
    var lastSavedPriority by remember { mutableStateOf(priority) }
    var lastSavedDueDate by remember { mutableStateOf(dueDate) }
    var lastSavedReminder by remember { mutableStateOf(remTime) }
    var lastSavedSubTasksHash by remember { mutableIntStateOf(subTasks.toList().hashCode()) }

    val focusRequester = remember { FocusRequester() }

    val currentPriority by rememberUpdatedState(priority)
    val currentDueDate by rememberUpdatedState(dueDate)
    val currentReminder by rememberUpdatedState(remTime)
    val currentTitle by rememberUpdatedState(title)
    val currentDescription by rememberUpdatedState(description)
    val currentSubTasksList by rememberUpdatedState(subTasks.toList())

    // TIME-BASED AUTOSAVE (DEBOUNCE)
    if (!isNewTask) {
        LaunchedEffect(title, description, priority, dueDate, remTime, subTasks.toList().hashCode()) {
            val currentSubTasksHash = subTasks.toList().hashCode()

            val hasChanges = title != lastSavedTitle ||
                    description != lastSavedDesc ||
                    priority != lastSavedPriority ||
                    dueDate != lastSavedDueDate ||
                    remTime != lastSavedReminder ||
                    currentSubTasksHash != lastSavedSubTasksHash

            if (hasChanges && title.isNotBlank()) {
                delay(600)
                // Save
                onAutoUpdate(title, description, priority, dueDate, remTime, subTasks.toList())
                // Update references
                lastSavedTitle = title
                lastSavedDesc = description
                lastSavedPriority = priority
                lastSavedDueDate = dueDate
                lastSavedReminder = remTime
                lastSavedSubTasksHash = currentSubTasksHash
            }
        }
    }

    // EMERGENCY SAVE ON CLOSE (DISPOSE)
    // This runs if the user closes the sheet before the delay finishes
    DisposableEffect(Unit) {
        onDispose {
            // Only autosave on exit if it's an ALREADY EXISTING task (Editing)
            if (!isNewTask) {
                val currentSubTasksHash = currentSubTasksList.hashCode()
                val hasPendingChanges = currentTitle != lastSavedTitle ||
                        currentDescription != lastSavedDesc ||
                        currentPriority != lastSavedPriority ||
                        currentDueDate != lastSavedDueDate ||
                        currentReminder != lastSavedReminder ||
                        currentSubTasksHash != lastSavedSubTasksHash

                if (hasPendingChanges && currentTitle.isNotBlank()) {
                    onAutoUpdate(
                        currentTitle,
                        currentDescription,
                        currentPriority,
                        currentDueDate,
                        currentReminder,
                        currentSubTasksList
                    )
                }
            }
        }
    }

    Column (
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Category selector (only when categories are enabled) ──────────────
        if (categoriesEnabled) {
            val defaultGeneralName = stringResource(R.string.category_general)
            val generalName = generalCategoryName?.takeIf { it.isNotBlank() } ?: defaultGeneralName
            val currentCategoryName = if (categoryId == null || categoryId == Category.GENERAL_ID) {
                generalName
            } else {
                categories.firstOrNull { it.id == categoryId }?.name ?: generalName
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showCategorySelector = true }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = currentCategoryName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.size(4.dp))
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.arrow_drop_down_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }


        // ── Title & description
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            if (title.isEmpty()) {
                Text(
                    text = stringResource(R.string.edit_task_placeholder),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 30.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 30.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
        }

        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            if (description.isEmpty()) {
                Text(
                    text = stringResource(R.string.edit_task_desc_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            BasicTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
        }

        // ── Metadata: due date · reminder as outlined chips ───────────
        Spacer(modifier = Modifier.height(10.dp))
        TaskMetadataChips(
            dueDate = dueDate,
            onDueDateChange = onDueDateChange,
            reminderTime = remTime,
            onReminderTimeChange = onReminderTimeChange
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SUBTASKS — computed lists & completed-chevron state
        val pendingSubTasks = remember(subTasks.toList()) {
            subTasks.filter { !it.isDone }
        }
        val completedSubTasks = remember(subTasks.toList()) {
            subTasks.filter { it.isDone }
        }

        val chevronRotation by animateFloatAsState(
            targetValue = if (completedExpanded) 180f else 0f,
            label = "chevron_rotation"
        )

        // ── Subtasks section ────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.subtask_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.subtasks),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Pending list + "add" row
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            pendingSubTasks.forEachIndexed { index, subTask ->
                val isFirst = index == 0
                val isLast = index == pendingSubTasks.lastIndex
                val isSingle = pendingSubTasks.size == 1
                val shape = when {
                    isSingle -> RoundedCornerShape(16.dp)
                    isFirst  -> RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = 4.dp, bottomEnd = 4.dp
                    )
                    isLast   -> RoundedCornerShape(
                        topStart = 4.dp, topEnd = 4.dp,
                        bottomStart = 16.dp, bottomEnd = 16.dp
                    )
                    else -> RoundedCornerShape(4.dp)
                }

                EditableSubTaskItem(
                    subTask = subTask,
                    isExpanded = expandedSubTaskId == subTask.id,
                    itemShape = shape,
                    onExpandChange = { shouldExpand ->
                        expandedSubTaskId = if (shouldExpand) subTask.id else null
                    },
                    onUpdate = { updatedSubTask ->
                        val realIndex = subTasks.indexOfFirst { it.id == updatedSubTask.id }
                        if (realIndex != -1) {
                            subTasks[realIndex] = updatedSubTask
                        }
                    },
                    onCheckedChange = {
                        // We need to find the index in the REAL list, not the visible list
                        val realIndex = subTasks.indexOfFirst { it.id == subTask.id }
                        if (realIndex != -1) {
                            subTasks[realIndex] = subTask.copy(isDone = !subTask.isDone)
                        }
                    },
                    onDelete = {
                        val realIndex = subTasks.indexOfFirst { it.id == subTask.id }
                        if (realIndex != -1) subTasks.removeAt(realIndex)
                        // Close expansion if this was the expanded item
                        if (expandedSubTaskId == subTask.id) expandedSubTaskId = null
                    }
                )
            }

        }

        Spacer(modifier = Modifier.height(10.dp))

        // Add subtask: small button centered under the group
        FilledTonalButton(
            onClick = {
                // Close any expanded subtask before opening the creation sheet
                expandedSubTaskId = null
                showCreationSheet = true
            },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.add_24px),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.add_subtask))
        }

        // ── Completed subtasks section ──────────────────────────────────────
        if (completedSubTasks.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { completedExpanded = !completedExpanded }
                    )
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = pluralStringResource(
                        id = R.plurals.completed_items,
                        count = completedSubTasks.size,
                        completedSubTasks.size
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.expand_more_40px),
                    contentDescription = if (completedExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }

            // Animated completed subtasks list
            AnimatedVisibility(
                visible = completedExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    completedSubTasks.forEachIndexed { index, subTask ->
                        val isFirst = index == 0
                        val isLast = index == completedSubTasks.lastIndex
                        val isSingle = completedSubTasks.size == 1
                        val shape = when {
                            isSingle -> RoundedCornerShape(16.dp)
                            isFirst  -> RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = 4.dp, bottomEnd = 4.dp
                            )
                            isLast   -> RoundedCornerShape(
                                topStart = 4.dp, topEnd = 4.dp,
                                bottomStart = 16.dp, bottomEnd = 16.dp
                            )
                            else -> RoundedCornerShape(4.dp)
                        }

                        EditableSubTaskItem(
                            subTask = subTask,
                            isExpanded = expandedSubTaskId == subTask.id,
                            itemShape = shape,
                            onExpandChange = { shouldExpand ->
                                expandedSubTaskId = if (shouldExpand) subTask.id else null
                            },
                            onUpdate = { updatedSubTask ->
                                val realIndex = subTasks.indexOfFirst { it.id == updatedSubTask.id }
                                if (realIndex != -1) {
                                    subTasks[realIndex] = updatedSubTask
                                }
                            },
                            onCheckedChange = {
                                val realIndex = subTasks.indexOfFirst { it.id == subTask.id }
                                if (realIndex != -1) {
                                    subTasks[realIndex] = subTask.copy(isDone = !subTask.isDone)
                                }
                            },
                            onDelete = {
                                val realIndex = subTasks.indexOfFirst { it.id == subTask.id }
                                if (realIndex != -1) subTasks.removeAt(realIndex)
                                if (expandedSubTaskId == subTask.id) expandedSubTaskId = null
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreationSheet = false },
            sheetState = sheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            TaskCreationSheetContent(
                title = draftSubTitle,
                onTitleChange = { draftSubTitle = it },
                description = draftSubDescription,
                onDescriptionChange = { draftSubDescription = it },
                priority = draftSubPriority,
                onPriorityChange = { draftSubPriority = it },
                dueDate = draftSubDueDate,
                onDueDateChange = { draftSubDueDate = it },
                titlePlaceholder = stringResource(R.string.add_subtask_placeholder),
                reminderTime = draftSubReminder,
                onReminderTimeChange = { draftSubReminder = it },
                onSave = { title, desc, prio, date, reminder ->
                    val newSubTask = SubTask(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        description = desc,
                        isDone = false,
                        priority = prio,
                        dueDate = date,
                        position = subTasks.size,
                        reminderTime = reminder
                    )
                    subTasks.add(newSubTask)

                    draftSubTitle = ""
                    draftSubDescription = ""
                    draftSubPriority = Priority.NOTHING
                    draftSubDueDate = null
                    draftSubReminder = null
                    showCreationSheet = false
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        if (isNewTask) focusRequester.requestFocus()
    }

    // ── Category selector bottom sheet ───────────────────────────────────────
    if (showCategorySelector) {
        CategorySelectorSheet(
            categories = categories,
            selectedCategoryId = categoryId,
            onCategorySelected = onCategoryChange,
            onDismiss = { showCategorySelector = false },
            generalTabName = generalName
        )
    }
}