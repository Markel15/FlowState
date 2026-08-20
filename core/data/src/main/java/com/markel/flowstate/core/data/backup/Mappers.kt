package com.markel.flowstate.core.data.backup

import com.markel.flowstate.core.data.local.*

private val habitConverters = HabitConverters()

// ── Task ──────────────────────────────────────────────────────────────

fun TaskEntity.toSchema() = TaskSchema(
    id = id,
    title = title,
    description = description,
    isDone = isDone,
    position = position,
    priority = priority,
    dueDate = dueDate,
    completedAt = completedAt,
    reminderTime = reminderTime,
    categoryId = categoryId
)

fun TaskSchema.toEntity() = TaskEntity(
    id = id,
    title = title,
    description = description,
    isDone = isDone,
    position = position,
    priority = priority,
    dueDate = dueDate,
    completedAt = completedAt,
    reminderTime = reminderTime,
    categoryId = categoryId
)

// ── SubTask ───────────────────────────────────────────────────────────

fun SubTaskEntity.toSchema() = SubTaskSchema(
    id = id,
    taskId = taskId,
    title = title,
    description = description,
    isDone = isDone,
    priority = priority,
    dueDate = dueDate,
    position = position,
    completedAt = completedAt,
    reminderTime = reminderTime
)

fun SubTaskSchema.toEntity() = SubTaskEntity(
    id = id,
    taskId = taskId,
    title = title,
    description = description,
    isDone = isDone,
    priority = priority,
    dueDate = dueDate,
    position = position,
    completedAt = completedAt,
    reminderTime = reminderTime
)

// ── Idea ──────────────────────────────────────────────────────────────

fun IdeaEntity.toSchema() = IdeaSchema(
    id = id,
    title = title,
    content = content,
    createdAt = createdAt,
    color = color,
    position = position,
    categoryId = categoryId
)

fun IdeaSchema.toEntity() = IdeaEntity(
    id = id,
    title = title,
    content = content,
    createdAt = createdAt,
    color = color,
    position = position,
    categoryId = categoryId
)

// ── Checklist ─────────────────────────────────────────────────────────

fun CheckListEntity.toSchema() = CheckListSchema(
    id = id,
    title = title,
    color = color,
    position = position,
    categoryId = categoryId
)

fun CheckListSchema.toEntity() = CheckListEntity(
    id = id,
    title = title,
    color = color,
    position = position,
    categoryId = categoryId
)

fun CheckListItemEntity.toSchema() = CheckListItemSchema(
    id = id,
    listId = listId,
    text = text,
    isDone = isDone,
    position = position
)

fun CheckListItemSchema.toEntity() = CheckListItemEntity(
    id = id,
    listId = listId,
    text = text,
    isDone = isDone,
    position = position
)

// ── Habit ─────────────────────────────────────────────────────────────

fun HabitEntity.toSchema() = HabitSchema(
    id = id,
    name = name,
    iconName = iconName,
    colorArgb = colorArgb,
    createdAt = createdAt,
    habitType = habitType,
    unit = unit,
    targetValue = targetValue,
    step = step,
    position = position,
    scheduledDays = habitConverters.fromScheduledDays(scheduledDays)
)

fun HabitSchema.toEntity() = HabitEntity(
    id = id,
    name = name,
    iconName = iconName,
    colorArgb = colorArgb,
    createdAt = createdAt,
    habitType = habitType,
    unit = unit,
    targetValue = targetValue,
    step = step,
    position = position,
    scheduledDays = habitConverters.toScheduledDays(scheduledDays)
)

fun HabitEntryEntity.toSchema() = HabitEntrySchema(
    id = id,
    habitId = habitId,
    completedAt = completedAt
)

fun HabitEntrySchema.toEntity() = HabitEntryEntity(
    id = id,
    habitId = habitId,
    completedAt = completedAt
)

fun HabitNumericEntryEntity.toSchema() = HabitNumericEntrySchema(
    habitId = habitId,
    epochDay = epochDay,
    value = value
)

fun HabitNumericEntrySchema.toEntity() = HabitNumericEntryEntity(
    habitId = habitId,
    epochDay = epochDay,
    value = value
)

// ── Category ───────────────────────────────────────────────────────

fun CategoryEntity.toSchema() = CategorySchema(
    id = id,
    name = name,
    position = position
)

fun CategorySchema.toEntity() = CategoryEntity(
    id = id,
    name = name,
    position = position
)
