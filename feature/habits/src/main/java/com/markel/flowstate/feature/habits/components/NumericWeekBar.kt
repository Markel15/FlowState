package com.markel.flowstate.feature.habits.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun NumericWeekBar(
    value: Float?,
    targetValue: Float?,
    scaleReference: Float,
    color: Color,
    date: LocalDate,
    isToday: Boolean,
    isFuture: Boolean,
    isScheduled: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayOfWeek = date.dayOfWeek

    // Calculate bar height
    val fillRatio = when {
        value == null || value == 0f -> 0f
        targetValue == null -> {
            // Without goal: scale by the week max.
            minOf(value / scaleReference, 1f)
        }
        else -> {
            // With goal: scale by goal or max.
            minOf(value / scaleReference, 1f)
        }
    }

    val animatedFill by animateFloatAsState(
        targetValue = fillRatio,
        animationSpec = spring(stiffness = 300f),
        label = "bar_fill"
    )

    val barColor by animateColorAsState(
        targetValue = when {
            isFuture -> MaterialTheme.colorScheme.surfaceContainerHigh
            !isScheduled && value != null && value > 0f -> color.copy(alpha = 0.2f)
            !isScheduled -> MaterialTheme.colorScheme.surfaceContainerHigh
            value == null || value == 0f -> MaterialTheme.colorScheme.surfaceContainerHigh
            targetValue == null && value > 0 -> color
            targetValue != null && value >= targetValue -> color
            else -> color.copy(alpha = 0.4f)
        },
        label = "bar_color"
    )

    // Minimum and maximum height
    val maxHeight = 80.dp
    val minVisibleHeight = 15.dp

    val barHeight = if (animatedFill > 0f) {
        androidx.compose.ui.unit.max(minVisibleHeight, maxHeight * animatedFill)
    } else {
        minVisibleHeight
    }

    Column(
        modifier = modifier.clickable(
            enabled = !isFuture && isScheduled,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(barColor)
                    .then(
                        if (isSelected && !isFuture && isScheduled) {
                            Modifier.border(
                                width = 2.dp,
                                color = color,
                                shape = RoundedCornerShape(
                                    topStart = 8.dp,
                                    topEnd = 8.dp,
                                    bottomStart = 4.dp,
                                    bottomEnd = 4.dp
                                )
                            )
                        } else {
                            Modifier
                        }
                    )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Day label
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, LocalLocale.current.platformLocale).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                color = when {
                    isSelected && !isFuture && isScheduled -> color
                    isFuture || !isScheduled ->
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 11.sp
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                color = when {
                    !isScheduled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    isToday -> color
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}