package com.markel.flowstate.feature.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
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

            items(achievements, key = { it.definition.id }) { progress ->
                AchievementCard(progress)
            }
        }
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
    AchievementId.NIGHT_OWL -> AchievementVisuals(
        R.drawable.owl_24px,
        R.string.achievement_night_owl_name, R.string.achievement_night_owl_desc
    )
    AchievementId.FLAWLESS_MONDAY -> AchievementVisuals(
        R.drawable.calendar_month_24px,
        R.string.achievement_flawless_monday_name, R.string.achievement_flawless_monday_desc
    )
    AchievementId.SUNDAY_FUN_DAY -> AchievementVisuals(
        R.drawable.task_alt_24px,
        R.string.achievement_sunday_fun_day_name, R.string.achievement_sunday_fun_day_desc
    )
    AchievementId.FLAWLESS_WEEK -> AchievementVisuals(
        R.drawable.view_week_24px,
        R.string.achievement_flawless_week_name, R.string.achievement_flawless_week_desc
    )
    AchievementId.COLLECTOR -> AchievementVisuals(
        R.drawable.award_star_24px,
        R.string.achievement_collector_name, R.string.achievement_collector_desc
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
    totalTiers == 1 -> if (tierReached >= 1) MaterialShapes.Cookie12Sided.toShape()
    else MaterialShapes.Cookie4Sided.toShape()

    else -> when (tierReached) {
        0 -> MaterialShapes.Cookie4Sided.toShape()
        1 -> MaterialShapes.Cookie6Sided.toShape()
        2 -> MaterialShapes.Cookie9Sided.toShape()
        else -> MaterialShapes.Cookie12Sided.toShape()
    }
}

/**
 * Expressive summary: wavy ring with the unlocked tier count, plus the
 * closest achievable next level — information that stays useful no
 * matter how often the screen is opened (no motivational filler).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroSummary(achievements: List<AchievementProgress>) {
    val unlocked = achievements.sumOf { it.tierReached }
    val total = achievements.sumOf { it.definition.tiers.size }

    // Animate the ring up from 0 when the screen opens / data changes.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val ringFraction by animateFloatAsState(
        targetValue = if (started && total > 0) unlocked.toFloat() / total else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "heroRing"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(26.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.achievements_hero_summary, unlocked, total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Closest next level: the achievement missing the fewest units.
                val nextUp = achievements
                    .filter { it.nextTier != null }
                    .minByOrNull { (it.nextTier ?: Int.MAX_VALUE) - it.current }
                if (nextUp != null) {
                    val name = stringResource(visualsFor(nextUp.definition.id).nameRes)
                    Text(
                        text = stringResource(
                            R.string.achievements_hero_next,
                            (nextUp.nextTier ?: 0) - nextUp.current,
                            name
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                CircularWavyProgressIndicator(
                    progress = { ringFraction },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    modifier = Modifier.size(84.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$unlocked",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = stringResource(R.string.achievements_hero_of, total),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AchievementCard(progress: AchievementProgress) {
    val visuals = visualsFor(progress.definition.id)
    val tier = progress.tierReached
    val totalTiers = progress.definition.tiers.size
    val cookieShape = shapeForTier(tier, totalTiers)

    // The accent mirrors the highest unlocked tier; locked stays dim.
    val accent: Color = when (tier) {
        0 -> MaterialTheme.colorScheme.onSurfaceVariant
        1 -> MaterialTheme.colorScheme.primary
        2 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val onAccent: Color = when (tier) {
        0 -> MaterialTheme.colorScheme.surface
        1 -> MaterialTheme.colorScheme.onPrimary
        2 -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onTertiary
    }
    val locked = tier == 0

    Card(
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
                    .run {
                        // Soft colored glow on the top tier.
                        if (tier == totalTiers && !locked) {
                            shadow(
                                elevation = 10.dp,
                                shape = cookieShape,
                                clip = false,
                                ambientColor = accent,
                                spotColor = accent
                            )
                        } else this
                    }
                    .background(color = accent, shape = cookieShape)
            ) {
                if (!locked) {
                    // Gloss on the top half so the cookie reads as "lit".
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.White.copy(alpha = 0.30f),
                                    0.45f to Color.Transparent
                                ),
                                shape = cookieShape
                            )
                    )
                }
                Icon(
                    imageVector = ImageVector.vectorResource(visuals.iconRes),
                    contentDescription = stringResource(visuals.nameRes),
                    tint = onAccent,
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
                        .copy(alpha = 0.65f) else accent,
                modifier = Modifier
                    .background(
                        color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = 0.10f) else accent.copy(alpha = 0.14f),
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
