package com.markel.flowstate.core.domain.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEvaluatorTest {

    private fun progress(id: AchievementId, inputs: AchievementInputs): AchievementProgress =
        AchievementEvaluator.evaluate(inputs).first { it.definition.id == id }

    @Test
    fun `with no activity everything is locked`() {
        val results = AchievementEvaluator.evaluate(AchievementInputs())
        assertTrue(results.all { it.tierReached == 0 })
        assertTrue(results.none { it.maxed })
    }

    @Test
    fun `streak tiers unlock gradually`() {
        val p = progress(AchievementId.STREAK, AchievementInputs(bestStreak = 21))
        assertEquals(1, p.tierReached)
        assertEquals(100, p.nextTier)
    }

    @Test
    fun `streak maxes out at the top tier`() {
        val p = progress(AchievementId.STREAK, AchievementInputs(bestStreak = 365))
        assertEquals(3, p.tierReached)
        assertTrue(p.maxed)
        assertNull(p.nextTier)
        assertEquals(365, p.current) // the raw value keeps growing
    }

    @Test
    fun `night owl is single tier`() {
        assertEquals(0, progress(AchievementId.NIGHT_OWL, AchievementInputs()).tierReached)
        val p = progress(AchievementId.NIGHT_OWL, AchievementInputs(nightOwlTasks = 1))
        assertEquals(1, p.tierReached)
        assertTrue(p.maxed)
    }

    @Test
    fun `sunday fun day uses the best single sunday`() {
        val p = progress(AchievementId.SUNDAY_FUN_DAY, AchievementInputs(bestSundayTasks = 5))
        assertEquals(2, p.tierReached) // 3 and 5 reached
        assertEquals(15, p.nextTier)
    }

    @Test
    fun `comeback unlocks at 1, 3 and 5`() {
        assertEquals(0, progress(AchievementId.COMEBACK, AchievementInputs()).tierReached)
        val first = progress(AchievementId.COMEBACK, AchievementInputs(comebacks = 1))
        assertEquals(1, first.tierReached)
        assertEquals(3, first.nextTier)
        assertTrue(progress(AchievementId.COMEBACK, AchievementInputs(comebacks = 5)).maxed)
    }

    @Test
    fun `new task achievements unlock at their thresholds`() {
        val inputs = AchievementInputs(
            earlyBirdTasks = 5,
            onTimeTasks = 15,
            productiveMornings = 1
        )
        assertEquals(2, progress(AchievementId.EARLY_BIRD, inputs).tierReached)
        assertEquals(2, progress(AchievementId.ON_TIME, inputs).tierReached)
        assertEquals(1, progress(AchievementId.PRODUCTIVE_MORNING, inputs).tierReached)
    }

    @Test
    fun `task total tiers climb at 25, 100 and 500`() {
        assertEquals(
            0,
            progress(AchievementId.TASK_TOTAL, AchievementInputs(totalTaskCompletions = 24))
                .tierReached
        )
        val first = progress(AchievementId.TASK_TOTAL, AchievementInputs(totalTaskCompletions = 25))
        assertEquals(1, first.tierReached)
        assertEquals(100, first.nextTier)
        val maxed = progress(AchievementId.TASK_TOTAL, AchievementInputs(totalTaskCompletions = 500))
        assertEquals(3, maxed.tierReached)
        assertTrue(maxed.maxed)
    }

    @Test
    fun `evaluate returns every catalog entry in order`() {
        val results = AchievementEvaluator.evaluate(AchievementInputs())
        assertEquals(AchievementCatalog.definitions.map { it.id }, results.map { it.definition.id })
    }
}
