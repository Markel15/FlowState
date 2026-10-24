package com.markel.flowstate.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markel.flowstate.core.domain.achievements.AchievementProgress
import java.text.NumberFormat

/**
 * Read-only detail sheet shared by every achievement state.
 *
 * It answers the three questions the compact badges cannot: what the
 * achievement asks for (full description), how far the user actually is (the
 * raw value with its own unit) and how each level was crossed (the tier
 * ladder). Achievements are derived data rebuilt from Room on every emission,
 * so there is nothing to persist or to undo in here.
 *
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AchievementDetailSheet(
    progress: AchievementProgress,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        AchievementDetailContent(progress)
    }
}

@Composable
private fun AchievementDetailContent(progress: AchievementProgress) {
    val visuals = visualsFor(progress.definition.id)
    val tier = progress.tierReached
    val totalTiers = progress.definition.tiers.size
    val locked = tier == 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DetailHeader(
            visuals = visuals,
            tier = tier,
            totalTiers = totalTiers,
            locked = locked,
            maxed = progress.maxed
        )
        Text(
            text = stringResource(visuals.descRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CountPanel(
            visuals = visuals,
            progress = progress,
            locked = locked
        )
        LevelLadder(progress = progress, visuals = visuals)
    }
}

/**
 * Cookie + name + a single state line. The cookie keeps the tier shape from the
 * grid so the sheet feels like an expansion of the badge that was tapped.
 */
@Composable
private fun DetailHeader(
    visuals: AchievementVisuals,
    tier: Int,
    totalTiers: Int,
    locked: Boolean,
    maxed: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(58.dp)
                .background(
                    color = cookieContainerColor(locked),
                    shape = shapeForTier(tier, totalTiers)
                )
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(visuals.iconRes),
                contentDescription = null,
                tint = cookieContentColor(locked),
                modifier = Modifier.size(26.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(visuals.nameRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            StateLine(tier = tier, totalTiers = totalTiers, locked = locked, maxed = maxed)
        }
    }
}

@Composable
private fun StateLine(tier: Int, totalTiers: Int, locked: Boolean, maxed: Boolean) {
    val color = if (maxed) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (maxed) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.check_circle_24px),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = when {
                maxed -> stringResource(R.string.achievement_state_completed, tier, totalTiers)
                locked -> stringResource(R.string.achievement_state_locked)
                else -> stringResource(R.string.achievement_state_in_progress, tier, totalTiers)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * The current value in its own unit, plus what the next milestone asks for.
 * A maxed achievement leaves the right slot free on purpose: that is where the
 * unlock date lands once the tier dates are derived.
 */
@Composable
private fun CountPanel(
    visuals: AchievementVisuals,
    progress: AchievementProgress,
    locked: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = formatCount(progress.current),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = pluralStringResource(visuals.unitRes, progress.current),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val target = if (locked) progress.definition.tiers.first() else progress.nextTier
        if (target != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(
                        if (locked) R.string.achievement_detail_first_level
                        else R.string.achievement_detail_next_level
                    ).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatCount(target),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LevelLadder(progress: AchievementProgress, visuals: AchievementVisuals) {
    Column {
        Text(
            text = stringResource(R.string.achievement_detail_progress_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        progress.definition.tiers.forEachIndexed { index, target ->
            LevelRow(
                index = index,
                target = target,
                progress = progress,
                visuals = visuals,
                isLast = index == progress.definition.tiers.lastIndex
            )
        }
    }
}

/**
 * One rung: the tier shape (solid when reached, ghosted when pending), the
 * requirement in the achievement's own unit and, on the rung in progress, the
 * exact distance left. The vertical line between rungs is drawn by the node
 * column so it always ends exactly on the last rung, whatever the text wraps.
 */
@Composable
private fun LevelRow(
    index: Int,
    target: Int,
    progress: AchievementProgress,
    visuals: AchievementVisuals,
    isLast: Boolean
) {
    val reached = index < progress.tierReached
    val inProgress = index == progress.tierReached

    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (reached) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        shape = shapeForTier(index + 1, progress.definition.tiers.size)
                    )
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(visuals.iconRes),
                    contentDescription = null,
                    tint = if (reached) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(15.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp)
                        .width(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 5.dp)
        ) {
            Text(
                text = stringResource(R.string.achievement_detail_level, index + 1),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (reached) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = requirementText(visuals, target),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .width(74.dp)
                .padding(vertical = 7.dp),
            horizontalAlignment = Alignment.End
        ) {
            when {
                reached -> Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                inProgress -> {
                    Text(
                        text = stringResource(
                            R.string.achievement_progress_format,
                            progress.current,
                            target
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(
                                    (progress.current.toFloat() / target).coerceIn(0f, 1f)
                                )
                                .fillMaxHeight()
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Plural manager for the first tier, where the level number above already carries the count.
 */
@Composable
private fun requirementText(visuals: AchievementVisuals, target: Int): String {
    val unit = pluralStringResource(visuals.unitRes, target)
    return if (target == 1) unit else "${formatCount(target)} $unit"
}

/** Grouping separators for the locale (6.482 in Spanish, 6,482 in English). */
private fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value.toLong())
