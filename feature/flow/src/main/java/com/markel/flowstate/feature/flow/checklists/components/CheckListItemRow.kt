package com.markel.flowstate.feature.flow.checklists.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markel.flowstate.feature.tasks.R
import sh.calvin.reorderable.ReorderableCollectionItemScope

// ── Real item row ──────────────────────────────────────────────────────────────

@Composable
fun CheckListItemRow(
    textState: TextFieldState,
    isDone: Boolean,
    requestFocusOnAppear: Boolean,
    onFocusConsumed: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onAddNext: () -> Unit,
    onCardColor: Color,
    scope: ReorderableCollectionItemScope?
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Request focus when this item is newly added
    LaunchedEffect(requestFocusOnAppear) {
        if (requestFocusOnAppear) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
            onFocusConsumed()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {

        // Drag handle — only shown for pending (reorderable) items
        if (scope != null) {
            with(scope) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.drag_handle_24px),
                    contentDescription = "Reorder item",
                    tint = onCardColor.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(36.dp)
                        .draggableHandle()
                )
            }
        } else {
            Spacer(modifier = Modifier.size(36.dp))
        }

        Checkbox(
            checked = isDone,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = onCardColor.copy(alpha = 0.75f),
                uncheckedColor = onCardColor.copy(alpha = 0.7f),
                checkmarkColor = MaterialTheme.colorScheme.surface
            )
        )

        BasicTextField(
            state = textState,
            textStyle = TextStyle(
                fontSize = 17.sp,
                color = if (isDone) onCardColor.copy(alpha = 0.45f) else onCardColor,
            ),
            cursorBrush = SolidColor(onCardColor),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                capitalization = KeyboardCapitalization.Sentences
            ),
            onKeyboardAction = { onAddNext() },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .focusRequester(focusRequester)
                .onFocusChanged{ isFocused = it.isFocused }
        )

        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isFocused,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.close_24px),
                        contentDescription = "Delete item",
                        tint = onCardColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}