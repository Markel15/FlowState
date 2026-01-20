package com.markel.flowstate.core.data.local

import androidx.room.Embedded
import androidx.room.Relation

/**
 * This class is not a table. It is the result of a query (implicit JOIN).
 * Room will fill this automatically.
 */
data class TaskWithSubTasks(
    @Embedded val task: TaskEntity,

    @Relation(
        parentColumn = "id", // ID in TaskEntity
        entityColumn = "taskId" // ID in SubTaskEntity
    )
    val subTasks: List<SubTaskEntity>
)