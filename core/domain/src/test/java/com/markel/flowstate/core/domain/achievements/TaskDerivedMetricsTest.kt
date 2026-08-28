package com.markel.flowstate.core.domain.achievements

import com.markel.flowstate.core.domain.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskDerivedMetricsTest {

    private val zone = ZoneId.of("Europe/Madrid")

    private fun doneTask(id: Int, dateTime: LocalDateTime): Task = Task(
        id = id,
        title = "t$id",
        description = "",
        isDone = true,
        completedAt = dateTime.atZone(zone).toInstant().toEpochMilli()
    )

    @Test
    fun `night owl counts tasks between midnight and 5 am`() {
        val tasks = listOf(
            doneTask(1, LocalDateTime.of(2026, 8, 23, 0, 30)),
            doneTask(2, LocalDateTime.of(2026, 8, 23, 4, 59)),
            doneTask(3, LocalDateTime.of(2026, 8, 23, 5, 0)),  // exactly 5:00 -> no
            doneTask(4, LocalDateTime.of(2026, 8, 23, 23, 30)) // evening -> no
        )
        assertEquals(2, computeTaskDerivedMetrics(tasks, zone).nightOwlTasks)
    }

    @Test
    fun `best sunday counts the busiest single sunday`() {
        val sundayA = LocalDate.of(2026, 8, 9)  // a Sunday
        val sundayB = LocalDate.of(2026, 8, 16) // the next Sunday
        val tasks = listOf(
            doneTask(1, sundayA.atTime(10, 0)),
            doneTask(2, sundayA.atTime(12, 0)),
            doneTask(3, sundayA.atTime(18, 0)),
            doneTask(4, sundayB.atTime(10, 0)),
            doneTask(5, sundayB.atTime(20, 0)),
            doneTask(6, LocalDate.of(2026, 8, 11).atTime(10, 0)) // Tuesday
        )
        assertEquals(3, computeTaskDerivedMetrics(tasks, zone).bestSundayTasks)
    }

    @Test
    fun `tasks without completion or pending tasks are ignored`() {
        val pending = Task(id = 1, title = "p", description = "", isDone = false)
        assertEquals(0, computeTaskDerivedMetrics(listOf(pending), zone).nightOwlTasks)
        assertEquals(0, computeTaskDerivedMetrics(listOf(pending), zone).bestSundayTasks)
    }
}
