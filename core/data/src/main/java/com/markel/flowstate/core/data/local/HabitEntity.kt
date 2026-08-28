package com.markel.flowstate.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val iconName: String = "self_improvement",
    val colorArgb: Int = 0xFF6650A4.toInt(),
    val createdAt: Long = System.currentTimeMillis(),
    val habitType: String = "BOOLEAN",
    val unit: String? = null,
    val targetValue: Float? = null,
    val step: Float = 1f,
    val position: Int = 0,
    @ColumnInfo(defaultValue = HabitConverters.ALL_DAYS)
    val scheduledDays: Set<DayOfWeek> = DayOfWeek.entries.toSet()
)

@Entity(
    tableName = "habit_entries",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["habitId"])
    ]
)
data class HabitEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val habitId: Int,
    val completedAt: Long
)

data class HabitEntryFlatEntity(val habitId: Int, val epochDay: Long)

@Entity(
    tableName = "habit_numeric_entries",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["habitId"])]
)
data class HabitNumericEntryEntity(
    val habitId: Int,
    val epochDay: Long,
    val value: Float
)