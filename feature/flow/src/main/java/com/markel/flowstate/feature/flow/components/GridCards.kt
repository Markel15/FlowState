package com.markel.flowstate.feature.flow.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markel.flowstate.core.designsystem.ui.CheckListSharedKeys
import com.markel.flowstate.core.designsystem.ui.IdeaSharedKeys
import com.markel.flowstate.core.designsystem.ui.TaskSharedKeys
import com.markel.flowstate.core.designsystem.ui.sharedCardBounds
import com.markel.flowstate.core.domain.CheckList
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.core.domain.Priority
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.feature.flow.tasks.components.formatDate
import com.markel.flowstate.feature.flow.tasks.components.isDateOverdue
import com.markel.flowstate.feature.flow.tasks.util.asColor
import com.markel.flowstate.feature.tasks.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cap on the idea body before it is truncated with an ellipsis.
 *
 * The card sizes itself to its content, so this is what bounds the height.
 * `heightIn(max = …)` would clip the text instead, which is why it is not used.
 */
private const val IDEA_CONTENT_MAX_LINES = 8

/** Pending items rendered inside a checklist card before the "+N more" line. */
private const val CHECK_LIST_VISIBLE_ITEMS = 5

// ── Idea ──────────────────────────────────────────────────────────────────────

@Composable
fun IdeaGridCard(
    idea: Idea,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedColor = idea.color.resolveIdeaColor()
    val isTransparent = resolvedColor == COLOR_TRANSPARENT
    val cardColor = if (isTransparent) Color.Transparent else Color(resolvedColor)
    val shape = RoundedCornerShape(12.dp)

    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = modifier
            .fillMaxWidth()
            .sharedCardBounds(key = IdeaSharedKeys.container(idea.id), shape = shape)
            .then(
                if (isTransparent) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = shape
                ) else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = idea.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (idea.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = idea.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = IDEA_CONTENT_MAX_LINES,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── CheckList ─────────────────────────────────────────────────────────────────

@Composable
fun CheckListGridCard(
    checkList: CheckList,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedColor = checkList.color.resolveIdeaColor()
    val isTransparent = resolvedColor == COLOR_TRANSPARENT
    val cardColor = if (isTransparent) Color.Transparent else Color(resolvedColor)
    val shape = RoundedCornerShape(12.dp)

    Card(
        shape = shape,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = modifier
            .fillMaxWidth()
            .sharedCardBounds(
                key = CheckListSharedKeys.container(checkList.id),
                shape = shape
            )
            .then(
                if (isTransparent) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = shape
                ) else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = checkList.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            val pendingItems = checkList.items.filter { !it.isDone }
            val visibleItems = pendingItems.take(CHECK_LIST_VISIBLE_ITEMS)
            visibleItems.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 1.dp)
                ) {
                    MiniCheckbox(checked = false)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (pendingItems.size > visibleItems.size) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "+${pendingItems.size - visibleItems.size} " + stringResource(com.markel.flowstate.feature.tasks.R.string.more),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}


/**
 * A small read-only checkbox for use inside grid cards.
 * Uses Box + border + optional fill instead of the full Checkbox composable
 * so we can control the exact size (16dp) without the built-in padding.
 */
@Composable
private fun MiniCheckbox(checked: Boolean) {

    Box(
        modifier = Modifier
            .size(13.dp)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(3.dp)
            )
            .background(color = Color.Transparent, shape = RoundedCornerShape(3.dp))
    ) {
        if (checked) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                contentDescription = null,
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        }
    }
}
