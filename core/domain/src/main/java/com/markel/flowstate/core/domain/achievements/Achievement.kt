package com.markel.flowstate.core.domain.achievements

import java.time.LocalDate

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

/**
 * Dates of the events behind every counter, already sorted oldest first.
 * Optional: leaving them out makes the evaluator behave exactly as before and
 * [AchievementProgress.tierDates] stays empty.
 */
data class AchievementDates(
    val perfectDays: List<LocalDate> = emptyList(),
    val mondayPerfectDays: List<LocalDate> = emptyList(),
    val perfectWeeks: List<LocalDate> = emptyList(),
    val habitCompletions: List<LocalDate> = emptyList(),
    val taskCompletions: List<LocalDate> = emptyList(),
    val nightOwl: List<LocalDate> = emptyList(),
    val earlyBird: List<LocalDate> = emptyList(),
    val productiveMornings: List<LocalDate> = emptyList(),
    val onTime: List<LocalDate> = emptyList(),
    val sundayCounts: List<DayCount> = emptyList(),
    val comebacks: List<LocalDate> = emptyList(),
    val streakRuns: List<StreakRun> = emptyList()
)

data class AchievementProgress(
    val definition: AchievementDefinition,
    /** Raw current value (can exceed the top tier). */
    val current: Int,
    /** How many tiers are unlocked (0..tiers.size). */
    val tierReached: Int,
    /** Target of the tier still in progress, or null when maxed out. */
    val nextTier: Int?,
    /**
     * Date on which each tier was crossed, aligned with
     * [AchievementDefinition.tiers]. Entries beyond [tierReached] are null, and
     * an unlocked tier can still be null when the underlying events cannot be
     * dated (e.g. a task marked as done without a completion timestamp).
     */
    val tierDates: List<LocalDate?> = emptyList()
) {
    val maxed: Boolean get() = nextTier == null

    /** Date of the last tier crossed, or null when nothing is unlocked yet. */
    val completedAt: LocalDate? get() = tierDates.getOrNull(tierReached - 1)
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

    /**
     * Evaluates the whole catalog against [inputs] and, when [dates] is
     * provided, dates every crossed tier. Omitting [dates] keeps the counters
     * untouched and leaves every tier undated (the list is still sized, with
     * nulls, so callers can index it by tier).
     */
    fun evaluate(
        inputs: AchievementInputs,
        dates: AchievementDates = AchievementDates()
    ): List<AchievementProgress> =
        AchievementCatalog.definitions.map { definition ->
            toProgress(definition, currentValue(definition.id, inputs), dates)
        }

    private fun toProgress(
        definition: AchievementDefinition,
        current: Int,
        dates: AchievementDates
    ): AchievementProgress {
        val tierReached = definition.tiers.count { current >= it }
        return AchievementProgress(
            definition = definition,
            current = current,
            tierReached = tierReached,
            nextTier = definition.tiers.getOrNull(tierReached),
            tierDates = definition.tiers.mapIndexed { index, target ->
                // Only crossed tiers get a date: an unfinished tier has no
                // "date" yet, and asking for one would invent it.
                if (index < tierReached) dateFor(definition.id, target, dates) else null
            }
        )
    }

    /**
     * Date on which [id] reached [target]: the N-th event for counters, or the
     * first date whose per-day count gets there.
     */
    private fun dateFor(id: AchievementId, target: Int, dates: AchievementDates): LocalDate? =
        when (id) {
            AchievementId.PERFECT_DAY -> dates.perfectDays.nthEvent(target)
            AchievementId.FLAWLESS_MONDAY -> dates.mondayPerfectDays.nthEvent(target)
            AchievementId.FLAWLESS_WEEK -> dates.perfectWeeks.nthEvent(target)
            AchievementId.UNSTOPPABLE -> dates.habitCompletions.nthEvent(target)
            AchievementId.TASK_TOTAL -> dates.taskCompletions.nthEvent(target)
            AchievementId.NIGHT_OWL -> dates.nightOwl.nthEvent(target)
            AchievementId.EARLY_BIRD -> dates.earlyBird.nthEvent(target)
            AchievementId.PRODUCTIVE_MORNING -> dates.productiveMornings.nthEvent(target)
            AchievementId.ON_TIME -> dates.onTime.nthEvent(target)
            AchievementId.COMEBACK -> dates.comebacks.nthEvent(target)
            // A Sunday holds several tasks, so its tier is reached on the first
            // Sunday whose total gets there, not on the N-th task.
            AchievementId.SUNDAY_FUN_DAY -> dates.sundayCounts.firstReaching(target)
            // A streak is a run, not a list of events: the date is the
            // occurrence in which the run got long enough.
            AchievementId.STREAK -> dates.streakRuns
                .mapNotNull { run -> run.dateReaching(target) }
                .minOrNull()
        }

    private fun List<LocalDate>.nthEvent(target: Int): LocalDate? = getOrNull(target - 1)

    private fun List<DayCount>.firstReaching(target: Int): LocalDate? =
        firstOrNull { it.count >= target }?.date

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
