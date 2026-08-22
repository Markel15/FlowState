package com.markel.flowstate.feature.habits

import app.cash.turbine.test
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.HabitNumericEntry
import com.markel.flowstate.core.domain.HabitRepository
import com.markel.flowstate.core.domain.HabitType
import com.markel.flowstate.core.domain.usecase.habits.GetHabitByIdUseCase
import com.markel.flowstate.core.domain.usecase.habits.GetNumericEntriesUseCase
import com.markel.flowstate.core.testing.util.MainDispatcherRule
import com.markel.flowstate.feature.habits.details.CalendarViewMode
import com.markel.flowstate.feature.habits.details.HabitDetailViewModel
import com.markel.flowstate.feature.habits.details.WeeklyBarsMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class HabitDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val habitRepository: HabitRepository = mockk(relaxed = true)
    private val getHabitById: GetHabitByIdUseCase = mockk(relaxed = true)
    private val getNumericDetails: GetNumericEntriesUseCase = mockk(relaxed = true)
    private val userPreferences: UserPreferencesRepository = mockk(relaxed = true)

    private lateinit var viewModel: HabitDetailViewModel

    @Before
    fun setup() {
        // Mock default user preferences to avoid the init block suspending indefinitely
        coEvery { userPreferences.calendarViewMode } returns flowOf(CalendarViewMode.ONE_MONTH.name)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun habit(
        id: Int = 1,
        type: HabitType = HabitType.BOOLEAN,
        targetValue: Float? = null,
        scheduledDays: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    ) = Habit(
        id = id,
        name = "Test habit",
        iconName = "icon",
        colorArgb = 0xFF123456.toInt(),
        habitType = type,
        targetValue = targetValue,
        createdAt = LocalDate.now().minusDays(30),
        scheduledDays = scheduledDays
    )

    private fun numericEntry(date: LocalDate, value: Float) = HabitNumericEntry(
        habitId = 1,
        date = date,
        value = value
    )

    private fun buildViewModel(habitId: Int = 1) = HabitDetailViewModel(
        habitId = habitId,
        getHabitById = getHabitById,
        habitRepository = habitRepository,
        getNumericDetails = getNumericDetails,
        userPreferences = userPreferences
    )

    // ── init / loading ────────────────────────────────────────────────────────

    @Test
    fun init_whenHabitNotFound_keepsDefaultState() = runTest {
        // GIVEN - getHabitById returns null
        coEvery { getHabitById(99) } returns null

        // WHEN
        viewModel = buildViewModel(habitId = 99)

        // THEN - State should remain as default without crashing
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.habit)
            assertEquals(0, state.currentStreak)
            assertEquals(0, state.bestStreak)
        }
    }

    @Test
    fun init_withBooleanHabit_loadsBooleanData() = runTest {
        // GIVEN
        val habit = habit(id = 1, type = HabitType.BOOLEAN)
        val today = LocalDate.now()
        coEvery { getHabitById(1) } returns habit
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(listOf(today))

        // WHEN
        viewModel = buildViewModel(habitId = 1)

        // THEN
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(habit, state.habit)
            assertTrue(today.toEpochDay() in state.allEntries)
            assertEquals(1, state.currentStreak)
        }
    }

    @Test
    fun init_withNumericHabit_loadsNumericData() = runTest {
        // GIVEN
        val habit = habit(id = 1, type = HabitType.NUMERIC, targetValue = 10f)
        val today = LocalDate.now()
        coEvery { getHabitById(1) } returns habit
        coEvery { getNumericDetails(1) } returns flowOf(listOf(numericEntry(today, 15f)))

        // WHEN
        viewModel = buildViewModel(habitId = 1)

        // THEN
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(habit, state.habit)
            assertEquals(15f, state.numericEntries[today])
            assertEquals(1, state.currentStreak) // 15 >= 10, so streak is 1
        }
    }

    // ── Boolean Calculations ──────────────────────────────────────────────────

    @Test
    fun calculateCurrentStreak_boolean_withGap_onlyCountsLatest() = runTest {
        // GIVEN
        val today = LocalDate.now()
        val entries = listOf(
            today, today.minusDays(1), // Streak of 2
            today.minusDays(3), today.minusDays(4) // Gap on minusDays(2)
        )
        coEvery { getHabitById(1) } returns habit(type = HabitType.BOOLEAN)
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(entries)

        // WHEN
        viewModel = buildViewModel()

        // THEN
        viewModel.uiState.test {
            assertEquals(2, awaitItem().currentStreak)
        }
    }

    @Test
    fun calculateBestStreak_boolean_picksLongestConsecutiveRun() = runTest {
        // GIVEN
        val today = LocalDate.now()
        val entries = listOf(
            today.minusDays(10), today.minusDays(9), // Streak of 2
            today.minusDays(4), today.minusDays(3), today.minusDays(2), today.minusDays(1), today // Streak of 5
        )
        coEvery { getHabitById(1) } returns habit(type = HabitType.BOOLEAN)
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(entries)

        // WHEN
        viewModel = buildViewModel()

        // THEN
        viewModel.uiState.test {
            assertEquals(5, awaitItem().bestStreak)
        }
    }

    @Test
    fun scheduledBooleanHabit_countsOnlyScheduledOccurrencesInStreaks() = runTest {
        // GIVEN
        val today = LocalDate.now()
        val scheduledDates = listOf(today.minusDays(4), today.minusDays(2), today)
        val scheduledDays = scheduledDates.mapTo(mutableSetOf()) { it.dayOfWeek }
        coEvery { getHabitById(1) } returns habit(
            type = HabitType.BOOLEAN,
            scheduledDays = scheduledDays
        )
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(scheduledDates)

        // WHEN
        viewModel = buildViewModel()

        // THEN
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(3, state.currentStreak)
            assertEquals(3, state.bestStreak)
        }
    }

    @Test
    fun calculateDayOfWeekCompletions_groupsCorrectlyAsRates() = runTest {
        // GIVEN - 2 Mondays and 1 Friday, habit created 30 days ago
        val monday1 = LocalDate.now().minusWeeks(1).with(DayOfWeek.MONDAY)
        val monday2 = monday1.minusWeeks(2)
        val friday = LocalDate.now().minusWeeks(1).with(DayOfWeek.FRIDAY)
        coEvery { getHabitById(1) } returns habit()
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(listOf(monday1, monday2, friday))

        // WHEN
        viewModel = buildViewModel()

        // THEN - Should return completion rates (0..1) per day of week
        viewModel.uiState.test {
            val dowCompletions = awaitItem().dayOfWeekCompletions
            // Monday has 2 completions, so rate > 0
            assertTrue(dowCompletions[1]!! > 0f)
            // Friday has 1 completion, so rate > 0
            assertTrue(dowCompletions[5]!! > 0f)
            // Monday rate should be higher than Friday (2 vs 1 completions)
            assertTrue(dowCompletions[1]!! > dowCompletions[5]!!)
            // All values should be valid rates between 0 and 1
            dowCompletions.values.forEach { rate ->
                assertTrue(rate in 0f..1f)
            }
        }
    }

    // ── Numeric Calculations ──────────────────────────────────────────────────

    @Test
    fun calculateNumericStreak_onlyCountsDaysMeetingTarget() = runTest {
        // GIVEN - Target is 10f
        val today = LocalDate.now()
        val entries = listOf(
            numericEntry(today, 12f),  // Valid
            numericEntry(today.minusDays(1), 10f),  // Valid
            numericEntry(today.minusDays(2), 5f),   // Invalid (breaks streak)
            numericEntry(today.minusDays(3), 15f)   // Valid but broken
        )
        coEvery { getHabitById(1) } returns habit(type = HabitType.NUMERIC, targetValue = 10f)
        coEvery { getNumericDetails(1) } returns flowOf(entries)

        // WHEN
        viewModel = buildViewModel()

        // THEN - Streak should be 2
        viewModel.uiState.test {
            assertEquals(2, awaitItem().currentStreak)
        }
    }

    @Test
    fun scheduledNumericHabit_usesSameStreakRulesAsBooleanHabit() = runTest {
        // GIVEN
        val today = LocalDate.now()
        val scheduledDates = listOf(today.minusDays(4), today.minusDays(2), today)
        val scheduledDays = scheduledDates.mapTo(mutableSetOf()) { it.dayOfWeek }
        val entries = scheduledDates.map { numericEntry(it, 10f) }
        coEvery { getHabitById(1) } returns habit(
            type = HabitType.NUMERIC,
            targetValue = 10f,
            scheduledDays = scheduledDays
        )
        coEvery { getNumericDetails(1) } returns flowOf(entries)

        // WHEN
        viewModel = buildViewModel()

        // THEN
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(3, state.currentStreak)
            assertEquals(3, state.bestStreak)
        }
    }

    @Test
    fun calculateNumericBestStreak_ignoresDaysBelowTarget() = runTest {
        // GIVEN - Target is 10f
        val today = LocalDate.now()
        val entries = listOf(
            numericEntry(today.minusDays(6), 11f), // Run 1
            numericEntry(today.minusDays(5), 11f), // Run 1
            numericEntry(today.minusDays(4), 11f), // Run 1 (Best streak: 3)
            numericEntry(today.minusDays(3), 2f),  // Break
            numericEntry(today.minusDays(2), 15f), // Run 2
            numericEntry(today.minusDays(1), 10f)  // Run 2
        )
        coEvery { getHabitById(1) } returns habit(type = HabitType.NUMERIC, targetValue = 10f)
        coEvery { getNumericDetails(1) } returns flowOf(entries)

        // WHEN
        viewModel = buildViewModel()

        // THEN
        viewModel.uiState.test {
            assertEquals(3, awaitItem().bestStreak)
        }
    }

    @Test
    fun calculateMonthlyProgress_calculatesCorrectlyForCurrentMonth() = runTest {
        // GIVEN - Target is 5f daily
        val today = LocalDate.now()
        val currentMonth = YearMonth.now()
        val day1 = currentMonth.atDay(1)
        val day2 = currentMonth.atDay(2)

        val entries = listOf(
            numericEntry(day1, 10f),  // Completed
            numericEntry(day2, 2f)  // Not completed
        )
        coEvery { getHabitById(1) } returns habit(type = HabitType.NUMERIC, targetValue = 5f)
        coEvery { getNumericDetails(1) } returns flowOf(entries)

        // WHEN
        viewModel = buildViewModel()

        // THEN
        viewModel.uiState.test {
            val progress = awaitItem().monthlyProgress
            assertNotNull(progress)
            if (today.dayOfMonth == 1) {
                assertEquals(10f, progress!!.currentValue)
                assertEquals(1, progress.daysCompleted)
                assertEquals(10f, progress.dailyAverage) // 10f / 1 day
            } else {
                assertEquals(12f, progress!!.currentValue) // 10 + 2
                assertEquals(1, progress.daysCompleted) // Only one day met the 5f target
                assertEquals(6f, progress.dailyAverage) // 12f / 2 days with data
            }
        }
    }

    @Test
    fun calculateDayOfWeekAverages_averagesOverAllOpportunities() = runTest {
        // GIVEN - Two Mondays with values 10 and 20, habit created 30 days ago
        val monday1 = LocalDate.now().with(DayOfWeek.MONDAY)
        val monday2 = monday1.minusWeeks(1)
        val entries = listOf(
            numericEntry(monday1, 10f),
            numericEntry(monday2, 20f)
        )
        coEvery { getHabitById(1) } returns habit(type = HabitType.NUMERIC)
        coEvery { getNumericDetails(1) } returns flowOf(entries)

        // WHEN
        viewModel = buildViewModel()

        // THEN - Monday average should include all Mondays since creation (not just 2)
        // With 30 days of history, there are ~5 Mondays. Sum=30, so avg=30/5=6 (approx)
        viewModel.uiState.test {
            val averages = awaitItem().dayOfWeekAverages
            val mondayLabel = DayOfWeek.MONDAY.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() }
            val mondayData = averages.find { it.label == mondayLabel }
            assertNotNull(mondayData)
            // Average is over ALL opportunities, so it should be less than the simple
            // average of the 2 entries (which would be 15)
            assertTrue(mondayData!!.count < 15f)
            // But it should be > 0 since we have data
            assertTrue(mondayData.count > 0f)
        }
    }

    // ── Navigation & View Mode ────────────────────────────────────────────────

    @Test
    fun cycleViewMode_cyclesThroughAllModes_andSavesToPreferences() = runTest {
        // GIVEN
        coEvery { getHabitById(1) } returns habit()
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(emptyList())
        viewModel = buildViewModel()

        // WHEN / THEN
        viewModel.uiState.test {
            assertEquals(CalendarViewMode.ONE_MONTH, awaitItem().viewMode)

            viewModel.cycleViewMode()
            assertEquals(CalendarViewMode.THREE_MONTHS, awaitItem().viewMode)

            viewModel.cycleViewMode()
            assertEquals(CalendarViewMode.ONE_YEAR, awaitItem().viewMode)

            viewModel.cycleViewMode()
            assertEquals(CalendarViewMode.ONE_MONTH, awaitItem().viewMode)
        }

        // Verify preferences were saved during the cycle
        coVerify { userPreferences.saveCalendarViewMode(CalendarViewMode.THREE_MONTHS.name) }
    }

    @Test
    fun navigatePrevious_inOneMonthMode_decrementsMonthAndWrapsYear() = runTest {
        // GIVEN - A view model forced to January
        coEvery { getHabitById(1) } returns habit()
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(emptyList())
        viewModel = buildViewModel()

        // Navigate until month is 0 (January)
        while (viewModel.uiState.value.displayMonth > 0) {
            viewModel.navigatePrevious()
        }
        val yearBeforeWrap = viewModel.uiState.value.displayYear

        // WHEN - Navigate previous from January
        viewModel.navigatePrevious()

        // THEN - Should wrap to December (11) and decrement year
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(11, state.displayMonth)
            assertEquals(yearBeforeWrap - 1, state.displayYear)
        }
    }

    @Test
    fun navigateNext_inOneMonthMode_preventsGoingToFuture() = runTest {
        // GIVEN
        coEvery { getHabitById(1) } returns habit()
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(emptyList())
        viewModel = buildViewModel()
        val currentMonth = viewModel.uiState.value.displayMonth

        // WHEN - Try navigating to the future
        viewModel.navigateNext()

        // THEN - Should remain on the current month
        viewModel.uiState.test {
            assertEquals(currentMonth, awaitItem().displayMonth)
        }
    }

    @Test
    fun navigatePrevious_inThreeMonthsMode_goesBackThreeMonths() = runTest {
        // GIVEN
        coEvery { getHabitById(1) } returns habit()
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(emptyList())
        viewModel = buildViewModel()
        viewModel.cycleViewMode() // Switch to THREE_MONTHS

        val initialMonth = viewModel.uiState.value.displayMonth

        // WHEN
        viewModel.navigatePrevious()

        // THEN
        viewModel.uiState.test {
            val expectedMonth = (initialMonth - 3 + 12) % 12
            assertEquals(expectedMonth, awaitItem().displayMonth)
        }
    }

    @Test
    fun navigatePrevious_inOneYearMode_decrementsYear() = runTest {
        // GIVEN
        coEvery { getHabitById(1) } returns habit()
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(emptyList())
        viewModel = buildViewModel()
        viewModel.cycleViewMode()
        viewModel.cycleViewMode() // Switch to ONE_YEAR

        val initialYear = viewModel.uiState.value.displayYear

        // WHEN
        viewModel.navigatePrevious()

        // THEN
        viewModel.uiState.test {
            assertEquals(initialYear - 1, awaitItem().displayYear)
        }
    }

    // ── Weekly Bars & Selection ───────────────────────────────────────────────

    @Test
    fun setWeeklyBarsMode_updatesMode_andClearsSelection() = runTest {
        // GIVEN
        coEvery { getHabitById(1) } returns habit()
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(emptyList())
        viewModel = buildViewModel()
        viewModel.selectBar(3) // Set a selection

        // WHEN
        viewModel.setWeeklyBarsMode(WeeklyBarsMode.SIXTEEN)

        // THEN
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(WeeklyBarsMode.SIXTEEN, state.weeklyBarsMode)
            assertNull(state.selectedBarIndex)
        }
    }

    @Test
    fun selectBar_updatesSelectedBarIndex() = runTest {
        // GIVEN
        coEvery { getHabitById(1) } returns habit()
        coEvery { habitRepository.getEntriesForHabit(1) } returns flowOf(emptyList())
        viewModel = buildViewModel()

        // WHEN
        viewModel.selectBar(5)

        // THEN
        viewModel.uiState.test {
            assertEquals(5, awaitItem().selectedBarIndex)
        }
    }
}