package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.local.HabitDao
import com.markel.flowstate.core.data.local.HabitEntity
import com.markel.flowstate.core.data.local.HabitNumericEntryEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitRepositoryImplTest {

    private val dao: HabitDao = mockk(relaxed = true)
    private val repository = HabitRepositoryImpl(dao)
    private val monday = LocalDate.of(2026, 8, 17)

    @Test
    fun toggleEntry_onScheduledDay_togglesDaoEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.MONDAY)
        )

        repository.toggleEntry(1, monday)

        coVerify { dao.toggleEntry(1, monday.toEpochDay()) }
    }

    @Test
    fun toggleEntry_onUnscheduledDay_doesNotToggleDaoEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.WEDNESDAY)
        )

        repository.toggleEntry(1, monday)

        coVerify(exactly = 0) { dao.toggleEntry(any(), any()) }
    }

    @Test
    fun logNumericEntry_onScheduledDay_upsertsEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.MONDAY)
        )

        repository.logNumericEntry(1, monday, 5f)

        coVerify {
            dao.upsertNumericEntry(
                HabitNumericEntryEntity(
                    habitId = 1,
                    epochDay = monday.toEpochDay(),
                    value = 5f
                )
            )
        }
    }

    @Test
    fun logNumericEntry_onUnscheduledDay_doesNotUpsertEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.WEDNESDAY)
        )

        repository.logNumericEntry(1, monday, 5f)

        coVerify(exactly = 0) { dao.upsertNumericEntry(any()) }
    }

    private fun habitEntity(scheduledDays: Set<DayOfWeek>) = HabitEntity(
        id = 1,
        name = "Exercise",
        scheduledDays = scheduledDays
    )
}
