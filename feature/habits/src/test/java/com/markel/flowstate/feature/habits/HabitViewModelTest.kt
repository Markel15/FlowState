package com.markel.flowstate.feature.habits

import app.cash.turbine.test
import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.HabitEntryFlat
import com.markel.flowstate.core.domain.HabitWithStatus
import com.markel.flowstate.core.domain.HabitType
import com.markel.flowstate.core.domain.usecase.habits.DecrementNumericValueUseCase
import com.markel.flowstate.core.domain.usecase.habits.DeleteHabitUseCase
import com.markel.flowstate.core.domain.usecase.habits.DeleteNumericEntryUseCase
import com.markel.flowstate.core.domain.usecase.habits.GetAllBooleanEntriesUseCase
import com.markel.flowstate.core.domain.usecase.habits.GetAllNumericEntriesUseCase
import com.markel.flowstate.core.domain.usecase.habits.GetHabitsWithStatusUseCase
import com.markel.flowstate.core.domain.usecase.habits.IncrementNumericValueUseCase
import com.markel.flowstate.core.domain.usecase.habits.InsertHabitUseCase
import com.markel.flowstate.core.domain.usecase.habits.LogNumericEntryUseCase
import com.markel.flowstate.core.domain.usecase.habits.ToggleHabitEntryUseCase
import com.markel.flowstate.core.domain.usecase.habits.UpdateHabitUseCase
import com.markel.flowstate.core.domain.usecase.habits.UpdateHabitsOrderUseCase
import com.markel.flowstate.core.testing.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mocks for all UseCases used in the ViewModel
    private val getHabitsWithStatus: GetHabitsWithStatusUseCase = mockk(relaxed = true)
    private val getAllBooleanEntries: GetAllBooleanEntriesUseCase = mockk(relaxed = true)
    private val getAllNumericEntries: GetAllNumericEntriesUseCase = mockk(relaxed = true)
    private val insertHabit: InsertHabitUseCase = mockk(relaxed = true)
    private val updateHabit: UpdateHabitUseCase = mockk(relaxed = true)
    private val deleteHabit: DeleteHabitUseCase = mockk(relaxed = true)
    private val toggleEntry: ToggleHabitEntryUseCase = mockk(relaxed = true)
    private val logNumericEntry: LogNumericEntryUseCase = mockk(relaxed = true)
    private val incrementNumericValue: IncrementNumericValueUseCase = mockk(relaxed = true)
    private val decrementNumericValue: DecrementNumericValueUseCase = mockk(relaxed = true)
    private val deleteNumericEntry: DeleteNumericEntryUseCase = mockk(relaxed = true)
    private val updateHabitsOrder: UpdateHabitsOrderUseCase = mockk(relaxed = true)

    private lateinit var viewModel: HabitViewModel

    @Before
    fun setUp() {
        coEvery { getAllNumericEntries() } returns flowOf(emptyList())  // Avoid blocking tests waiting for flows. Tests that would need new numeric entries would overwrite this
    }
    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun habit(id: Int = 1, name: String = "Test habit") = Habit(
        id = id,
        name = name,
        iconName = "icon",
        colorArgb = 0xFF123456.toInt(),
        habitType = HabitType.BOOLEAN,
        createdAt = LocalDate.now().minusDays(7)
    )

    private fun habitWithStatus(habit: Habit = habit(), completedToday: Boolean = false) =
        HabitWithStatus(habit = habit, isCompletedToday = completedToday)

    private fun entry(habitId: Int, epochDay: Long) =
        HabitEntryFlat(habitId = habitId, epochDay = epochDay)

    private fun buildViewModel() = HabitViewModel(
        getHabitsWithStatus = getHabitsWithStatus,
        getAllBooleanEntries = getAllBooleanEntries,
        getAllNumericEntries = getAllNumericEntries,
        insertHabit = insertHabit,
        updateHabit = updateHabit,
        deleteHabit = deleteHabit,
        toggleEntry = toggleEntry,
        logNumericEntry = logNumericEntry,
        incrementNumericValue = incrementNumericValue,
        decrementNumericValue = decrementNumericValue,
        deleteNumericEntry = deleteNumericEntry,
        updateHabitsOrder = updateHabitsOrder
    )

    // ── uiState ───────────────────────────────────────────────────────────────

    @Test
    fun uiState_initialValue_isLoading() = runTest {
        // GIVEN - Flows that never emit (simulate slow loading)
        coEvery { getHabitsWithStatus() } returns flowOf()
        coEvery { getAllBooleanEntries() } returns flowOf()

        // WHEN
        viewModel = buildViewModel()

        // THEN - The first emitted value must be Loading
        viewModel.uiState.test {
            assertTrue(awaitItem() is HabitUiState.Loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_whenRepositoryEmitsData_transitionsToSuccess() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(listOf(habitWithStatus(habit(id = 1))))
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())

        // WHEN
        viewModel = buildViewModel()

        // THEN
        viewModel.uiState.test {
            val state = awaitItem()
            val successState = if (state is HabitUiState.Loading) awaitItem() else state
            assertTrue(successState is HabitUiState.Success)
            assertEquals(1, (successState as HabitUiState.Success).totalHabits)
        }
    }

    @Test
    fun uiState_withNoEntries_completedTodayIsZero() = runTest {
        // GIVEN - Two habits but no completed entries
        coEvery { getHabitsWithStatus() } returns flowOf(
            listOf(habitWithStatus(habit(id = 1)), habitWithStatus(habit(id = 2)))
        )
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())

        // WHEN
        viewModel = buildViewModel()

        // THEN
        viewModel.uiState.test {
            val successState = awaitItem().let {
                if (it is HabitUiState.Loading) awaitItem() else it
            } as HabitUiState.Success

            assertEquals(0, successState.completedToday)
        }
    }

    @Test
    fun uiState_withTodayEntry_completedTodayCountsCorrectly() = runTest {
        // GIVEN - Habit 1 completed today, habit 2 not
        val today = LocalDate.now().toEpochDay()
        coEvery { getHabitsWithStatus() } returns flowOf(
            listOf(habitWithStatus(habit(id = 1), completedToday = true), habitWithStatus(habit(id = 2)))
        )
        coEvery { getAllBooleanEntries() } returns flowOf(
            listOf(entry(habitId = 1, epochDay = today))
        )

        // WHEN
        viewModel = buildViewModel()

        // THEN
        viewModel.uiState.test {
            val successState = awaitItem().let {
                if (it is HabitUiState.Loading) awaitItem() else it
            } as HabitUiState.Success

            assertEquals(1, successState.completedToday)
        }
    }

    @Test
    fun uiState_entriesGroupedByHabitId_inWeekEntriesByHabit() = runTest {
        // GIVEN - Two entries for habit 1, one for habit 2
        val today = LocalDate.now().toEpochDay()
        val yesterday = today - 1
        coEvery { getHabitsWithStatus() } returns flowOf(
            listOf(habitWithStatus(habit(id = 1)), habitWithStatus(habit(id = 2)))
        )
        coEvery { getAllBooleanEntries() } returns flowOf(
            listOf(
                entry(habitId = 1, epochDay = today),
                entry(habitId = 1, epochDay = yesterday),
                entry(habitId = 2, epochDay = today)
            )
        )

        // WHEN
        viewModel = buildViewModel()

        // THEN
        viewModel.uiState.test {
            val successState = awaitItem().let {
                if (it is HabitUiState.Loading) awaitItem() else it
            } as HabitUiState.Success

            assertEquals(setOf(today, yesterday), successState.weekEntriesByHabit[1])
            assertEquals(setOf(today), successState.weekEntriesByHabit[2])
        }
    }

    // ── showAddDialog / hideAddDialog ─────────────────────────────────────────

    @Test
    fun showAddDialog_setsShowAddDialogToTrue() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()

        // WHEN
        viewModel.showAddDialog()

        // THEN
        viewModel.uiState.test {
            val successState = awaitItem().let {
                if (it is HabitUiState.Loading) awaitItem() else it
            } as HabitUiState.Success

            assertTrue(successState.showAddDialog)
        }
    }

    @Test
    fun hideAddDialog_setsShowAddDialogToFalse() = runTest {
        // GIVEN - Dialog already open
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()
        viewModel.showAddDialog()

        // WHEN
        viewModel.hideAddDialog()

        // THEN
        viewModel.uiState.test {
            val successState = awaitItem().let {
                if (it is HabitUiState.Loading) awaitItem() else it
            } as HabitUiState.Success

            assertFalse(successState.showAddDialog)
        }
    }

    // ── addHabit ──────────────────────────────────────────────────────────────

    @Test
    fun addHabit_withValidName_callsInsertHabitUseCase() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()

        // WHEN
        viewModel.addHabit("Read", "book_icon", 0xFF0000FF.toInt())

        // THEN
        coVerify {
            insertHabit(match { habit ->
                habit.name == "Read" &&
                        habit.iconName == "book_icon" &&
                        habit.colorArgb == 0xFF0000FF.toInt() &&
                        habit.habitType == HabitType.BOOLEAN &&
                        habit.scheduledDays == DayOfWeek.entries.toSet()
            })
        }
    }

    @Test
    fun addHabit_withSelectedDays_persistsSelection() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()
        val selectedDays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.FRIDAY
        )

        // WHEN
        viewModel.addHabit(
            name = "Exercise",
            iconName = "fitness_center",
            colorArgb = 0,
            scheduledDays = selectedDays
        )

        // THEN
        coVerify {
            insertHabit(match { it.scheduledDays == selectedDays })
        }
    }

    @Test
    fun addHabit_withNoScheduledDays_doesNotCallInsertHabitUseCase() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()

        // WHEN
        viewModel.addHabit(
            name = "Exercise",
            iconName = "fitness_center",
            colorArgb = 0,
            scheduledDays = emptySet()
        )

        // THEN
        coVerify(exactly = 0) { insertHabit(any()) }
    }

    @Test
    fun addHabit_withBlankName_doesNotCallRepository() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()

        // WHEN
        viewModel.addHabit("   ", "icon", 0)

        // THEN - A blank name must be silently ignored
        coVerify(exactly = 0) { insertHabit(any()) }
    }

    @Test
    fun addHabit_withValidName_closesAddDialog() = runTest {
        // GIVEN - Dialog is open before saving
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()
        viewModel.showAddDialog()

        // WHEN
        viewModel.addHabit("Meditate", "lotus_icon", 0)

        // THEN - Dialog must be dismissed after a successful add
        viewModel.uiState.test {
            val successState = awaitItem().let {
                if (it is HabitUiState.Loading) awaitItem() else it
            } as HabitUiState.Success

            assertFalse(successState.showAddDialog)
        }
    }

    // ── editHabit ─────────────────────────────────────────────────────────────

    @Test
    fun editHabit_withValidName_callsUpdateHabitUseCase() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()
        val oldHabit = habit(id = 1, name = "Old Name")
        val newScheduledDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)

        // WHEN
        viewModel.editHabit(
            habit = oldHabit,
            newName = "New Name",
            newIcon = "new_icon",
            newColorArgb = 0xFF00FF00.toInt(),
            newUnit = "Pages",
            newTargetValue = 20f,
            newStep = 5f,
            newScheduledDays = newScheduledDays
        )

        // THEN
        coVerify {
            updateHabit(match { habit ->
                habit.id == 1 &&
                        habit.name == "New Name" &&
                        habit.iconName == "new_icon" &&
                        habit.colorArgb == 0xFF00FF00.toInt() &&
                        habit.unit == "Pages" &&
                        habit.targetValue == 20f &&
                        habit.step == 5f &&
                        habit.scheduledDays == newScheduledDays
            })
        }
    }

    @Test
    fun editHabit_withBlankName_doesNotCallUpdateHabitUseCase() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()
        val oldHabit = habit(id = 1, name = "Old Name")

        // WHEN
        viewModel.editHabit(
            habit = oldHabit,
            newName = "   ",
            newIcon = "new_icon",
            newColorArgb = 0xFF00FF00.toInt()
        )

        // THEN - A blank name must be silently ignored
        coVerify(exactly = 0) { updateHabit(any()) }
    }

    @Test
    fun editHabit_withNoScheduledDays_doesNotCallUpdateHabitUseCase() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()

        // WHEN
        viewModel.editHabit(
            habit = habit(id = 1),
            newName = "Exercise",
            newIcon = "fitness_center",
            newColorArgb = 0,
            newScheduledDays = emptySet()
        )

        // THEN
        coVerify(exactly = 0) { updateHabit(any()) }
    }

    // ── deleteHabit ───────────────────────────────────────────────────────────

    @Test
    fun deleteHabit_callsDeleteUseCaseWithCorrectHabit() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()
        val habitToDelete = habit(id = 5, name = "Exercise")

        // WHEN
        viewModel.deleteHabit(habitToDelete)

        // THEN
        coVerify { deleteHabit(habitToDelete) }
    }

    // ── toggleBooleanHabitOnDate ──────────────────────────────────────────────

    @Test
    fun toggleBooleanHabitOnDate_callsToggleEntryUseCase_withCorrectArguments() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()
        val date = LocalDate.now().minusDays(2)

        // WHEN
        viewModel.toggleBooleanHabitOnDate(habitId = 3, date = date)

        // THEN
        coVerify { toggleEntry(3, date) }
    }

    // ── Numeric Habits Operations ─────────────────────────────────────────────

    @Test
    fun incrementNumericHabit_callsIncrementNumericValueUseCase() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()
        val date = LocalDate.now()

        // WHEN
        viewModel.incrementNumericHabit(habitId = 1, date = date, currentValue = 5f, step = 1f)

        // THEN
        coVerify { incrementNumericValue(1, date, 5f, 1f) }
    }

    @Test
    fun decrementNumericHabit_callsDecrementNumericValueUseCase() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()
        val date = LocalDate.now()

        // WHEN
        viewModel.decrementNumericHabit(habitId = 2, date = date, currentValue = 10f, step = 2f)

        // THEN
        coVerify { decrementNumericValue(2, date, 10f, 2f) }
    }

    @Test
    fun setNumericValue_callsLogNumericEntryUseCase() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()
        val date = LocalDate.now()

        // WHEN
        viewModel.setNumericValue(habitId = 3, date = date, value = 15.5f)

        // THEN
        coVerify { logNumericEntry(3, date, 15.5f) }
    }

    @Test
    fun deleteNumericEntry_callsDeleteNumericEntryUseCase() = runTest {
        // GIVEN
        coEvery { getHabitsWithStatus() } returns flowOf(emptyList())
        coEvery { getAllBooleanEntries() } returns flowOf(emptyList())
        viewModel = buildViewModel()
        val date = LocalDate.now()

        // WHEN
        viewModel.deleteNumericEntry(habitId = 4, date = date)

        // THEN
        coVerify { deleteNumericEntry(4, date) }
    }
}