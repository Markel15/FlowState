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
    TASK_TOTAL,
    NIGHT_OWL,
    EARLY_BIRD,
    FLAWLESS_MONDAY,
    SUNDAY_FUN_DAY,
    PRODUCTIVE_MORNING,
    ON_TIME,
    FLAWLESS_WEEK,
    COMEBACK
}

data class AchievementDefinition(
    val id: AchievementId,
    val tiers: List<Int>
)

/** Aggregated raw values the catalog is evaluated against. */
data class AchievementInputs(
    val totalHabitCompletions: Int = 0,
    val totalTaskCompletions: Int = 0,
    val bestStreak: Int = 0,
    val perfectDays: Int = 0,
    val mondayPerfectDays: Int = 0,
    val nightOwlTasks: Int = 0,
    val earlyBirdTasks: Int = 0,
    val bestSundayTasks: Int = 0,
    val productiveMornings: Int = 0,
    val onTimeTasks: Int = 0,
    val perfectWeeks: Int = 0,
    val comebacks: Int = 0
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

    /** Achievement list, in display order. */
    val definitions: List<AchievementDefinition> = listOf(
        AchievementDefinition(AchievementId.PERFECT_DAY, listOf(1, 10, 30)),
        AchievementDefinition(AchievementId.STREAK, listOf(21, 100, 365)),
        AchievementDefinition(AchievementId.UNSTOPPABLE, listOf(100, 1000, 5000)),
        AchievementDefinition(AchievementId.TASK_TOTAL, listOf(25, 100, 500)),
        AchievementDefinition(AchievementId.NIGHT_OWL, listOf(1)),
        AchievementDefinition(AchievementId.EARLY_BIRD, listOf(1, 5, 15)),
        AchievementDefinition(AchievementId.FLAWLESS_MONDAY, listOf(1, 4, 10)),
        AchievementDefinition(AchievementId.SUNDAY_FUN_DAY, listOf(3, 5, 15)),
        AchievementDefinition(AchievementId.PRODUCTIVE_MORNING, listOf(1, 5, 10)),
        AchievementDefinition(AchievementId.ON_TIME, listOf(5, 15, 30)),
        AchievementDefinition(AchievementId.FLAWLESS_WEEK, listOf(1, 4, 10)),
        AchievementDefinition(AchievementId.COMEBACK, listOf(1, 3, 5))
    )
}

object AchievementEvaluator {

    /** Evaluates the whole catalog against [inputs]. */
    fun evaluate(inputs: AchievementInputs): List<AchievementProgress> =
        AchievementCatalog.definitions.map { definition ->
            toProgress(definition, currentValue(definition.id, inputs))
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
        AchievementId.TASK_TOTAL -> inputs.totalTaskCompletions
        AchievementId.NIGHT_OWL -> inputs.nightOwlTasks
        AchievementId.EARLY_BIRD -> inputs.earlyBirdTasks
        AchievementId.FLAWLESS_MONDAY -> inputs.mondayPerfectDays
        AchievementId.SUNDAY_FUN_DAY -> inputs.bestSundayTasks
        AchievementId.PRODUCTIVE_MORNING -> inputs.productiveMornings
        AchievementId.ON_TIME -> inputs.onTimeTasks
        AchievementId.FLAWLESS_WEEK -> inputs.perfectWeeks
        AchievementId.COMEBACK -> inputs.comebacks
    }
}
