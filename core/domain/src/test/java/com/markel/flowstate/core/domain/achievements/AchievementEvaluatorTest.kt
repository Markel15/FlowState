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
    fun `collector counts tiers unlocked by other achievements`() {
        // 3 tiers from a maxed streak + 1 tier from a perfect day
        val inputs = AchievementInputs(bestStreak = 365, perfectDays = 1)
        val p = progress(AchievementId.COLLECTOR, inputs)
        assertEquals(4, p.current)
        assertEquals(1, p.tierReached)
        assertEquals(8, p.nextTier)
    }

    @Test
    fun `collector does not count itself`() {
        // Everything maxed: 22 tiers total, collector contributes 3,
        // so it must see 19 unlocked tiers (reaching its own top tier).
        val inputs = AchievementInputs(
            totalHabitCompletions = 5000,
            bestStreak = 365,
            perfectDays = 30,
            mondayPerfectDays = 10,
            nightOwlTasks = 1,
            bestSundayTasks = 15,
            perfectWeeks = 10
        )
        val p = progress(AchievementId.COLLECTOR, inputs)
        assertEquals(19, p.current)
        assertTrue(p.maxed)
    }

    @Test
    fun `evaluate returns every catalog entry in order`() {
        val results = AchievementEvaluator.evaluate(AchievementInputs())
        assertEquals(AchievementCatalog.definitions.map { it.id }, results.map { it.definition.id })
    }
}
