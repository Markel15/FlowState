package com.markel.flowstate.feature.flow.components

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markel.flowstate.feature.tasks.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontStyle

@Composable
fun DynamicHeader(
    isMinimized: Boolean,
    today: LocalDate = LocalDate.now()
) {
    val greeting = when (LocalTime.now().hour) {
        in 5..12 -> R.string.good_morning
        in 13..20 -> R.string.good_evening
        else -> R.string.good_night
    }

    val dateText = DateTimeFormatter.ofPattern("EEEE, d MMM", LocalLocale.current.platformLocale)
        .format(today)
        .uppercase()

    val headerHeight by animateDpAsState(
        targetValue = if (isMinimized) 35.dp else 65.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "height"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight),
        ) {

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .animateContentSize(),
                verticalArrangement = Arrangement.Center
            ) {
                // The message is only rendered if it hasn't been minimized
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isMinimized,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = stringResource(greeting) + " " ,  // Added a blank space so the last word isn't cut
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            fontStyle = FontStyle.Italic,
                        )
                    )
                }
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.animateContentSize() // Smooths the alignment change
                )
            }
        }
    }
}