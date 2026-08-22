package com.markel.flowstate.core.domain.usecase.habits

import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.HabitEntryFlat
import com.markel.flowstate.core.domain.HabitNumericEntry
import com.markel.flowstate.core.domain.HabitRepository
import com.markel.flowstate.core.domain.HabitStreakCalculator
import com.markel.flowstate.core.domain.HabitType
import com.markel.flowstate.core.domain.HabitWithStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import javax.inject.Inject

class GetHabitsWithStatusUseCase @Inject constructor(
    private val repository: HabitRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(date: LocalDate = LocalDate.now()): Flow<List<HabitWithStatus>> {
        // First we get the habits, then we combine them (boolean + numeric)
        return repository.getHabits()
            .flatMapLatest { habits ->
                if (habits.isEmpty()) return@flatMapLatest flowOf(emptyList())

                // Boolean habits flow
                val boolFlow = repository.getAllEntries()

                // A flow for every numeric habit, combined within one only flow
                // Map<habitId, List<HabitNumericEntry>>
                val numericFlows = habits
                    .filter { it.habitType == HabitType.NUMERIC }
                    .map { habit -> repository.getNumericEntries(habit.id) }

                if (numericFlows.isEmpty()) {
                    boolFlow.combine(flowOf(emptyMap<Int, List<HabitNumericEntry>>())) { entries, numeric ->
                        buildStatus(habits, entries.groupBy { it.habitId }, numeric, date)
                    }
                } else {
                    val numericHabitIds = habits
                        .filter { it.habitType == HabitType.NUMERIC }
                        .map { it.id }

                    val combinedNumeric: Flow<Map<Int, List<HabitNumericEntry>>> =
                        numericFlows.reduce { acc, flow ->
                            acc.combine(flow) { a, b -> a + b }
                        }.combine(flowOf(numericHabitIds)) { allEntries, ids ->
                            allEntries.groupBy { it.habitId }
                                .filterKeys { it in ids }
                        }

                    boolFlow.combine(combinedNumeric) { boolEntries, numericByHabit ->
                        buildStatus(
                            habits = habits,
                            boolEntriesByHabit = boolEntries.groupBy { it.habitId },
                            numericByHabit = numericByHabit,
                            date = date
                        )
                    }
                }
            }
    }

    private fun buildStatus(
        habits: List<Habit>,
        boolEntriesByHabit: Map<Int, List<HabitEntryFlat>>,
        numericByHabit: Map<Int, List<HabitNumericEntry>>,
        date: LocalDate
    ): List<HabitWithStatus> {
        val today = date.toEpochDay()

        return habits
            .sortedBy { it.position }
            .map { habit ->
                when (habit.habitType) {
                    HabitType.BOOLEAN -> {
                        val entries = boolEntriesByHabit[habit.id] ?: emptyList()
                        val isCompletedToday = entries.any { it.epochDay == today }
                        val completedDates = entries.map { LocalDate.ofEpochDay(it.epochDay) }
                        val streak = HabitStreakCalculator.current(
                            completedDates = completedDates,
                            scheduledDays = habit.scheduledDays,
                            today = date
                        )
                        HabitWithStatus(
                            habit = habit,
                            isCompletedToday = isCompletedToday,
                            streak = streak
                        )
                    }
                    HabitType.NUMERIC -> {
                        val entries = numericByHabit[habit.id] ?: emptyList()
                        val entriesByDay = entries.associateBy { it.date.toEpochDay() }
                        val todayValue = entriesByDay[today]?.value

                        // Completed if there is a value today and is bigger than the goal (or simply have a value without goal)
                        val isCompletedToday = when {
                            todayValue == null -> false
                            habit.targetValue != null -> todayValue >= habit.targetValue
                            else -> todayValue > 0f
                        }

                        val completedDates = HabitStreakCalculator.qualifyingNumericDates(
                            entries = entries,
                            targetValue = habit.targetValue
                        )
                        val streak = HabitStreakCalculator.current(
                            completedDates = completedDates,
                            scheduledDays = habit.scheduledDays,
                            today = date
                        )

                        HabitWithStatus(
                            habit = habit,
                            isCompletedToday = isCompletedToday,
                            streak = streak,
                            todayValue = todayValue
                        )
                    }
                }
            }
    }

}