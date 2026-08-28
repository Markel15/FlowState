package com.markel.flowstate.core.data.backup

import com.markel.flowstate.core.data.local.HabitConverters
import kotlinx.serialization.Serializable

/**
 * Top-level schema for a FlowState backup file.
 *
 * Every export writes one [FlowStateExport] as JSON; every restore reads one
 * back and validates [schemaVersion] before applying changes.
 */
@Serializable
data class FlowStateExport(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportTimestamp: Long = System.currentTimeMillis(),
    val tasks: List<TaskSchema>,
    val subTasks: List<SubTaskSchema>,
    val ideas: List<IdeaSchema>,
    val checkLists: List<CheckListSchema>,
    val checkListItems: List<CheckListItemSchema>,
    val habits: List<HabitSchema>,
    val habitEntries: List<HabitEntrySchema>,
    val habitNumericEntries: List<HabitNumericEntrySchema>,
    val categories: List<CategorySchema> = emptyList()
) {
    companion object {
        /** Must match the Room database version so restores stay consistent. */
        const val CURRENT_SCHEMA_VERSION = 19
    }
}

// ── Task ──────────────────────────────────────────────────────────────

@Serializable
data class TaskSchema(
    val id: Int,
    val title: String,
    val description: String,
    val isDone: Boolean,
    val position: Int,
    val priority: Int,
    val dueDate: Long? = null,
    val completedAt: Long? = null,
    val reminderTime: Long? = null,
    val categoryId: Int? = null
)

@Serializable
data class SubTaskSchema(
    val id: String,
    val taskId: Int,
    val title: String,
    val description: String,
    val isDone: Boolean,
    val priority: Int,
    val dueDate: Long? = null,
    val position: Int,
    val completedAt: Long? = null,
    val reminderTime: Long? = null
)

// ── Idea ──────────────────────────────────────────────────────────────

@Serializable
data class IdeaSchema(
    val id: Int,
    val title: String,
    val content: String,
    val createdAt: Long,
    val color: Long,
    val position: Int,
    val categoryId: Int? = null
)

// ── Checklist ─────────────────────────────────────────────────────────

@Serializable
data class CheckListSchema(
    val id: Int,
    val title: String,
    val color: Long,
    val position: Int,
    val categoryId: Int? = null
)

@Serializable
data class CheckListItemSchema(
    val id: String,
    val listId: Int,
    val text: String,
    val isDone: Boolean,
    val position: Int
)

// ── Habit ─────────────────────────────────────────────────────────────

@Serializable
data class HabitSchema(
    val id: Int,
    val name: String,
    val iconName: String,
    val colorArgb: Int,
    val createdAt: Long,
    val habitType: String,
    val unit: String? = null,
    val targetValue: Float? = null,
    val step: Float,
    val position: Int,
    val scheduledDays: String = HabitConverters.ALL_DAYS
)

@Serializable
data class HabitEntrySchema(
    val id: Int,
    val habitId: Int,
    val completedAt: Long
)

@Serializable
data class HabitNumericEntrySchema(
    val habitId: Int,
    val epochDay: Long,
    val value: Float
)

// ── Category ───────────────────────────────────────────────────────

@Serializable
data class CategorySchema(
    val id: Int,
    val name: String,
    val position: Int
)