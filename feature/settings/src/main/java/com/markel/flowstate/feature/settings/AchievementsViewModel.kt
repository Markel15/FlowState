package com.markel.flowstate.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.domain.achievements.AchievementEvaluator
import com.markel.flowstate.core.domain.achievements.AchievementInputs
import com.markel.flowstate.core.domain.achievements.AchievementProgress
import com.markel.flowstate.core.domain.usecase.achievements.GetAchievementMetricsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    getAchievementMetrics: GetAchievementMetricsUseCase
) : ViewModel() {

    val achievements: StateFlow<List<AchievementProgress>> = getAchievementMetrics()
        .map { metrics ->
            AchievementEvaluator.evaluate(
                AchievementInputs(
                    totalHabitCompletions = metrics.habit.totalCompletions,
                    bestStreak = metrics.habit.bestStreak,
                    perfectDays = metrics.habit.perfectDays,
                    mondayPerfectDays = metrics.habit.mondayPerfectDays,
                    perfectWeeks = metrics.habit.perfectWeeks,
                    nightOwlTasks = metrics.tasks.nightOwlTasks,
                    bestSundayTasks = metrics.tasks.bestSundayTasks
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
