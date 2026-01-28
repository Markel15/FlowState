package com.markel.flowstate.feature.tasks.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markel.flowstate.feature.tasks.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DynamicHeader(isMinimized: Boolean) {
    val greeting = when (LocalTime.now().hour) {
        in 5..12 -> R.string.good_morning
        in 13..20 -> R.string.good_evening
        else -> R.string.good_night
    }

    val dateText = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())
        .format(LocalDate.now())
        .uppercase()

    val headerHeight by animateDpAsState(
        targetValue = if (isMinimized) 40.dp else 80.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "height"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            // The message is only rendered if it hasn't been minimized
            androidx.compose.animation.AnimatedVisibility(
                visible = !isMinimized,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = stringResource(greeting),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isMinimized) Alignment.CenterStart else Alignment.BottomEnd
            ) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .align(if (isMinimized) Alignment.CenterStart else Alignment.BottomEnd)
                        .animateContentSize() // Smooths the alignment change
                )
            }
        }

        AnimatedVisibility(visible = isMinimized) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
    }
}