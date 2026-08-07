package com.markel.flowstate.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.feature.settings.components.SettingsGroupShapes
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.markel.flowstate.core.designsystem.R as DesignR

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val categoriesEnabled by viewModel.categoriesEnabled.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val localCategories = remember(categories) {
        mutableStateListOf<Category>().apply {
            addAll(categories)
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Category?>(null) }
    var deleteItemsPermanently by remember { mutableStateOf(false) }
    // Null = closed. General = rename the virtual General category.
    // Real(id) = rename the user category with that id.
    var showRenameDialog by remember { mutableStateOf<RenameTarget?>(null) }

    val customGeneralName by viewModel.generalCategoryName.collectAsStateWithLifecycle()
    val defaultGeneralName = stringResource(R.string.category_general)
    val generalName = customGeneralName?.takeIf { it.isNotBlank() } ?: defaultGeneralName

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val catStartOffset = 3 // switch + spacer + header
        val catEndExclusive = catStartOffset + localCategories.size

        if (from.index in catStartOffset until catEndExclusive &&
            to.index in catStartOffset until catEndExclusive
        ) {
            val fromIndex = from.index - catStartOffset
            val toIndex = to.index - catStartOffset
            localCategories.add(toIndex, localCategories.removeAt(fromIndex))
            viewModel.reorderCategories(localCategories.toList())
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.categories_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.arrow_back_24px),
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 60.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ── Enable/Disable switch ──────────────────────────────
            item(key = "switch") {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.categories_enabled),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.categories_enabled_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = categoriesEnabled,
                            onCheckedChange = { viewModel.setCategoriesEnabled(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SettingsGroupShapes.singleItemShape)
                )
            }

            item(key = "spacer") {
                Spacer(modifier = Modifier.padding(8.dp))
            }

            if (categoriesEnabled) {
                item(key = "categories_header") {
                    Text(
                        text = stringResource(R.string.categories_list),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // ── All categories ──
                items(
                    items = localCategories,
                    key = { it.id }
                ) { category ->
                    val index = localCategories.indexOf(category)
                    val isGeneral = category.id == Category.GENERAL_ID
                    ReorderableItem(reorderableState, key = category.id) { isDragging ->
                        val scale by animateFloatAsState(
                            targetValue = if (isDragging) 1.03f else 1f,
                            label = "cat_drag_scale"
                        )
                        val shape = when {
                            localCategories.size == 1 -> SettingsGroupShapes.singleItemShape
                            index == 0 -> SettingsGroupShapes.leadingItemShape
                            index == localCategories.lastIndex -> SettingsGroupShapes.endItemShape
                            else -> SettingsGroupShapes.middleItemShape
                        }

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = if (isGeneral) generalName else category.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        showRenameDialog = if (isGeneral) {
                                            RenameTarget.General
                                        } else {
                                            RenameTarget.Real(category.id, category.name)
                                        }
                                    }) {
                                        Icon(
                                            imageVector = ImageVector.vectorResource(R.drawable.edit_24px),
                                            contentDescription = stringResource(R.string.categories_rename),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // General cannot be deleted — it's the fallback category.
                                    if (!isGeneral) {
                                        IconButton(onClick = {
                                            deleteItemsPermanently = false
                                            showDeleteDialog = category
                                        }) {
                                            Icon(
                                                imageVector = ImageVector.vectorResource(DesignR.drawable.delete_24px),
                                                contentDescription = stringResource(R.string.categories_delete),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.drag_indicator_24px),
                                        contentDescription = stringResource(R.string.bottom_nav_drag_hint),
                                        modifier = Modifier.longPressDraggableHandle(
                                            interactionSource = remember { MutableInteractionSource() }
                                        ),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = if (isDragging) 0.92f else 1f
                                }
                                .zIndex(if (isDragging) 1f else 0f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        )
                    }
                }

                item(key = "add_category_spacer") {
                    Spacer(modifier = Modifier.padding(top = 16.dp))
                }

                item(key = "add_category") {
                    ListItem(
                        headlineContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(DesignR.drawable.add_24px),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                                Text(
                                    text = stringResource(R.string.categories_add),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        },
                        leadingContent = null,
                        trailingContent = null,
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SettingsGroupShapes.singleItemShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showCreateDialog = true }
                            )
                    )
                }
            }
        }
    }

    // ── Create category dialog ──────────────────────────────────
    if (showCreateDialog) {
        var categoryName by remember { mutableStateOf("") }
        val isValid = categoryName.isNotBlank()

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.categories_create)) },
            text = {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text(stringResource(R.string.categories_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isValid) {
                            viewModel.createCategory(categoryName.trim())
                            showCreateDialog = false
                        }
                    },
                    enabled = isValid
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Rename category dialog (General OR a real user category) ──
    showRenameDialog?.let { target ->
        // Initial value: custom General name (if renaming General) or the
        // category's current name (if renaming a real one).
        val initialValue = when (target) {
            is RenameTarget.General -> customGeneralName ?: ""
            is RenameTarget.Real -> target.currentName
        }
        var newName by remember(target) { mutableStateOf(initialValue) }
        val isValid = newName.isNotBlank()

        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = {
                Text(
                    text = when (target) {
                        is RenameTarget.General -> stringResource(R.string.categories_rename_general_title)
                        is RenameTarget.Real -> stringResource(R.string.categories_rename_title)
                    }
                )
            },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.categories_name_label)) },
                    singleLine = true,
                    isError = !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isValid) {
                            when (target) {
                                is RenameTarget.General ->
                                    viewModel.renameGeneralCategory(newName)
                                is RenameTarget.Real ->
                                    viewModel.renameCategory(target.id, newName)
                            }
                            showRenameDialog = null
                        }
                    },
                    enabled = isValid
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "Reset to default" only makes sense for General (whose
                    // default is the localized string). Real categories have no
                    // default name to reset to.
                    if (target is RenameTarget.General && customGeneralName != null) {
                        TextButton(
                            onClick = {
                                viewModel.renameGeneralCategory(null)
                                showRenameDialog = null
                            }
                        ) {
                            Text(stringResource(R.string.categories_reset_general))
                        }
                    }
                    TextButton(onClick = { showRenameDialog = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    // ── Delete category confirmation dialog ─────────────────────
    showDeleteDialog?.let { category ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.categories_delete_title, category.name)) },
            text = { Text(stringResource(R.string.categories_delete_message)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.deleteCategory(category.id, deleteItems = true)
                            showDeleteDialog = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.categories_delete_permanently))
                    }
                    TextButton(
                        onClick = {
                            viewModel.deleteCategory(category.id, deleteItems = false)
                            showDeleteDialog = null
                        }
                    ) {
                        Text(stringResource(R.string.categories_move_to_general))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Identifies which category is being renamed in the rename dialog.
 * - [General] → the General category (id = [Category.GENERAL_ID]), whose
 *   custom display name lives in DataStore (the DB row's name is always "General").
 * - [Real]    → a real user category row, whose name lives in the DB.
 */
private sealed interface RenameTarget {
    data object General : RenameTarget
    data class Real(val id: Int, val currentName: String) : RenameTarget
}