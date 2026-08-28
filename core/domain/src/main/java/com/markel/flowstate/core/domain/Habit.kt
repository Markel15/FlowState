package com.markel.flowstate.core.domain

import java.time.DayOfWeek
import java.time.LocalDate

enum class HabitType { BOOLEAN, NUMERIC }

data class Habit(
    val id: Int = 0,
    val name: String,
    val iconName: String = "self_improvement",
    val colorArgb: Int = 0xFF6650A4.toInt(),
    val createdAt: LocalDate = LocalDate.now(),
    val habitType: HabitType = HabitType.BOOLEAN,
    val unit: String? = null,
    val targetValue: Float? = null,
    val step: Float = 1f,
    val position: Int = 0,
    val scheduledDays: Set<DayOfWeek> = DayOfWeek.entries.toSet()
)

fun Habit.isScheduledFor(date: LocalDate): Boolean = date.dayOfWeek in scheduledDays

/**
 * The first scheduled date strictly after [after], at most one week
 * ahead. Searches at most 7 days, so it always terminates, and returns
 * null for a defensive edge case: a habit with an empty schedule.
 */
fun Habit.nextScheduledDate(after: LocalDate): LocalDate? =
    (1L..7L)
        .asSequence()
        .map { after.plusDays(it) }
        .firstOrNull { isScheduledFor(it) }

data class HabitWithStatus(
    val habit: Habit,
    val isCompletedToday: Boolean,
    val streak: Int = 0,
    val todayValue: Float? = null,
)

data class HabitEntryFlat(val habitId: Int, val epochDay: Long)

data class HabitNumericEntry(val habitId: Int, val date: LocalDate, val value: Float)