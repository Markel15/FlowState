package com.markel.flowstate.core.data.local

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Esta clase no es una tabla. Es un resultado de una consulta (JOIN implicito).
 * Room rellenará esto automáticamente.
 */
data class TaskWithSubTasks(
    @Embedded val task: TaskEntity,

    @Relation(
        parentColumn = "id", // ID en TaskEntity
        entityColumn = "taskId" // ID en SubTaskEntity
    )
    val subTasks: List<SubTaskEntity>
)