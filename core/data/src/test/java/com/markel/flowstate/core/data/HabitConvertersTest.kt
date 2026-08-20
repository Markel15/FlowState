package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.local.HabitConverters
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class HabitConvertersTest {

    private val converters = HabitConverters()

    @Test
    fun fromScheduledDays_ordersDaysFromMondayToSunday() {
        val days = setOf(
            DayOfWeek.FRIDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.WEDNESDAY
        )

        assertEquals(
            "MONDAY,WEDNESDAY,FRIDAY",
            converters.fromScheduledDays(days)
        )
    }

    @Test
    fun scheduledDays_roundTrip_preservesSelection() {
        val days = setOf(
            DayOfWeek.TUESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.SUNDAY
        )

        val restored = converters.toScheduledDays(
            converters.fromScheduledDays(days)
        )

        assertEquals(days, restored)
    }

    @Test
    fun emptySelection_roundTrip_isSupported() {
        assertEquals(emptySet<DayOfWeek>(), converters.toScheduledDays(""))
        assertEquals("", converters.fromScheduledDays(emptySet()))
    }
}
