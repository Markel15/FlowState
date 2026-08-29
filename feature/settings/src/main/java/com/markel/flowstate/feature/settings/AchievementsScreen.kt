package com.markel.flowstate.feature.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.designsystem.R as DesignR
import com.markel.flowstate.core.domain.achievements.AchievementId
import com.markel.flowstate.core.domain.achievements.AchievementProgress

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // To avoid big gaps of surface at the top & bottom (same as the
        // main tabs): the grid scrolls behind the gesture navigation bar.
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.achievements_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.arrow_back_24px),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Hero summary card ─────────────────────────────
            if (achievements.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HeroSummary(achievements)
                }
            }

            // The list arrives pre-sorted as a level ladder from the ViewModel
            val locked = achievements.filter { it.tierReached == 0 }
            val inProgress = achievements.filter { !it.maxed && it.tierReached > 0 }
            val completed = achievements.filter { it.maxed }

            if (locked.isNotEmpty()) {
                item(key = "header_locked", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        text = stringResource(R.string.achievements_section_locked),
                        count = locked.size,
                        modifier = Modifier.animateItem()
                    )
                }
                items(locked, key = { it.definition.id }) { progress ->
                    AchievementCard(progress, modifier = Modifier.animateItem())
                }
            }

            if (inProgress.isNotEmpty()) {
                item(key = "header_in_progress", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        text = stringResource(R.string.achievements_section_in_progress),
                        count = inProgress.size,
                        modifier = Modifier.animateItem()
                    )
                }
                items(inProgress, key = { it.definition.id }) { progress ->
                    AchievementCard(progress, modifier = Modifier.animateItem())
                }
            }

            if (completed.isNotEmpty()) {
                item(key = "header_completed", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        text = stringResource(R.string.achievements_section_completed),
                        count = completed.size,
                        modifier = Modifier.animateItem()
                    )
                }
                items(completed, key = { it.definition.id }) { progress ->
                    AchievementCard(progress, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

/**
 * Small label + count that separates one tier band from the next, adding hierarchy anchors
 */
@Composable
private fun SectionHeader(text: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                    shape = CircleShape
                )
                .padding(horizontal = 8.dp, vertical = 1.dp)
        )
    }
}

/** Visual assets per achievement: the icon and its strings. */
private data class AchievementVisuals(val iconRes: Int, val nameRes: Int, val descRes: Int)

private fun visualsFor(id: AchievementId): AchievementVisuals = when (id) {
    AchievementId.PERFECT_DAY -> AchievementVisuals(
        R.drawable.star_shine_24px,
        R.string.achievement_perfect_day_name, R.string.achievement_perfect_day_desc
    )
    AchievementId.STREAK -> AchievementVisuals(
        R.drawable.mode_heat_24px,
        R.string.achievement_streak_name, R.string.achievement_streak_desc
    )
    AchievementId.UNSTOPPABLE -> AchievementVisuals(
        R.drawable.check_circle_24px,
        R.string.achievement_unstoppable_name, R.string.achievement_unstoppable_desc
    )
    AchievementId.TASK_TOTAL -> AchievementVisuals(
        R.drawable.check_box_24px,
        R.string.achievement_task_total_name, R.string.achievement_task_total_desc
    )
    AchievementId.NIGHT_OWL -> AchievementVisuals(
        R.drawable.owl_24px,
        R.string.achievement_night_owl_name, R.string.achievement_night_owl_desc
    )
    AchievementId.EARLY_BIRD -> AchievementVisuals(
        R.drawable.wb_twilight_24px,
        R.string.achievement_early_bird_name, R.string.achievement_early_bird_desc
    )
    AchievementId.FLAWLESS_MONDAY -> AchievementVisuals(
        R.drawable.calendar_month_24px,
        R.string.achievement_flawless_monday_name, R.string.achievement_flawless_monday_desc
    )
    AchievementId.SUNDAY_FUN_DAY -> AchievementVisuals(
        R.drawable.task_alt_24px,
        R.string.achievement_sunday_fun_day_name, R.string.achievement_sunday_fun_day_desc
    )
    AchievementId.PRODUCTIVE_MORNING -> AchievementVisuals(
        R.drawable.wb_sunny_24px,
        R.string.achievement_productive_morning_name,
        R.string.achievement_productive_morning_desc
    )
    AchievementId.ON_TIME -> AchievementVisuals(
        R.drawable.event_available_24px,
        R.string.achievement_on_time_name, R.string.achievement_on_time_desc
    )
    AchievementId.FLAWLESS_WEEK -> AchievementVisuals(
        R.drawable.view_week_24px,
        R.string.achievement_flawless_week_name, R.string.achievement_flawless_week_desc
    )
    AchievementId.COMEBACK -> AchievementVisuals(
        R.drawable.restart_alt_24px,
        R.string.achievement_comeback_name, R.string.achievement_comeback_desc
    )
}

/**
 * Shared visual language: the cookie gains sides as the level grows.
 * Locked -> Cookie4 (dimmed), tier1 -> Cookie6, tier2 -> Cookie9,
 * tier3 -> Cookie12. Single-tier achievements jump 4 -> 12.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun shapeForTier(tierReached: Int, totalTiers: Int): Shape = when {
    totalTiers == 1 -> if (tierReached >= 1) MaterialShapes.SoftBurst.toShape()
    else MaterialShapes.Cookie4Sided.toShape()

    else -> when (tierReached) {
        0 -> MaterialShapes.Square.toShape()
        1 -> MaterialShapes.Cookie4Sided.toShape()
        2 -> MaterialShapes.Cookie9Sided.toShape()
        else -> MaterialShapes.SoftBurst.toShape()
    }
}

/**
 * Summary: ring with the unlocked tier count, plus the closest achievable next level
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
/**
 * Hero: A giant cookie echoing the collection's overall level
 * sits in the background (cropped by the card edges); the content is just
 * the count of unlocked levels with a count-up and a thin gradient bar.
 * No colored container: same surface as the grid cards.
 *
 * The background cookie follows the same shape ladder as the cards,
 * driven by the overall collection level 0..3 — Square → Cookie4 →
 * Cookie9 → SoftBurst. When the collection is complete the shape already
 * becomes the "achieved" one.
 */
private fun HeroSummary(achievements: List<AchievementProgress>) {
    val unlocked = achievements.sumOf { it.tierReached }
    val total = achievements.sumOf { it.definition.tiers.size }
    val fraction = if (total > 0) unlocked.toFloat() / total else 0f

    // Collection complete: minimal celebration — the label switches and
    // the background cookie brightens.
    val complete = unlocked >= total

    // Count-up + bar fill when the screen opens / data changes.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animatedCount by animateIntAsState(
        targetValue = if (started) unlocked else 0,
        animationSpec = tween(durationMillis = 800),
        label = "heroCount"
    )
    val barFraction by animateFloatAsState(
        targetValue = if (started) fraction else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "heroBar"
    )

    // Overall collection level
    val globalTier = if (total > 0) (unlocked * 3) / total else 0
    val heroShape = shapeForTier(globalTier, totalTiers = 3)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(26.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Giant cookie decoration, cropped by the card edges.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 30.dp, y = 28.dp)
                    .size(170.dp)
                    .graphicsLayer { alpha = if (complete) 0.14f else 0.07f }
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = heroShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = stringResource(
                        if (complete) R.string.achievements_hero_complete
                        else R.string.achievements_hero_label
                    ).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = if (complete) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$animatedCount",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = stringResource(R.string.achievements_hero_of, total),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            shape = CircleShape
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction)
                            .fillMaxHeight()
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AchievementCard(progress: AchievementProgress, modifier: Modifier = Modifier) {
    val visuals = visualsFor(progress.definition.id)
    val tier = progress.tierReached
    val totalTiers = progress.definition.tiers.size
    val cookieShape = shapeForTier(tier, totalTiers)

    // Level semantics: a single "primary" hue that fills up as the tier
    // grows — level 3 (max) is always the solid one, so the rank reads
    // at a glance with any dynamic color scheme.
    val locked = tier == 0
    val maxed = progress.maxed
    val cookieColor: Color = when {
        locked -> MaterialTheme.colorScheme.onSurfaceVariant
        maxed -> MaterialTheme.colorScheme.primary
        tier == 1 -> MaterialTheme.colorScheme.primaryContainer
        else -> lerp(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            0.37f
        )
    }
    val onCookie: Color = when {
        locked -> MaterialTheme.colorScheme.surface
        maxed -> MaterialTheme.colorScheme.onPrimary
        // Neutral on the lighter fills so the grid doesn't drown in
        // primary: near-black in light theme, near-white in dark.
        else -> MaterialTheme.colorScheme.onSurface
    }
    val accent: Color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.primary
    val onAccent: Color = if (locked) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.onPrimary

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        val s = if (locked) 0.9f else 1f
                        scaleX = s
                        scaleY = s
                        alpha = if (locked) 0.55f else 1f
                    }
                    .background(color = cookieColor, shape = cookieShape)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(visuals.iconRes),
                    contentDescription = stringResource(visuals.nameRes),
                    tint = onCookie,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = stringResource(visuals.nameRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant
                else Color.Unspecified
            )
            Text(
                text = stringResource(visuals.descRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
                    .copy(alpha = if (locked) 0.55f else 0.8f),
                textAlign = TextAlign.Center,
                minLines = 2
            )

            Text(
                text = stringResource(R.string.achievement_level_chip, tier, totalTiers),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = 0.65f) else onCookie,
                modifier = Modifier
                    .background(
                        color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = 0.10f) else cookieColor,
                        shape = CircleShape
                    )
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            )

            TierDots(
                tiers = totalTiers,
                reached = tier,
                unlockedColor = accent,
                modifier = Modifier.height(8.dp)
            )

            // Fixed-height bottom zone: progress or the "maxed" pill, so
            // every card in the grid measures exactly the same height.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (progress.maxed) {
                    Surface(
                        shape = CircleShape,
                        color = accent,
                        contentColor = onAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "★ " + stringResource(R.string.achievement_max_level),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                } else {
                    val target = progress.nextTier ?: return@Column
                    Text(
                        text = stringResource(
                            R.string.achievement_progress_format,
                            minOf(progress.current, target),
                            target
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.2.sp
                    )
                    // Straight bar with a spring — it "grows" to its value
                    // when the screen opens or the data advances.
                    var started by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { started = true }
                    val fraction by animateFloatAsState(
                        targetValue = if (started)
                            (progress.current.toFloat() / target).coerceIn(0f, 1f)
                        else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "cardProgress"
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = 0.4f) else accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TierDots(
    tiers: Int,
    reached: Int,
    unlockedColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(tiers) { index ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = if (index < reached) unlockedColor
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
            )
        }
    }
}
