package com.markel.flowstate.feature.flow.checklists

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.designsystem.components.ExpressiveIconButton
import com.markel.flowstate.core.designsystem.ui.CheckListSharedKeys
import com.markel.flowstate.core.designsystem.ui.sharedDetailBounds
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.feature.flow.checklists.components.CheckListItemRow
import com.markel.flowstate.feature.flow.checklists.components.GhostItemRow
import com.markel.flowstate.feature.flow.components.COLOR_TRANSPARENT
import com.markel.flowstate.feature.flow.components.CategorySelectorSheet
import com.markel.flowstate.feature.flow.components.ColorPicker
import com.markel.flowstate.feature.flow.components.resolveIdeaColor
import com.markel.flowstate.feature.tasks.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Full screen for checklist editing/creation.
 *
 * [checkListId] = null → new checklist mode
 * [checkListId] = Int → existing checklist editing mode
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckListEditorScreen(
    checkListId: Int?,
    categoryId: Int? = null,
    onBack: () -> Unit,
    viewModel: CheckListViewModel = hiltViewModel()
) {
    BackHandler {
        viewModel.closeAndSave()
        onBack()
    }

    val editorState by viewModel.editor.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoriesEnabled by viewModel.categoriesEnabled.collectAsStateWithLifecycle()
    val generalCategoryName by viewModel.generalCategoryName.collectAsStateWithLifecycle()
    var showCategorySelector by remember { mutableStateOf(false) }

    LaunchedEffect(checkListId) {
        if (checkListId == null) viewModel.openNew(categoryId)
        else viewModel.loadForEditing(checkListId)
    }

    val resolvedColor = editorState.color.resolveIdeaColor()
    val cardColor = if (resolvedColor == COLOR_TRANSPARENT)
        MaterialTheme.colorScheme.surface
    else
        Color(resolvedColor)

    val onCardColor = MaterialTheme.colorScheme.onSurface
    var showColorSheet by remember { mutableStateOf(false) }
    // Tracks which item id should steal focus (set after addItem())
    var pendingFocusId by remember { mutableStateOf<String?>(null) }

    // Controls whether the completed section is expanded
    var completedExpanded by remember { mutableStateOf(false) }

    // Split items into pending and completed
    val pendingItems = editorState.items.filter { !it.isDone }
    val completedItems = editorState.items.filter { it.isDone }

    // Animate the chevron rotation
    val chevronRotation by animateFloatAsState(
        targetValue = if (completedExpanded) 180f else 0f,
        label = "chevron_rotation"
    )

    // LazyColumn state shared between reorderable and the list itself
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        // Offset by 1 because index 0 is the title header item
        viewModel.reorderPendingItems(from.index - 1, to.index - 1)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        modifier = Modifier.sharedDetailBounds(
            key = checkListId?.let { CheckListSharedKeys.container(it) }
                ?: "checklist_new"
        ),
        containerColor = cardColor,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cardColor,
                    navigationIconContentColor = onCardColor,
                    actionIconContentColor = onCardColor
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.closeAndSave()
                        onBack()
                    }) {
                        Icon(ImageVector.vectorResource(R.drawable.arrow_back_24px), contentDescription = "Back")
                    }
                },
                title = {},
                actions = {
                    ExpressiveIconButton(
                        onClick = { showColorSheet = true },
                        imageVector = ImageVector.vectorResource(R.drawable.palette_24px),
                        contentDescription = "Change background color",
                        containerColor = cardColor,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    if (checkListId != null) {
                        ExpressiveIconButton(
                            onClick = {
                            viewModel.deleteCheckList(checkListId)
                            onBack()
                            },
                            imageVector = ImageVector.vectorResource(R.drawable.delete_24px),
                            contentDescription = "Delete checklist",
                            containerColor = cardColor,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Title field
            item(key = "title") {
                // ── Category selector (only when categories are enabled) ──────────────
                if (categoriesEnabled) {
                    val defaultGeneralName = stringResource(R.string.category_general)
                    val generalName = generalCategoryName?.takeIf { it.isNotBlank() } ?: defaultGeneralName
                    val currentCategoryId = editorState.categoryId  // Use editorState.categoryId (the live state) so the chip updates
                    val currentCategoryName = if (currentCategoryId == null || currentCategoryId == Category.GENERAL_ID) {
                        generalName
                    } else {
                        categories.firstOrNull { it.id == currentCategoryId }?.name ?: generalName
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showCategorySelector = true }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = currentCategoryName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.arrow_drop_down_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                }
                else Spacer(modifier = Modifier.size(6.dp))
                BasicTextField(
                    value = editorState.title,
                    onValueChange = { viewModel.updateTitle(it) },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = onCardColor
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    cursorBrush = SolidColor(onCardColor),
                    decorationBox = { inner ->
                        Box {
                            if (editorState.title.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.title),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 29.sp
                                    ),
                                    color = onCardColor.copy(alpha = 0.4f)
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
            }

            items(pendingItems, key = { it.id }) { item ->
                // Pending items
                ReorderableItem(reorderableState, key = item.id) { isDragging ->
                    val requestFocus = pendingFocusId == item.id
                    CheckListItemRow(
                        text = item.text,
                        isDone = item.isDone,
                        requestFocusOnAppear = requestFocus,
                        onFocusConsumed = { if (requestFocus) pendingFocusId = null },
                        onTextChange = { viewModel.updateItemText(item.id, it) },
                        onToggle = { viewModel.toggleItem(item.id) },
                        onDelete = { viewModel.removeItem(item.id) },
                        onAddNext = {
                            val newId = viewModel.addItem()
                            pendingFocusId = newId
                        },
                        onCardColor = onCardColor,
                        scope = this
                    )
                }
            }
            item(key = "ghost") {
                // Ghost row — always visible at the bottom, tapping it creates a real item
                GhostItemRow(
                    onCardColor = onCardColor,
                    onClick = {
                        val newId = viewModel.addItem()
                        pendingFocusId = newId
                    }
                )
            }

            // ── Completed section ──────────────────────────────────────────────
            if (completedItems.isNotEmpty()) {
                item(key = "completed") {
                    Spacer(Modifier.height(8.dp))

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
                                count = completedItems.size,
                                completedItems.size
                            ),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = onCardColor.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.expand_more_40px),
                            contentDescription = if (completedExpanded) "Collapse" else "Expand",
                            tint = onCardColor.copy(alpha = 0.45f),
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation)
                        )
                    }

                    // Animated completed items list
                    AnimatedVisibility(
                        visible = completedExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            completedItems.forEach { item ->
                                CheckListItemRow(
                                    text = item.text,
                                    isDone = item.isDone,
                                    requestFocusOnAppear = false,
                                    onFocusConsumed = {},
                                    onTextChange = { viewModel.updateItemText(item.id, it) },
                                    onToggle = { viewModel.toggleItem(item.id) },
                                    onDelete = { viewModel.removeItem(item.id) },
                                    onAddNext = {
                                        val newId = viewModel.addItem()
                                        pendingFocusId = newId
                                    },
                                    onCardColor = onCardColor,
                                    scope = null
                                )
                            }
                        }
                    }
                }
            }
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(40.dp))
            }

        }
    }

    if (showColorSheet) {
        ModalBottomSheet(
            onDismissRequest = { showColorSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null,
            containerColor = cardColor,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f),
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.backg_color),
                    style = MaterialTheme.typography.titleSmall,
                    color = onCardColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                ColorPicker(
                    selectedColor = editorState.color,
                    onColorSelected = { viewModel.updateColor(it) }
                )
            }
        }
    }

    // ── Category selector bottom sheet ───────────────────────────────────────
    if (showCategorySelector) {
        CategorySelectorSheet(
            categories = categories,
            selectedCategoryId = editorState.categoryId,
            onCategorySelected = { viewModel.updateCategory(it) },
            onDismiss = { showCategorySelector = false },
            containerColor = cardColor,
            contentColor = onCardColor,
            generalTabName = generalCategoryName
        )
    }
}