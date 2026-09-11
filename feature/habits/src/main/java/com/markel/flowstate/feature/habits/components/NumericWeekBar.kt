package com.markel.flowstate.feature.habits.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * One vertical bar of the numeric habit weekly strip.
 *  - Only days with a recorded value draw a bar
 *  - Bars are pills ([CircleShape]) at 88% of the column width, centered — the
 *    column (tap target) still spans the full width.
 *  - Tonal color: full color on goal reached (or any value when there is no goal),
 *    35% below the goal, 15% for values logged on non-scheduled days.
 *  - Selection: a ringed dot (white fill + habit-color ring) sits inside the bar,
 *    6dp under its top edge; when the bar is too short to host it, the dot
 *    overflows above the tip. For a selected day with no value, a plain
 *    habit-color dot is shown on the baseline. Nothing gets displaced.
 *  - Animations follow [MaterialTheme.motionScheme].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val safeValue = value ?: 0f
    val hasValue = safeValue > 0f

    // Fill ratio relative to the goal (or the week max when there's no goal).
    val fillRatio = when {
        !hasValue -> 0f
        scaleReference <= 0f -> 0f
        else -> (safeValue / scaleReference).coerceIn(0f, 1f)
    }

    val animatedFill by animateFloatAsState(
        targetValue = fillRatio,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "bar_fill"
    )

    val barColor by animateColorAsState(
        targetValue = when {
            !hasValue || isFuture -> Color.Transparent
            !isScheduled -> color.copy(alpha = 0.15f)  // value on a non-scheduled day
            targetValue == null -> color  // no goal: any value reads as done
            safeValue >= targetValue -> color // goal reached
            else -> color.copy(alpha = 0.35f)  // below goal
        },
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec() ,
        label = "bar_color"
    )

    val maxHeight = 80.dp
    val minBarHeight = 8.dp

    // Selection dot
    val dotSize = 7.dp
    val dotTopInset = 6.dp

    val isSelectable = isScheduled && !isFuture
    val isMarkedSelected = isSelected && isSelectable

    Column(
        modifier = modifier.clickable(enabled = isSelectable, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Fixed-height track keeps every day label aligned; only the fill grows.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (hasValue) {
                val barHeight = (maxHeight * animatedFill).coerceAtLeast(minBarHeight)

                // Pill fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(barHeight)
                        .background(barColor, CircleShape)  // paints the pill without clipping children, so the selection dot can overflow the tip.
                )

                if (isMarkedSelected) {
                    // Dot inside the bar, 6dp below its top edge; if the bar is
                    // too short to host it, is placed above.
                    val dotBottomOffset =
                        if (barHeight >= dotTopInset + dotSize) {
                            barHeight - dotTopInset - dotSize
                        } else {
                            barHeight + dotTopInset
                        }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = -dotBottomOffset)
                            .size(dotSize)
                            .background(Color.White, CircleShape)
                            .border(1.5.dp, color, CircleShape)
                    )
                }
            } else if (isMarkedSelected) {
                // Selected day with no value -> dot on the baseline.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(dotSize)
                        .background(color, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Day label
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, LocalLocale.current.platformLocale).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                color = when {
                    isMarkedSelected -> color
                    isFuture || !isScheduled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 11.sp
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                color = when {
                    isFuture || !isScheduled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    isToday -> color
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}