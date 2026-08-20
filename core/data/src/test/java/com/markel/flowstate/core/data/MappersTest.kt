package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.backup.CheckListItemSchema
import com.markel.flowstate.core.data.backup.CheckListSchema
import com.markel.flowstate.core.data.backup.HabitEntrySchema
import com.markel.flowstate.core.data.backup.HabitNumericEntrySchema
import com.markel.flowstate.core.data.backup.HabitSchema
import com.markel.flowstate.core.data.backup.IdeaSchema
import com.markel.flowstate.core.data.backup.SubTaskSchema
import com.markel.flowstate.core.data.backup.TaskSchema
import com.markel.flowstate.core.data.backup.toEntity
import com.markel.flowstate.core.data.backup.toSchema
import com.markel.flowstate.core.data.local.CheckListEntity
import com.markel.flowstate.core.data.local.CheckListItemEntity
import com.markel.flowstate.core.data.local.HabitEntryEntity
import com.markel.flowstate.core.data.local.HabitEntity
import com.markel.flowstate.core.data.local.HabitNumericEntryEntity
import com.markel.flowstate.core.data.local.IdeaEntity
import com.markel.flowstate.core.data.local.SubTaskEntity
import com.markel.flowstate.core.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

/**
 * Unit tests for the bidirectional mapper extension functions in [Mappers.kt].
 *
 * These mappers are the connection between Room entities (database layer) and
 * [ExportSchema] classes (serialization layer). If a field is added to an
 * Entity but forgotten in the Schema or the Mapper, **these tests will fail**,
 * catching the desynchronization early — before it reaches production backup/restore.
 *
 * Test strategy per entity type:
 *   1. toSchema()  — every field of the Entity appears in the resulting Schema
 *   2. toEntity()  — every field of the Schema appears in the resulting Entity
 *   3. Round-trip  — entity.toSchema().toEntity() equals the original entity
 *   4. Nullable    — null vs. non-null values survive the round-trip
 */
class MappersTest {

    // ═══════════════════════════════════════════════════════════════════
    //  TASK
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun taskEntity_toSchema_mapsAllFields() {
        val entity = TaskEntity(
            id = 42,
            title = "Finish report",
            description = "Q4 results",
            isDone = true,
            position = 3,
            priority = 2,
            dueDate = 1700000000000L,
            completedAt = 1700099999999L,
            reminderTime = 1700000001000L
        )

        val schema = entity.toSchema()

        assertEquals(entity.id, schema.id)
        assertEquals(entity.title, schema.title)
        assertEquals(entity.description, schema.description)
        assertEquals(entity.isDone, schema.isDone)
        assertEquals(entity.position, schema.position)
        assertEquals(entity.priority, schema.priority)
        assertEquals(entity.dueDate, schema.dueDate)
        assertEquals(entity.completedAt, schema.completedAt)
        assertEquals(entity.reminderTime, schema.reminderTime)
    }

    @Test
    fun taskSchema_toEntity_mapsAllFields() {
        val schema = TaskSchema(
            id = 42,
            title = "Finish report",
            description = "Q4 results",
            isDone = true,
            position = 3,
            priority = 2,
            dueDate = 1700000000000L,
            completedAt = 1700099999999L,
            reminderTime = 1700000001000L
        )

        val entity = schema.toEntity()

        assertEquals(schema.id, entity.id)
        assertEquals(schema.title, entity.title)
        assertEquals(schema.description, entity.description)
        assertEquals(schema.isDone, entity.isDone)
        assertEquals(schema.position, entity.position)
        assertEquals(schema.priority, entity.priority)
        assertEquals(schema.dueDate, entity.dueDate)
        assertEquals(schema.completedAt, entity.completedAt)
        assertEquals(schema.reminderTime, entity.reminderTime)
    }

    @Test
    fun taskEntity_roundTrip_preservesAllFields() {
        val original = TaskEntity(
            id = 1, title = "Task", description = "Desc",
            isDone = false, position = 0, priority = 1,
            dueDate = 100L, completedAt = 200L, reminderTime = 300L
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    @Test
    fun taskEntity_roundTrip_withNullablesNull_preservesAllFields() {
        val original = TaskEntity(
            id = 5, title = "No dates", description = "",
            isDone = false, position = 0, priority = 0,
            dueDate = null, completedAt = null, reminderTime = null
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
        assertNull(roundTripped.dueDate)
        assertNull(roundTripped.completedAt)
        assertNull(roundTripped.reminderTime)
    }

    @Test
    fun taskSchema_roundTrip_preservesAllFields() {
        val original = TaskSchema(
            id = 99, title = "Schema RT", description = "Test",
            isDone = true, position = 7, priority = 3,
            dueDate = 500L, completedAt = 600L, reminderTime = 700L
        )
        val roundTripped = original.toEntity().toSchema()
        assertEquals(original, roundTripped)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  SUBTASK
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun subTaskEntity_toSchema_mapsAllFields() {
        val entity = SubTaskEntity(
            id = "sub-abc-123",
            taskId = 10,
            title = "Buy eggs",
            description = "Free range",
            isDone = false,
            priority = 1,
            dueDate = 1700000000000L,
            position = 2,
            completedAt = 1700099999999L,
            reminderTime = 1700000005000L
        )

        val schema = entity.toSchema()

        assertEquals(entity.id, schema.id)
        assertEquals(entity.taskId, schema.taskId)
        assertEquals(entity.title, schema.title)
        assertEquals(entity.description, schema.description)
        assertEquals(entity.isDone, schema.isDone)
        assertEquals(entity.priority, schema.priority)
        assertEquals(entity.dueDate, schema.dueDate)
        assertEquals(entity.position, schema.position)
        assertEquals(entity.completedAt, schema.completedAt)
        assertEquals(entity.reminderTime, schema.reminderTime)
    }

    @Test
    fun subTaskSchema_toEntity_mapsAllFields() {
        val schema = SubTaskSchema(
            id = "sub-xyz-789",
            taskId = 20,
            title = "Read book",
            description = "Chapter 5",
            isDone = true,
            priority = 0,
            dueDate = null,
            position = 1,
            completedAt = null,
            reminderTime = null
        )

        val entity = schema.toEntity()

        assertEquals(schema.id, entity.id)
        assertEquals(schema.taskId, entity.taskId)
        assertEquals(schema.title, entity.title)
        assertEquals(schema.description, entity.description)
        assertEquals(schema.isDone, entity.isDone)
        assertEquals(schema.priority, entity.priority)
        assertEquals(schema.dueDate, entity.dueDate)
        assertEquals(schema.position, entity.position)
        assertEquals(schema.completedAt, entity.completedAt)
        assertEquals(schema.reminderTime, entity.reminderTime)
    }

    @Test
    fun subTaskEntity_roundTrip_preservesAllFields() {
        val original = SubTaskEntity(
            id = "rt-sub-1", taskId = 5, title = "RT Sub",
            description = "Desc", isDone = true, priority = 2,
            dueDate = 1000L, position = 4, completedAt = 2000L,
            reminderTime = 3000L
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    @Test
    fun subTaskEntity_roundTrip_withNullablesNull_preservesAllFields() {
        val original = SubTaskEntity(
            id = "rt-sub-null", taskId = 1, title = "Null sub",
            description = "", isDone = false, priority = 0,
            dueDate = null, position = 0, completedAt = null,
            reminderTime = null
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
        assertNull(roundTripped.dueDate)
        assertNull(roundTripped.completedAt)
        assertNull(roundTripped.reminderTime)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  IDEA
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun ideaEntity_toSchema_mapsAllFields() {
        val entity = IdeaEntity(
            id = 7,
            title = "Startup idea",
            content = "AI-powered journaling",
            createdAt = 1700000000000L,
            color = 0xFF579D42,
            position = 5
        )

        val schema = entity.toSchema()

        assertEquals(entity.id, schema.id)
        assertEquals(entity.title, schema.title)
        assertEquals(entity.content, schema.content)
        assertEquals(entity.createdAt, schema.createdAt)
        assertEquals(entity.color, schema.color)
        assertEquals(entity.position, schema.position)
    }

    @Test
    fun ideaSchema_toEntity_mapsAllFields() {
        val schema = IdeaSchema(
            id = 8,
            title = "Book note",
            content = "Chapter summary",
            createdAt = 1700099999999L,
            color = 0xFF6650A4,
            position = 3
        )

        val entity = schema.toEntity()

        assertEquals(schema.id, entity.id)
        assertEquals(schema.title, entity.title)
        assertEquals(schema.content, entity.content)
        assertEquals(schema.createdAt, entity.createdAt)
        assertEquals(schema.color, entity.color)
        assertEquals(schema.position, entity.position)
    }

    @Test
    fun ideaEntity_roundTrip_preservesAllFields() {
        val original = IdeaEntity(
            id = 15, title = "RT Idea", content = "Content",
            createdAt = 999L, color = 0xFFFFFFFF, position = 10
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    @Test
    fun ideaSchema_roundTrip_preservesAllFields() {
        val original = IdeaSchema(
            id = 20, title = "RT Schema", content = "More content",
            createdAt = 1234L, color = 0x00000000, position = 0
        )
        val roundTripped = original.toEntity().toSchema()
        assertEquals(original, roundTripped)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CHECKLIST
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun checkListEntity_toSchema_mapsAllFields() {
        val entity = CheckListEntity(
            id = 3,
            title = "Travel prep",
            color = 0xFFABCDEF,
            position = 2
        )

        val schema = entity.toSchema()

        assertEquals(entity.id, schema.id)
        assertEquals(entity.title, schema.title)
        assertEquals(entity.color, schema.color)
        assertEquals(entity.position, schema.position)
    }

    @Test
    fun checkListSchema_toEntity_mapsAllFields() {
        val schema = CheckListSchema(
            id = 4,
            title = "Groceries",
            color = 0x00FFFFFF,
            position = 1
        )

        val entity = schema.toEntity()

        assertEquals(schema.id, entity.id)
        assertEquals(schema.title, entity.title)
        assertEquals(schema.color, entity.color)
        assertEquals(schema.position, entity.position)
    }

    @Test
    fun checkListEntity_roundTrip_preservesAllFields() {
        val original = CheckListEntity(
            id = 50, title = "RT List", color = 0L, position = 0
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    @Test
    fun checkListSchema_roundTrip_preservesAllFields() {
        val original = CheckListSchema(
            id = 60, title = "RT Schema List", color = 999L, position = 7
        )
        val roundTripped = original.toEntity().toSchema()
        assertEquals(original, roundTripped)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CHECKLIST ITEM
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun checkListItemEntity_toSchema_mapsAllFields() {
        val entity = CheckListItemEntity(
            id = "item-abc",
            listId = 3,
            text = "Passport",
            isDone = true,
            position = 0
        )

        val schema = entity.toSchema()

        assertEquals(entity.id, schema.id)
        assertEquals(entity.listId, schema.listId)
        assertEquals(entity.text, schema.text)
        assertEquals(entity.isDone, schema.isDone)
        assertEquals(entity.position, schema.position)
    }

    @Test
    fun checkListItemSchema_toEntity_mapsAllFields() {
        val schema = CheckListItemSchema(
            id = "item-xyz",
            listId = 5,
            text = "Charger",
            isDone = false,
            position = 3
        )

        val entity = schema.toEntity()

        assertEquals(schema.id, entity.id)
        assertEquals(schema.listId, entity.listId)
        assertEquals(schema.text, entity.text)
        assertEquals(schema.isDone, entity.isDone)
        assertEquals(schema.position, entity.position)
    }

    @Test
    fun checkListItemEntity_roundTrip_preservesAllFields() {
        val original = CheckListItemEntity(
            id = "rt-item-1", listId = 10, text = "RT Item",
            isDone = false, position = 5
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    @Test
    fun checkListItemSchema_roundTrip_preservesAllFields() {
        val original = CheckListItemSchema(
            id = "rt-item-schema", listId = 99, text = "RT Schema",
            isDone = true, position = 0
        )
        val roundTripped = original.toEntity().toSchema()
        assertEquals(original, roundTripped)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HABIT
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun habitEntity_toSchema_mapsAllFields() {
        val entity = HabitEntity(
            id = 1,
            name = "Running",
            iconName = "directions_run",
            colorArgb = 0xFFE91E63.toInt(),
            createdAt = 1700000000000L,
            habitType = "NUMERIC",
            unit = "km",
            targetValue = 5.0f,
            step = 0.5f,
            position = 2,
            scheduledDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
            )
        )

        val schema = entity.toSchema()

        assertEquals(entity.id, schema.id)
        assertEquals(entity.name, schema.name)
        assertEquals(entity.iconName, schema.iconName)
        assertEquals(entity.colorArgb, schema.colorArgb)
        assertEquals(entity.createdAt, schema.createdAt)
        assertEquals(entity.habitType, schema.habitType)
        assertEquals(entity.unit, schema.unit)
        assertEquals(entity.step, schema.step, 0.001f)
        assertEquals(entity.position, schema.position)
        assertEquals("MONDAY,WEDNESDAY,FRIDAY", schema.scheduledDays)
    }

    @Test
    fun habitSchema_toEntity_mapsAllFields() {
        val schema = HabitSchema(
            id = 2,
            name = "Reading",
            iconName = "menu_book",
            colorArgb = 0xFF4CAF50.toInt(),
            createdAt = 1700099999999L,
            habitType = "BOOLEAN",
            unit = null,
            targetValue = null,
            step = 1f,
            position = 0,
            scheduledDays = "TUESDAY,THURSDAY"
        )

        val entity = schema.toEntity()

        assertEquals(schema.id, entity.id)
        assertEquals(schema.name, entity.name)
        assertEquals(schema.iconName, entity.iconName)
        assertEquals(schema.colorArgb, entity.colorArgb)
        assertEquals(schema.createdAt, entity.createdAt)
        assertEquals(schema.habitType, entity.habitType)
        assertEquals(schema.unit, entity.unit)
        assertEquals(schema.step, entity.step, 0.001f)
        assertEquals(schema.position, entity.position)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), entity.scheduledDays)
    }

    @Test
    fun habitEntity_roundTrip_withAllFieldsPopulated() {
        val original = HabitEntity(
            id = 10, name = "Yoga", iconName = "self_improvement",
            colorArgb = 0xFF9C27B0.toInt(),
            createdAt = 5000L, habitType = "NUMERIC",
            unit = "min", targetValue = 30f, step = 5f, position = 8
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    @Test
    fun habitEntity_roundTrip_withNullablesNull_preservesAllFields() {
        val original = HabitEntity(
            id = 11, name = "Meditate", iconName = "spa",
            colorArgb = 0xFF6650A4.toInt(),
            createdAt = 6000L, habitType = "BOOLEAN",
            unit = null, targetValue = null, step = 1f, position = 0
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
        assertNull(roundTripped.unit)
        assertNull(roundTripped.targetValue)
    }

    @Test
    fun habitSchema_roundTrip_preservesAllFields() {
        val original = HabitSchema(
            id = 30, name = "Push-ups", iconName = "fitness_center",
            colorArgb = 0xFFF44336.toInt(),
            createdAt = 7777L, habitType = "NUMERIC",
            unit = "reps", targetValue = 50f, step = 10f, position = 3
        )
        val roundTripped = original.toEntity().toSchema()
        assertEquals(original, roundTripped)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HABIT ENTRY
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun habitEntryEntity_toSchema_mapsAllFields() {
        val entity = HabitEntryEntity(
            id = 100,
            habitId = 5,
            completedAt = 1700000000000L
        )

        val schema = entity.toSchema()

        assertEquals(entity.id, schema.id)
        assertEquals(entity.habitId, schema.habitId)
        assertEquals(entity.completedAt, schema.completedAt)
    }

    @Test
    fun habitEntrySchema_toEntity_mapsAllFields() {
        val schema = HabitEntrySchema(
            id = 200,
            habitId = 10,
            completedAt = 1700099999999L
        )

        val entity = schema.toEntity()

        assertEquals(schema.id, entity.id)
        assertEquals(schema.habitId, entity.habitId)
        assertEquals(schema.completedAt, entity.completedAt)
    }

    @Test
    fun habitEntryEntity_roundTrip_preservesAllFields() {
        val original = HabitEntryEntity(
            id = 55, habitId = 3, completedAt = 8888L
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    @Test
    fun habitEntrySchema_roundTrip_preservesAllFields() {
        val original = HabitEntrySchema(
            id = 66, habitId = 7, completedAt = 9999L
        )
        val roundTripped = original.toEntity().toSchema()
        assertEquals(original, roundTripped)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HABIT NUMERIC ENTRY (composite primary key: habitId + epochDay)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun habitNumericEntryEntity_toSchema_mapsAllFields() {
        val entity = HabitNumericEntryEntity(
            habitId = 3,
            epochDay = 19700L,
            value = 7.5f
        )

        val schema = entity.toSchema()

        assertEquals(entity.habitId, schema.habitId)
        assertEquals(entity.epochDay, schema.epochDay)
        assertEquals(entity.value, schema.value, 0.001f)
    }

    @Test
    fun habitNumericEntrySchema_toEntity_mapsAllFields() {
        val schema = HabitNumericEntrySchema(
            habitId = 4,
            epochDay = 19800L,
            value = 12.0f
        )

        val entity = schema.toEntity()

        assertEquals(schema.habitId, entity.habitId)
        assertEquals(schema.epochDay, entity.epochDay)
        assertEquals(schema.value, entity.value, 0.001f)
    }

    @Test
    fun habitNumericEntryEntity_roundTrip_preservesAllFields() {
        val original = HabitNumericEntryEntity(
            habitId = 99, epochDay = 20000L, value = 3.14f
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    @Test
    fun habitNumericEntrySchema_roundTrip_preservesAllFields() {
        val original = HabitNumericEntrySchema(
            habitId = 88, epochDay = 20100L, value = 2.71f
        )
        val roundTripped = original.toEntity().toSchema()
        assertEquals(original, roundTripped)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun taskEntity_withZeroAndNegativeValues_roundTrips() {
        // Position and priority are Int — verify zero and edge values
        val original = TaskEntity(
            id = 0, title = "", description = "",
            isDone = false, position = 0, priority = 0,
            dueDate = 0L, completedAt = 0L, reminderTime = 0L
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    @Test
    fun habitEntity_withZeroStep_roundTrips() {
        // Step is Float — verify zero step doesn't get lost
        val original = HabitEntity(
            id = 0, name = "Zero step", iconName = "",
            colorArgb = 0,
            createdAt = 0L, habitType = "",
            unit = "", targetValue = 0f, step = 0f, position = 0
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    @Test
    fun habitNumericEntry_withZeroValue_roundTrips() {
        val original = HabitNumericEntryEntity(
            habitId = 0, epochDay = 0L, value = 0f
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    @Test
    fun subTaskEntity_withEmptyStrings_roundTrips() {
        val original = SubTaskEntity(
            id = "", taskId = 0, title = "", description = "",
            isDone = false, priority = 0, dueDate = null,
            position = 0, completedAt = null, reminderTime = null
        )
        val roundTripped = original.toSchema().toEntity()
        assertEquals(original, roundTripped)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FULL ROUND-TRIP: Entity → Schema → JSON → Schema → Entity
    //  (validates that mappers + serialization work together correctly)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun taskEntity_fullRoundTrip_viaJson_preservesAllFields() {
        val original = TaskEntity(
            id = 1, title = "Full RT Task", description = "Via JSON",
            isDone = true, position = 5, priority = 3,
            dueDate = 1700000000000L, completedAt = 1700050000000L,
            reminderTime = 1700000010000L
        )
        val schema = original.toSchema()
        val json = kotlinx.serialization.json.Json.encodeToString(TaskSchema.serializer(), schema)
        val parsedSchema = kotlinx.serialization.json.Json.decodeFromString(TaskSchema.serializer(), json)
        val roundTripped = parsedSchema.toEntity()

        assertEquals(original, roundTripped)
    }

    @Test
    fun habitEntity_fullRoundTrip_viaJson_preservesNullableFields() {
        val original = HabitEntity(
            id = 1, name = "Full RT Habit", iconName = "spa",
            colorArgb = 0xFF6650A4.toInt(),
            createdAt = 1700000000000L, habitType = "BOOLEAN",
            unit = null, targetValue = null, step = 1f, position = 0
        )
        val schema = original.toSchema()
        val json = kotlinx.serialization.json.Json.encodeToString(HabitSchema.serializer(), schema)
        val parsedSchema = kotlinx.serialization.json.Json.decodeFromString(HabitSchema.serializer(), json)
        val roundTripped = parsedSchema.toEntity()

        assertEquals(original, roundTripped)
        assertNull(roundTripped.unit)
        assertNull(roundTripped.targetValue)
    }

    @Test
    fun habitEntity_fullRoundTrip_viaJson_withNumericFields_preservesAllFields() {
        val original = HabitEntity(
            id = 2, name = "Water intake", iconName = "water_drop",
            colorArgb = 0xFF2196F3.toInt(),
            createdAt = 1700000000000L, habitType = "NUMERIC",
            unit = "ml", targetValue = 2000f, step = 250f, position = 1
        )
        val schema = original.toSchema()
        val json = kotlinx.serialization.json.Json.encodeToString(HabitSchema.serializer(), schema)
        val parsedSchema = kotlinx.serialization.json.Json.decodeFromString(HabitSchema.serializer(), json)
        val roundTripped = parsedSchema.toEntity()

        assertEquals(original, roundTripped)
        assertNotNull(roundTripped.unit)
        assertNotNull(roundTripped.targetValue)
    }

    @Test
    fun habitNumericEntry_fullRoundTrip_viaJson_preservesAllFields() {
        val original = HabitNumericEntryEntity(
            habitId = 5, epochDay = 19750L, value = 1500f
        )
        val schema = original.toSchema()
        val json = kotlinx.serialization.json.Json.encodeToString(
            HabitNumericEntrySchema.serializer(), schema
        )
        val parsedSchema = kotlinx.serialization.json.Json.decodeFromString(
            HabitNumericEntrySchema.serializer(), json
        )
        val roundTripped = parsedSchema.toEntity()

        assertEquals(original, roundTripped)
    }
}
