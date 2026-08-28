package com.markel.flowstate.core.domain.achievements

/**
 * Static catalog and pure evaluation logic for the achievements section.
 *
 * Every achievement is DERIVED from Room data (habit entries, streaks,
 * task timestamps): nothing lives in preferences, so progress survives
 * device migrations through the regular JSON backup. Achievements tied to
 * volatile data (e.g. task timestamps) reflect the current state — if the
 * underlying entries are removed, the milestone becomes pending again,
 * which matches the meaning of deleting that data.
 *
 * Definitions live in code (not in the database). UI-specific resources
 * (icons, shapes, strings) are mapped from [AchievementId] in the
 * presentation layer, keeping this module pure.
 */

enum class AchievementId {
    PERFECT_DAY,
    STREAK,
    UNSTOPPABLE,
    NIGHT_OWL,
    FLAWLESS_MONDAY,
    SUNDAY_FUN_DAY,
    FLAWLESS_WEEK,
    COLLECTOR
}

data class AchievementDefinition(
    val id: AchievementId,
    val tiers: List<Int>
)

/** Aggregated raw values the catalog is evaluated against. */
data class AchievementInputs(
    val totalHabitCompletions: Int = 0,
    val bestStreak: Int = 0,
    val perfectDays: Int = 0,
    val mondayPerfectDays: Int = 0,
    val nightOwlTasks: Int = 0,
    val bestSundayTasks: Int = 0,
    val perfectWeeks: Int = 0
)

data class AchievementProgress(
    val definition: AchievementDefinition,
    /** Raw current value (can exceed the top tier). */
    val current: Int,
    /** How many tiers are unlocked (0..tiers.size). */
    val tierReached: Int,
    /** Target of the tier still in progress, or null when maxed out. */
    val nextTier: Int?
) {
    val maxed: Boolean get() = nextTier == null
}

object AchievementCatalog {

    /** Achievement list, in display order. [COLLECTOR] is evaluated last. */
    val definitions: List<AchievementDefinition> = listOf(
        AchievementDefinition(AchievementId.PERFECT_DAY, listOf(1, 10, 30)),
        AchievementDefinition(AchievementId.STREAK, listOf(21, 100, 365)),
        AchievementDefinition(AchievementId.UNSTOPPABLE, listOf(100, 1000, 5000)),
        AchievementDefinition(AchievementId.NIGHT_OWL, listOf(1)),
        AchievementDefinition(AchievementId.FLAWLESS_MONDAY, listOf(1, 4, 10)),
        AchievementDefinition(AchievementId.SUNDAY_FUN_DAY, listOf(3, 5, 15)),
        AchievementDefinition(AchievementId.FLAWLESS_WEEK, listOf(1, 4, 10)),
        AchievementDefinition(AchievementId.COLLECTOR, listOf(4, 8, 12))
    )
}

object AchievementEvaluator {

    /**
     * Evaluates the catalog. [AchievementId.COLLECTOR] is a meta
     * achievement: its current value is the number of tiers unlocked
     * across every OTHER achievement.
     */
    fun evaluate(inputs: AchievementInputs): List<AchievementProgress> {
        val base = AchievementCatalog.definitions
            .filterNot { it.id == AchievementId.COLLECTOR }
            .map { definition -> toProgress(definition, currentValue(definition.id, inputs)) }

        val unlockedTiers = base.sumOf { it.tierReached }
        val collector = AchievementCatalog.definitions
            .first { it.id == AchievementId.COLLECTOR }
            .let { toProgress(it, unlockedTiers) }

        return base + collector
    }

    private fun toProgress(definition: AchievementDefinition, current: Int): AchievementProgress {
        val tierReached = definition.tiers.count { current >= it }
        return AchievementProgress(
            definition = definition,
            current = current,
            tierReached = tierReached,
            nextTier = definition.tiers.getOrNull(tierReached)
        )
    }

    private fun currentValue(id: AchievementId, inputs: AchievementInputs): Int = when (id) {
        AchievementId.PERFECT_DAY -> inputs.perfectDays
        AchievementId.STREAK -> inputs.bestStreak
        AchievementId.UNSTOPPABLE -> inputs.totalHabitCompletions
        AchievementId.NIGHT_OWL -> inputs.nightOwlTasks
        AchievementId.FLAWLESS_MONDAY -> inputs.mondayPerfectDays
        AchievementId.SUNDAY_FUN_DAY -> inputs.bestSundayTasks
        AchievementId.FLAWLESS_WEEK -> inputs.perfectWeeks
        AchievementId.COLLECTOR -> 0 // evaluated in a second pass
    }
}
