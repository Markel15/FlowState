package com.markel.flowstate.feature.habits.components

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.google.android.material.color.MaterialColors


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingCheckButton(
    isCompleted: Boolean,
    iconName: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Animation states
    val morphProgress by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "morph"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isCompleted) 135f else 0f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 150f
        ),
        label = "rotation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isCompleted) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "scale"
    )

    // Morphing definition
    val morph = remember { Morph(MaterialShapes.Pill, MaterialShapes.SoftBurst) }

    val shape = remember(morphProgress) {
        object : Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                density: Density
            ): Outline {
                val path = morph.toPath(morphProgress)
                val matrix = Matrix()
                matrix.scale(size.width, size.height)
                path.transform(matrix)
                return Outline.Generic(path)
            }
        }
    }

    // Final "product", the shape + (icon) should be here
    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .background(color, shape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val iconVector = getHabitIcon(iconName)
        if (iconVector != null) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        rotationZ = -rotation
                    }
            )
        }
    }
}