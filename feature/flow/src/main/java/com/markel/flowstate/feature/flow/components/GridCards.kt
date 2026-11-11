package com.markel.flowstate.feature.flow.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markel.flowstate.core.data.FlowCardHeight
import com.markel.flowstate.core.designsystem.components.FillHeightText
import com.markel.flowstate.core.designsystem.ui.CheckListSharedKeys
import com.markel.flowstate.core.designsystem.ui.IdeaSharedKeys
import com.markel.flowstate.core.designsystem.ui.sharedCardBounds
import com.markel.flowstate.core.domain.CheckList
import com.markel.flowstate.core.domain.Idea
import com.markel.flowstate.feature.tasks.R

/**
 * Fixed height shared by **every** card of the grid (ideas and checklists) when
 * [FlowCardHeight.UNIFORM] is selected, so a row of them lines up instead of
 * producing a ragged masonry of 60dp and 160dp cards. Ignored in
 * [FlowCardHeight.CONTENT].
 *
 * Inside that height the space is distributed by content:
 *  - idea with a body → title capped at [IDEA_TITLE_MAX_LINES], body fills the
 *    rest of the card ([FillHeightText]);
 *  - idea without a body → the title itself fills the whole card, so a long
 *    title is no longer cut at 2 lines with half the card empty.
 */
private const val FLOW_CARD_HEIGHT_DP = 146

/**
 * Title lines allowed when the idea also has a body.
 *
 * Without a body the title is not capped: it fills the card and ellipsizes on
 * the last line that fits.
 */
private const val IDEA_TITLE_MAX_LINES = 3

/**
 * Hard cap for [FlowCardHeight.CONTENT] mode only.
 *
 * There the card grows with its text, so this is not a layout budget but a
 * guard: without it a 40-line idea would own the whole scroll.
 */
private const val IDEA_CONTENT_MAX_LINES = 8

/** Pending items rendered inside a checklist card before the "+N more" line. */
private const val CHECK_LIST_VISIBLE_ITEMS = 5

/**
 * Same, but for [FlowCardHeight.UNIFORM], where the height is fixed and the items
 * have to fit inside it.
 *
*/
private const val CHECK_LIST_VISIBLE_ITEMS_UNIFORM = 4

// ── Idea ──────────────────────────────────────────────────────────────────────

@Composable
fun IdeaGridCard(
    idea: Idea,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardHeight: FlowCardHeight = FlowCardHeight.UNIFORM,
) {
    val resolvedColor = idea.color.resolveIdeaColor()
    val isTransparent = resolvedColor == COLOR_TRANSPARENT
    val cardColor = if (isTransparent) Color.Transparent else Color(resolvedColor)
    val shape = RoundedCornerShape(12.dp)
    val isUniform = cardHeight == FlowCardHeight.UNIFORM

    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = modifier
            .fillMaxWidth()
            .then(if (isUniform) Modifier.height(FLOW_CARD_HEIGHT_DP.dp) else Modifier)
            .sharedCardBounds(key = IdeaSharedKeys.container(idea.id), shape = shape)
            .then(
                if (isTransparent) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = shape
                ) else Modifier
            )
    ) {
        val titleStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        val bodyStyle = MaterialTheme.typography.bodySmall
        val hasBody = idea.content.isNotBlank()

        Column(
            modifier = Modifier
                .then(if (isUniform) Modifier.fillMaxHeight() else Modifier)
                .padding(12.dp)
        ) {
            when {
                // ── UNIFORM · title + body ────────────────────────────────
                isUniform && hasBody -> {
                    Text(
                        text = idea.title,
                        style = titleStyle,
                        maxLines = IDEA_TITLE_MAX_LINES,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        FillHeightText(
                            text = idea.content,
                            style = bodyStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── UNIFORM · title only ─────────────────────────────────
                isUniform -> FillHeightText(
                    text = idea.title,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // ── ADAPTABLE ──────────────────────────────────────────────
                else -> {
                    if (hasBody) {
                        Text(
                            text = idea.title,
                            style = titleStyle,
                            maxLines = IDEA_TITLE_MAX_LINES,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = idea.content,
                            style = bodyStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = IDEA_CONTENT_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = idea.title,
                            style = titleStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = IDEA_CONTENT_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ── CheckList ─────────────────────────────────────────────────────────────────

@Composable
fun CheckListGridCard(
    checkList: CheckList,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardHeight: FlowCardHeight = FlowCardHeight.UNIFORM,
) {
    val resolvedColor = checkList.color.resolveIdeaColor()
    val isTransparent = resolvedColor == COLOR_TRANSPARENT
    val cardColor = if (isTransparent) Color.Transparent else Color(resolvedColor)
    val shape = RoundedCornerShape(12.dp)
    val isUniform = cardHeight == FlowCardHeight.UNIFORM

    Card(
        shape = shape,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = modifier
            .fillMaxWidth()
            .then(if (isUniform) Modifier.height(FLOW_CARD_HEIGHT_DP.dp) else Modifier)
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
            val visibleItems = pendingItems.take(
                if (isUniform) CHECK_LIST_VISIBLE_ITEMS_UNIFORM else CHECK_LIST_VISIBLE_ITEMS
            )
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
