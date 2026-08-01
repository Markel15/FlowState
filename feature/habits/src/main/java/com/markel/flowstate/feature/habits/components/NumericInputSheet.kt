package com.markel.flowstate.feature.habits.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import com.markel.flowstate.feature.habits.R
import com.markel.flowstate.feature.habits.util.formatFloat
import com.markel.flowstate.core.designsystem.R as DesignR
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOLD_REPEAT_DELAY_MS = 380L
private const val HOLD_REPEAT_START_MS = 170L
private const val HOLD_REPEAT_MIN_MS = 60L
private const val HOLD_REPEAT_ACCEL_MS = 22L

/**
 * Quick-entry sheet for numeric habits, redesigned around a "goal ring"
 * fully owned by the habit's own color:
 *
 *  - HERO: the day's progress vs the habit target is a large wavy ring tinted
 *    with the habit's own color. Every −/+ tap visibly fills it with a spring.
 *  - HEADER: the pill shows the habit's real icon and morphs Pill → SoftBurst
 *    once the day counts as completed (target reached, or any value > 0 when
 *    the habit has no target), exactly like the cards' check button.
 *  - CELEBRATION: crossing the target fires one tactile beat, the wave calms
 *    down (amplitude → 0), the ring pops and the subline flips to
 *    "Goal reached!". Re-opening an already-completed day does NOT celebrate.
 *  - STEPPERS: chunky 64dp buttons with press-morph corners, painted with the
 *    habit color.
 *  - EXACT VALUES stay first-class and SPEED comes first: the keyboard opens
 *    on its own ~250ms after the sheet settles, the field
 *    lives synced with the ring (typing fills it live), and tapping the big
 *    number re-opens the keyboard.
 *  - LAYOUT: no drag handle (it clipped the morphed shape) and a fixed-but-
 *    adaptive max height (~460dp) keeps the whole primary flow visible even
 *    with the keyboard open; the destructive "clear" action lives below the
 *    fold, inside the scrollable zone.
 *  - Habits without target: no ring, just the big bouncing number.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NumericInputSheet(
    habitName: String,
    iconName: String,
    unit: String?,
    step: Float = 1f,
    habitColor: Color,
    targetValue: Float? = null,
    currentValue: Float?,
    onDismiss: () -> Unit,
    onConfirm: (Float?) -> Unit
) {
    var textFieldValue by remember {
        mutableStateOf(
            run {
                val initialText = currentValue?.let { if (it == 0f) "" else formatFloat(it) } ?: ""
                TextFieldValue(
                    text = initialText,
                    selection = TextRange(initialText.length)
                )
            }
        )
    }
    val valueText = textFieldValue.text
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val numericValue = valueText.toFloatOrNull() ?: 0f
    val hasTarget = targetValue != null && targetValue > 0f
    // Same completion rule as GetHabitsWithStatusUseCase:
    //  with a target → reach it; without one → any value > 0 counts.
    val isComplete = if (hasTarget) numericValue >= targetValue!! else numericValue > 0f

    // Ring animations: spring fill + pop on completion + calm wave when done
    val ringProgress = if (hasTarget) (numericValue / targetValue!!).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = ringProgress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f),
        label = "ring_progress"
    )
    val completePop by animateFloatAsState(
        targetValue = if (isComplete) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "complete_pop"
    )

    // Number bounce on every value change (skipped on first composition)
    val numberScale = remember { Animatable(1f) }
    var previousValue by remember { mutableFloatStateOf(numericValue) }
    LaunchedEffect(numericValue) {
        if (previousValue != numericValue) {
            numberScale.snapTo(0.88f)
            numberScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
            )
        }
        previousValue = numericValue
    }

    fun stepBy(direction: Float) {
        val next = (numericValue + direction * step).coerceAtLeast(0f)
        textFieldValue = TextFieldValue(
            text = formatFloat(next),
            selection = TextRange(formatFloat(next).length)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // No drag handle: its reserved space clipped the morphed shape's spikes.
        // The top padding below provides the margin instead.
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        // Fixed-but-adaptive cap: the whole primary flow (header → ring → field
        // → actions) stays visible even with the keyboard open; anything below
        // that (the clear button lives down there) is reached via scroll.
        val sheetMaxHeight = minOf(455f, LocalConfiguration.current.screenHeightDp * 0.85f).dp

        Column(
            modifier = Modifier
                .heightIn(max = sheetMaxHeight)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header: habit icon pill + title (morphs to SoftBurst on goal) ─
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MorphingGoalIcon(
                    iconName = iconName,
                    habitColor = habitColor,
                    isComplete = isComplete
                )
                Text(
                    text = stringResource(R.string.habit_input_dialog_title, habitName),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            // ── Hero: stepper − [ ring | big number ] stepper + ──────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HoldRepeatStepButton(
                    iconRes = DesignR.drawable.remove_24px,
                    contentDescription = "Decrease",
                    containerColor = habitColor.copy(alpha = 0.18f),
                    contentColor = habitColor,
                    onStep = { stepBy(-1f) }
                )

                if (hasTarget) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.graphicsLayer {
                            scaleX = completePop
                            scaleY = completePop
                        }
                    ) {
                        CircularWavyProgressIndicator(
                            progress = { animatedProgress },
                            color = habitColor,
                            trackColor = habitColor.copy(alpha = 0.18f),
                            stroke = Stroke(
                                width = 12f,
                                cap = StrokeCap.Round
                            ),
                            trackStroke = Stroke(
                                width = 12f,
                                cap = StrokeCap.Round
                            ),
                            // Once the goal is met the wave settles into a calm full ring
                            amplitude = { if (isComplete) 0f else 1f },
                            modifier = Modifier.size(145.dp)
                        )
                        HeroValue(
                            valueText = valueText,
                            unit = unit,
                            targetValue = targetValue,
                            habitColor = habitColor,
                            numberScale = numberScale.value,
                            showTargetLabel = true,
                            onRequestExactEdit = {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        )
                    }
                } else {
                    HeroValue(
                        valueText = valueText,
                        unit = unit,
                        targetValue = null,
                        habitColor = habitColor,
                        numberScale = numberScale.value,
                        showTargetLabel = false,
                        onRequestExactEdit = {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    )
                }

                HoldRepeatStepButton(
                    iconRes = DesignR.drawable.add_24px,
                    contentDescription = "Increase",
                    containerColor = habitColor,
                    contentColor = Color.White,
                    onStep = { stepBy(1f) }
                )
            }

            // ── Subline: remaining count flips to a celebration ──────────
            if (hasTarget) {
                val sublineColor by animateColorAsState(
                    targetValue = if (isComplete) habitColor
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "subline_color"
                )
                AnimatedContent(
                    targetState = isComplete,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "subline_state",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { completed ->
                    Text(
                        text = if (completed) {
                            stringResource(R.string.habit_input_goal_reached)
                        } else {
                            val remaining = (targetValue!! - numericValue).coerceAtLeast(0f)
                            val remainingText = formatFloat(remaining) + (unit?.let { " $it" } ?: "")
                            stringResource(R.string.habit_input_remaining, remainingText)
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = sublineColor
                    )
                }
            }

            // ── Exact value: same source of truth, so the ring reacts live ──
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    if (newValue.text.isEmpty() || newValue.text.matches(Regex("^\\d*\\.?\\d*$"))) {
                        textFieldValue = newValue
                    }
                },
                label = {
                    Text(
                        if (unit != null) stringResource(R.string.habit_input_label_unit, unit)
                        else stringResource(R.string.habit_input_label_plain)
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = habitColor,
                    focusedLabelColor = habitColor,
                    cursorColor = habitColor
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                keyboardActions = KeyboardActions(
                    onDone = { onConfirm(valueText.toFloatOrNull()) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            // ── Actions ──────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = habitColor.copy(alpha = 0.12f),
                        contentColor = habitColor
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.add_habit_cancel_button))
                }
                Button(
                    onClick = { onConfirm(valueText.toFloatOrNull()) },
                    enabled = valueText.isNotBlank() && valueText.toFloatOrNull() != null,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = habitColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.habit_input_confirm))
                }
            }

            // ── Clear
            if (currentValue != null && currentValue > 0f) {
                FilledTonalButton(
                    onClick = { onConfirm(null) },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.habit_clean_amount))
                }
            }
        }
    }
}

/** Big animated value, scaled/bounced by the parent.
 *  Tapping it requests focus on the exact-value field below. */
@Composable
private fun HeroValue(
    valueText: String,
    unit: String?,
    targetValue: Float?,
    habitColor: Color,
    numberScale: Float,
    showTargetLabel: Boolean,
    onRequestExactEdit: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = numberScale
                scaleY = numberScale
            }
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onRequestExactEdit)
            .padding(horizontal = 8.dp)
    ) {
        AnimatedContent(
            targetState = valueText.toFloatOrNull()?.let { formatFloat(it) } ?: "0",
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "numeric_value"
        ) { displayed ->
            Text(
                text = displayed,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = habitColor,
                letterSpacing = (-2.0).sp,
            )
        }
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showTargetLabel && targetValue != null) {
            Text(
                text = stringResource(R.string.habit_input_of_target, formatFloat(targetValue)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Chunky stepper button with:
 *  - press-morph corners (circle → rounded square) + scale dip while pressed,
 *  - single step on tap,
 *  - hold-to-repeat with acceleration (so large values don't need 75 taps).
 */
@Composable
private fun HoldRepeatStepButton(
    iconRes: Int,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnStep by rememberUpdatedState(onStep)

    val cornerPercent by animateIntAsState(
        targetValue = if (pressed) 30 else 50,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "step_corner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "step_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(cornerPercent))
            .background(containerColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val repeatJob = scope.launch {
                            delay(HOLD_REPEAT_DELAY_MS)
                            var interval = HOLD_REPEAT_START_MS
                            while (true) {
                                currentOnStep()
                                delay(interval)
                                if (interval > HOLD_REPEAT_MIN_MS) interval -= HOLD_REPEAT_ACCEL_MS
                            }
                        }
                        tryAwaitRelease()
                        pressed = false
                        repeatJob.cancel()
                    },
                    onTap = { currentOnStep() }
                )
            }
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(iconRes),
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(30.dp)
        )
    }
}

/**
 * Header icon, owned by the habit: always painted with [habitColor] and,
 * when the goal is met, morphs Pill → SoftBurst with the same motion recipe
 * as the cards' MorphingCheckButton (600ms morph + springy rotation/scale).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingGoalIcon(
    iconName: String,
    habitColor: Color,
    isComplete: Boolean,
    modifier: Modifier = Modifier
) {
    val morphProgress by animateFloatAsState(
        targetValue = if (isComplete) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "goal_pill_morph"
    )
    val rotation by animateFloatAsState(
        targetValue = if (isComplete) 135f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 150f),
        label = "goal_pill_rotation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isComplete) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "goal_pill_scale"
    )

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

    Box(
        modifier = modifier
            .size(50.dp)
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
            }
            .background(habitColor, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = getHabitIcon(iconName)
                ?: ImageVector.vectorResource(R.drawable.tag_24px),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { rotationZ = -rotation } // keep the icon upright
        )
    }
}