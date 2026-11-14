package com.markel.flowstate.feature.calendar

import app.cash.turbine.test
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskRepository
import com.markel.flowstate.core.domain.usecase.tasks.ToggleTaskUseCase
import com.markel.flowstate.core.notifications.ReminderScheduler
import com.markel.flowstate.core.testing.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

class CalendarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: TaskRepository = mockk(relaxed = true)
    private val toggleTaskUseCase: ToggleTaskUseCase = mockk(relaxed = true)
    private val reminderScheduler: ReminderScheduler = mockk(relaxed = true)
    private lateinit var viewModel: CalendarViewModel

    // DatePicker stores dueDate as midnight UTC of the selected calendar day
    private fun LocalDate.toDueDateMillis(): Long {
        return this.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }

    // ToggleTaskUseCase stores completedAt as an instant (System.currentTimeMillis())
    private fun LocalDateTime.toCompletedMillis(zone: ZoneId = ZoneId.systemDefault()): Long {
        return this.atZone(zone).toInstant().toEpochMilli()
    }

    @Test
    fun initialState_loadsAndGroupsTasksCorrectlyByDate() = runTest {
        // GIVEN - Test dates
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        // Create tasks for each scenario:

        // 1. Task with a Due Date (Should go to 'tomorrow')
        val taskWithDueDate = Task(
            id = 1, title = "Due Task", isDone = false,
            dueDate = tomorrow.toDueDateMillis()
        )

        // 2. Completed Task without a due date (Should go to 'today' which is when it was completed)
        val taskCompleted = Task(
            id = 2, title = "Done Task", isDone = true,
            dueDate = null,
            completedAt = today.atTime(12, 0).toCompletedMillis()
        )

        // 3. Task without date and not done (Should NOT appear in the calendar)
        val taskBacklog = Task(
            id = 3, title = "Backlog Task", isDone = false,
            dueDate = null, completedAt = null
        )

        // 4. Task with both dates (According to the logic, DueDate has preference)
        val taskHybrid = Task(
            id = 4, title = "Hybrid Task", isDone = true,
            dueDate = tomorrow.toDueDateMillis(),
            completedAt = today.atTime(12, 0).toCompletedMillis()
        )

        coEvery { repository.getTasks() } returns flowOf(
            listOf(
                taskWithDueDate,
                taskCompleted,
                taskBacklog,
                taskHybrid
            )
        )

        // WHEN
        viewModel = CalendarViewModel(repository, toggleTaskUseCase, reminderScheduler)

        // THEN
        viewModel.uiState.test {
            // Skip initial state if necessary
            val state = awaitItem()
            val successState = if (state is CalendarUiState.Loading) awaitItem() as CalendarUiState.Success else state as CalendarUiState.Success

            val map = successState.tasksByDate

            // Verify 'today'
            val tasksToday = map[today] ?: emptyList()
            Assert.assertEquals(1, tasksToday.size)
            Assert.assertEquals("Done Task", tasksToday[0].title)

            // Verify 'tomorrow'
            val tasksTomorrow = map[tomorrow] ?: emptyList()
            Assert.assertEquals(2, tasksTomorrow.size)
            // Both DueDate task and Hybrid task should be present
            Assert.assertTrue(tasksTomorrow.any { it.title == "Due Task" })
            Assert.assertTrue(tasksTomorrow.any { it.title == "Hybrid Task" })

            // Verify that the backlog task is not in the map
            val allTasksInMap = map.values.flatten()
            Assert.assertTrue(allTasksInMap.none { it.id == 3 })
        }
    }

    @Test
    fun onDateSelected_updatesSelectedDateInUiState() = runTest {
        coEvery { repository.getTasks() } returns flowOf(emptyList())
        viewModel = CalendarViewModel(repository, toggleTaskUseCase, reminderScheduler)

        val randomDate = LocalDate.of(2025, 12, 31)

        viewModel.uiState.test {
            // Initial state (today)
            val initialState = awaitItem() as CalendarUiState.Success
            Assert.assertEquals(LocalDate.now(), initialState.selectedDate)

            // Action
            viewModel.onDateSelected(randomDate)

            // New state
            val newState = awaitItem() as CalendarUiState.Success
            Assert.assertEquals(randomDate, newState.selectedDate)
        }
    }

    @Test
    fun toggleTaskDone_callsToggleUseCaseAndCancelsAlarm() = runTest {
        coEvery { repository.getTasks() } returns flowOf(emptyList())
        viewModel = CalendarViewModel(repository, toggleTaskUseCase, reminderScheduler)

        val task = Task(id = 1, title = "Test", isDone = false)

        viewModel.toggleTaskDone(task)

        coVerify { toggleTaskUseCase(task) }
        coVerify { reminderScheduler.cancel(1) }
    }

    @Test
    fun uiState_completedAfterMidnightInPositiveUtcOffset_groupsUnderLocalDate() = runTest {
        val previousTimeZone = TimeZone.getDefault()
        val madrid = ZoneId.of("Europe/Madrid")
        TimeZone.setDefault(TimeZone.getTimeZone(madrid))
        try {
            // Winter (UTC+1): 2026-11-14 00:20 local == 2026-11-13 23:20 UTC
            val winterLocalDate = LocalDate.of(2026, 11, 14)
            val winterTask = Task(
                id = 10,
                title = "Night task (winter)",
                isDone = true,
                dueDate = null,
                completedAt = LocalDateTime.of(2026, 11, 14, 0, 20).toCompletedMillis(madrid)
            )

            // Summer (UTC+2): 2026-07-15 01:30 local == 2026-07-14 23:30 UTC
            val summerLocalDate = LocalDate.of(2026, 7, 15)
            val summerTask = Task(
                id = 11,
                title = "Night task (summer)",
                isDone = true,
                dueDate = null,
                completedAt = LocalDateTime.of(2026, 7, 15, 1, 30).toCompletedMillis(madrid)
            )

            coEvery { repository.getTasks() } returns flowOf(listOf(winterTask, summerTask))
            viewModel = CalendarViewModel(repository, toggleTaskUseCase, reminderScheduler)

            viewModel.uiState.test {
                val state = awaitItem()
                val success = if (state is CalendarUiState.Loading) {
                    awaitItem() as CalendarUiState.Success
                } else {
                    state as CalendarUiState.Success
                }

                Assert.assertEquals(listOf(winterTask), success.tasksByDate[winterLocalDate])
                Assert.assertNull(success.tasksByDate[winterLocalDate.minusDays(1)])
                Assert.assertEquals(listOf(summerTask), success.tasksByDate[summerLocalDate])
                Assert.assertNull(success.tasksByDate[summerLocalDate.minusDays(1)])
            }
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }
}