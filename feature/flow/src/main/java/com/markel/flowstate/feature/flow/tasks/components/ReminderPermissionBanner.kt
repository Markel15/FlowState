package com.markel.flowstate.feature.flow.tasks.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.markel.flowstate.feature.tasks.R
import androidx.core.net.toUri

private const val BANNER_EXPAND_MS = 250
private const val BANNER_SHRINK_MS = 300
private const val BANNER_FADE_IN_MS = 200
private const val BANNER_FADE_OUT_MS = 150

/**
 * Contextual banner shown above the task list when:
 *  1. At least one task has a reminderTime set (there is something that should ring), AND
 *  2. The exact alarm permission is missing (it won't ring).
 *
 * The visibility check (hasTasksWithReminder && !canScheduleExactAlarms) is done
 * by the caller (SectionedFlowView) so this composable stays dumb.
 *
 * ── It must stay OUT of the scrolling container ──────────────────────────────
 * Animating its height inside the LazyVerticalStaggeredGrid moved the end of the
 * content on every frame of the exit, and that bound is what
 * LazyLayoutItemAnimator uses to invent a start offset for rows that were never
 * composed: they were born glued to the row above, or popped straight into their
 * final slot when the collapse happened in one frame. Pinned above the grid the
 * height belongs to the viewport, not to the content, so the grid translates
 * rigidly and the rows it reveals come in through the bottom edge.
 */
@Composable
fun ReminderPermissionBanner(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = BANNER_FADE_IN_MS)) +
            expandVertically(animationSpec = tween(durationMillis = BANNER_EXPAND_MS)),
        exit = fadeOut(animationSpec = tween(durationMillis = BANNER_FADE_OUT_MS)) +
            shrinkVertically(animationSpec = tween(durationMillis = BANNER_SHRINK_MS)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.notifications_active_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .size(22.dp)
                    .padding(top = 2.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.permission_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                "package:${context.packageName}".toUri()
                            )
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        stringResource(R.string.permission_settings),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
