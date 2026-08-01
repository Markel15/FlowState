package com.markel.flowstate.feature.habits.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.markel.flowstate.core.domain.HabitType
import com.markel.flowstate.feature.habits.R
import com.markel.flowstate.feature.habits.util.formatFloat
import com.markel.flowstate.core.designsystem.R as DesignR

private val habitColors = listOf(
    Color(0xFF6650A4), Color(0xFF0061A4), Color(0xFF006E1C),
    Color(0xFFB3261E), Color(0xFFE8710A), Color(0xFF006A6A),
    Color(0xFF6B5778), Color(0xFF386666)
)

/**
 * Upsert bottom sheet:
 *  - Pill-shaped icon container + big emphasized title header.
 *  - The habit TYPE was already chosen at the FAB menu, so the sheet
 *    only shows a small non-interactive context chip (no type selector).
 *  - Icon picker: selected icon pops into a Cookie shape with a spring.
 *  - Color picker: selected dot bounces and shows a springy check.
 *  - Big connected action buttons with the M3 Expressive press-morph shapes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddHabitSheet(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        icon: String,
        colorArgb: Int,
        habitType: HabitType,
        unit: String?,
        targetValue: Float?,
        step: Float
    ) -> Unit,
    initialName: String = "",
    initialIcon: String = "none",
    initialColor: Color? = null,
    initialHabitType: HabitType = HabitType.BOOLEAN,
    initialUnit: String? = null,
    initialTargetValue: Float? = null,
    initialStep: Float = 1f
) {
    val isEditMode = initialName.isNotEmpty() || initialColor != null
    var name by remember { mutableStateOf(initialName) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }
    var selectedColor by remember {
        mutableStateOf(initialColor?.let { ic -> habitColors.firstOrNull { it == ic } } ?: habitColors.first())
    }
    val habitType = initialHabitType
    var unit by remember { mutableStateOf(initialUnit ?: "") }
    var targetValueText by remember { mutableStateOf(initialTargetValue?.let { formatFloat(it) } ?: "") }
    var stepText by remember { mutableStateOf(formatFloat(initialStep)) }

    val parsedTarget = targetValueText.toFloatOrNull()
    val parsedStep = stepText.toFloatOrNull()
    val isTargetInvalid = habitType == HabitType.NUMERIC && targetValueText.isNotBlank() && (parsedTarget == null || parsedTarget <= 0f)
    val isStepInvalid = habitType == HabitType.NUMERIC && stepText.isNotBlank() && (parsedStep == null || parsedStep <= 0f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header: pill icon + title at the same height ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialShapes.Pill.toShape()
                        )
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(
                            if (isEditMode) DesignR.drawable.edit_24px
                            else DesignR.drawable.add_24px
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = if (isEditMode) {
                        stringResource(R.string.edit_habit_dialog_title)
                    } else {
                        stringResource(
                            R.string.add_habit_typed_title,
                            stringResource(
                                if (habitType == HabitType.NUMERIC) R.string.habit_type_numeric
                                else R.string.habit_type_boolean
                            )
                        )
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            // ── Name ─────────────────────────────────────────────────────
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.add_habit_name_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Numeric-only fields ──────────────────────────────────────
            if (habitType == HabitType.NUMERIC) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(stringResource(R.string.habit_unit_label)) },
                        placeholder = {
                            Text(
                                text = " h, km, kg...",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("") } // keep padding even between fields
                    )
                    OutlinedTextField(
                        value = targetValueText,
                        onValueChange = { targetValueText = it },
                        label = {
                            Text("${stringResource(R.string.habit_target_label)} (${stringResource(R.string.habit_target_optional)})")
                        },
                        placeholder = {
                            Text(
                                text = "2",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                        isError = isTargetInvalid,
                        supportingText = {
                            when {
                                isTargetInvalid -> Text(text = stringResource(R.string.habit_target_error))
                                unit.isNotBlank() && targetValueText.isNotBlank() ->
                                    Text(stringResource(R.string.habit_target_preview, targetValueText, unit))
                            }
                        }
                    )
                    OutlinedTextField(
                        value = stepText,
                        onValueChange = { stepText = it },
                        label = { Text(stringResource(R.string.habit_step_label)) },
                        placeholder = { Text("1, 0.5 ...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                        isError = isStepInvalid,
                        supportingText = {
                            if (isStepInvalid) Text(text = stringResource(R.string.habit_step_error))
                            else Text(stringResource(R.string.habit_step_explanation))
                        }
                    )
                }
            }

            // ── Icon picker ──────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.icon), style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(HabitIconList) { (iconName, vector) ->
                        IconChoice(
                            iconName = iconName,
                            vector = vector,
                            selected = iconName == selectedIcon,
                            onClick = { selectedIcon = iconName }
                        )
                    }
                }
            }

            // ── Color picker ─────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.add_habit_color_label),
                    style = MaterialTheme.typography.labelLarge
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(84.dp)
                ) {
                    items(habitColors) { color ->
                        ColorDot(
                            color = color,
                            selected = color == selectedColor,
                            onClick = { selectedColor = color }
                        )
                    }
                }
            }

            // ── Actions (M3 Expressive press morph) ─
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.add_habit_cancel_button))
                }
                Button(
                    onClick = {
                        val target = targetValueText.toFloatOrNull()
                        val step = stepText.toFloatOrNull() ?: 1f
                        onConfirm(
                            name,
                            selectedIcon,
                            selectedColor.toArgb(),
                            habitType,
                            if (habitType == HabitType.NUMERIC && unit.isNotBlank()) unit else null,
                            if (habitType == HabitType.NUMERIC) target else null,
                            if (habitType == HabitType.NUMERIC && stepText.isNotBlank()) step else 1f
                        )
                        onDismiss()
                    },
                    enabled = name.isNotBlank() && !isTargetInvalid && !isStepInvalid,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(
                            if (isEditMode) R.string.edit_habit_save_button
                            else R.string.add_habit_create_button
                        )
                    )
                }
            }
        }
    }
}

/**
 * Simplified version for boolean habits (retrocompatibility)
 */
@Composable
fun AddHabitSheet(
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, colorArgb: Int) -> Unit,
    initialName: String = "",
    initialIcon: String = "none",
    initialColor: Color? = null
) {
    AddHabitSheet(
        onDismiss = onDismiss,
        onConfirm = { name, icon, colorArgb, _, _, _, _ -> onConfirm(name, icon, colorArgb) },
        initialName = initialName,
        initialIcon = initialIcon,
        initialColor = initialColor,
        initialHabitType = HabitType.BOOLEAN,
        initialUnit = null,
        initialTargetValue = null,
        initialStep = 1f
    )
}

/** Icon option: pops into a Cookie shape with a spring when selected. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconChoice(
    iconName: String,
    vector: Int?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "icon_choice_scale"
    )
    val shape: Shape = if (selected) MaterialShapes.Cookie7Sided.toShape() else CircleShape

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = shape
            )
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            }
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(vector ?: DesignR.drawable.block_24px),
            contentDescription = iconName,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Color option: bounces on selection and reveals a springy check icon. */
@Composable
private fun ColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "color_dot_scale"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "color_dot_check"
    )
    // Always a circle: morphing to a Cookie shape clashed visually with the dots.
    val shape = CircleShape

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.6f else 0f),
                shape = shape
            )
            .clip(shape)
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            }
    ) {
        if (checkScale > 0f) {
            Icon(
                imageVector = ImageVector.vectorResource(DesignR.drawable.check_24px),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { scaleX = checkScale; scaleY = checkScale }
            )
        }
    }
}