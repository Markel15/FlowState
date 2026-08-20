package com.markel.flowstate.core.data.local

import androidx.room.TypeConverter
import java.time.DayOfWeek

/**
 * Converts the weekdays selected for a habit to the textual representation
 * stored by Room, for example: "MONDAY,WEDNESDAY,FRIDAY".
 */
class HabitConverters {

    @TypeConverter
    fun fromScheduledDays(days: Set<DayOfWeek>): String =
        days
            .sortedBy { it.value }
            .joinToString(",") { it.name }

    @TypeConverter
    fun toScheduledDays(value: String): Set<DayOfWeek> =
        if (value.isBlank()) {
            emptySet()
        } else {
            value.split(",").mapTo(linkedSetOf()) { DayOfWeek.valueOf(it) }
        }

    companion object {
        const val ALL_DAYS =
            "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY"
    }
}
