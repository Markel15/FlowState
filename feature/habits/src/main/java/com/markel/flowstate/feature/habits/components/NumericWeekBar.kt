package com.markel.flowstate.feature.habits.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
 *  - The pill is always composed: clearing a value shrinks it to zero and fades it out
 *  - Selection: a single ringed dot (white fill + habit-color ring). It sits
 *    inside the bar, 6dp under its top edge, blending smoothly to floating 6dp
 *    above the tip when the bar is too short to host it; on a selected day with
 *    no value it rests on the baseline as a plain habit-color dot.
 *  - The dot never pops in or out: it enters with a springy overshoot, exits
 *    with a quick shrink+fade, glides between baseline and bar tip as values are
 *    logged or cleared, and cross-fades between its two styles.
 *  - Animations follow [MaterialTheme.motionScheme], with two deliberate
 *    exceptions: the dot entrance uses a bouncier spring, and the dot exit is a fast
 *    tween. Color always animates with effects specs
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
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "bar_color"
    )

    val maxHeight = 80.dp
    val minBarHeight = 8.dp

    // Selection dot geometry
    val dotSize = 7.dp
    val dotTopInset = 6.dp

    val isSelectable = isScheduled && !isFuture
    val isMarkedSelected = isSelected && isSelectable

    // --- Dot visibility -----------------------------------------------------
    val dotScale by animateFloatAsState(
        targetValue = if (isMarkedSelected) 1f else 0f,
        animationSpec = if (isMarkedSelected) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        } else {
            tween(durationMillis = 150, easing = FastOutLinearInEasing)
        },
        label = "dot_scale"
    )

    // --- Dot position -------------------------------------------------------
    val barHeight = (maxHeight * animatedFill).coerceAtLeast(if (hasValue) minBarHeight else 0.dp)

    // The dot stays above the bar until the bar is tall enough to contain:
    // 6dp bottom margin + 7dp dot + 6dp top margin.
    val dotFitThreshold = dotTopInset + dotSize + dotTopInset

    val targetDotOffset = if (barHeight < dotFitThreshold) {
        // Dot sits 6dp above the bar.
        barHeight + dotTopInset
    } else {
        // Dot sits inside the bar, with 6dp above it and 6dp below it.
        barHeight - dotTopInset - dotSize
    }

    val dotBottomOffset by animateDpAsState(
        targetValue = if (hasValue) targetDotOffset else 0.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "dot_offset"
    )

    // --- Dot look -----------------------------------------------------------
    // White core + habit-color ring on valued days, plain habit-color dot on
    // empty ones; the two styles cross-fade .
    val dotFillColor by animateColorAsState(
        targetValue = if (hasValue) Color.White else color,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "dot_fill"
    )
    val dotRingColor by animateColorAsState(
        targetValue = if (hasValue) color else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "dot_ring"
    )

    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = isSelectable,
            onClick = onClick
        ),
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
            // Pill fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(barHeight)
                    .background(barColor, CircleShape)
            )

            // Selection dot — composed while the day can ever show it.
            if (isSelectable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset {
                            // Deferred read: position updates skip recomposition.
                            IntOffset(x = 0, y = -dotBottomOffset.roundToPx())
                        }
                        .graphicsLayer {
                            // Deferred read: scale/alpha updates skip recomposition.
                            scaleX = dotScale
                            scaleY = dotScale
                            alpha = dotScale.coerceIn(0f, 1f)
                        }
                        .size(dotSize)
                        .background(dotFillColor, CircleShape)
                        .border(1.5.dp, dotRingColor, CircleShape)
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