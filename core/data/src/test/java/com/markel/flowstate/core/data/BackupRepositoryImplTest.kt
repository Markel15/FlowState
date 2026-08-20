package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.backup.BackupRepositoryImpl
import com.markel.flowstate.core.data.backup.FlowStateExport
import com.markel.flowstate.core.data.backup.RestoreErrorType
import com.markel.flowstate.core.data.backup.RestoreResult
import com.markel.flowstate.core.data.local.CategoryDao
import com.markel.flowstate.core.data.local.CategoryEntity
import com.markel.flowstate.core.data.local.CheckListDao
import com.markel.flowstate.core.data.local.CheckListEntity
import com.markel.flowstate.core.data.local.CheckListItemEntity
import com.markel.flowstate.core.data.local.CheckListWithItems
import com.markel.flowstate.core.data.local.HabitConverters
import com.markel.flowstate.core.data.local.HabitDao
import com.markel.flowstate.core.data.local.HabitEntryEntity
import com.markel.flowstate.core.data.local.HabitEntity
import com.markel.flowstate.core.data.local.HabitNumericEntryEntity
import com.markel.flowstate.core.data.local.HabitWithEntries
import com.markel.flowstate.core.data.local.IdeaDao
import com.markel.flowstate.core.data.local.IdeaEntity
import com.markel.flowstate.core.data.local.SubTaskEntity
import com.markel.flowstate.core.data.local.TaskDao
import com.markel.flowstate.core.data.local.TaskEntity
import com.markel.flowstate.core.data.local.TaskWithSubTasks
import com.markel.flowstate.core.domain.Category
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupRepositoryImplTest {

    // ── Mocked DAOs ────────────────────────────────────────────────────

    private val taskDao: TaskDao = mockk(relaxed = true)
    private val ideaDao: IdeaDao = mockk(relaxed = true)
    private val checkListDao: CheckListDao = mockk(relaxed = true)
    private val habitDao: HabitDao = mockk(relaxed = true)
    private val categoryDao: CategoryDao = mockk(relaxed = true)

    private lateinit var repository: BackupRepositoryImpl

    @Before
    fun setUp() {
        repository = BackupRepositoryImpl(taskDao, ideaDao, checkListDao, habitDao, categoryDao)
    }

    // ── Test data ──────────────────────────────────────────────────────

    private val sampleTask = TaskEntity(
        id = 1, title = "Buy groceries", description = "Milk and bread",
        isDone = false, position = 0, priority = 1,
        dueDate = 1700000000000L, completedAt = null, reminderTime = null
    )

    private val sampleSubTask = SubTaskEntity(
        id = "sub-001", taskId = 1, title = "Get milk", description = "",
        isDone = false, priority = 0, dueDate = null, position = 0,
        completedAt = null, reminderTime = null
    )

    private val sampleIdea = IdeaEntity(
        id = 1, title = "App idea", content = "A new productivity app",
        createdAt = 1700000000000L, color = 0xFF6650A4, position = 0
    )

    private val sampleCheckList = CheckListEntity(
        id = 1, title = "Packing list", color = 0xFF579D42, position = 0
    )

    private val sampleCheckListItem = CheckListItemEntity(
        id = "item-001", listId = 1, text = "Passport", isDone = false, position = 0
    )

    private val sampleHabit = HabitEntity(
        id = 1, name = "Meditation", iconName = "self_improvement",
        colorArgb = 0xFF6650A4.toInt(),
        createdAt = 1700000000000L, habitType = "BOOLEAN",
        unit = null, targetValue = null, step = 1f, position = 0
    )

    private val sampleHabitEntry = HabitEntryEntity(
        id = 1, habitId = 1, completedAt = 1700100000000L
    )

    private val sampleNumericEntry = HabitNumericEntryEntity(
        habitId = 2, epochDay = 19700L, value = 3.5f
    )

    private val sampleCategory = CategoryEntity(
        id = 1, name = "Work", position = 0
    )

    /** Stubs all DAO one-shot queries with sample data. */
    private fun stubDaoQueries() {
        coEvery { taskDao.getAllTasksOnce() } returns listOf(
            TaskWithSubTasks(sampleTask, listOf(sampleSubTask))
        )
        coEvery { ideaDao.getAllIdeasOnce() } returns listOf(sampleIdea)
        coEvery { checkListDao.getAllListsOnce() } returns listOf(
            CheckListWithItems(sampleCheckList, listOf(sampleCheckListItem))
        )
        coEvery { habitDao.getAllHabitsOnce() } returns listOf(
            HabitWithEntries(sampleHabit, listOf(sampleHabitEntry))
        )
        coEvery { habitDao.getAllEntriesOnce() } returns listOf(sampleHabitEntry)
        coEvery { habitDao.getAllNumericEntriesOnce() } returns listOf(sampleNumericEntry)
        coEvery { categoryDao.getAllCategoriesOnce() } returns listOf(sampleCategory)
        // When restoring, the remap logic asks the DAO to insert new categories.
        // Return a deterministic id so tests can assert against it.
        coEvery { categoryDao.upsertCategory(any()) } returns 100L
    }

    // ═══════════════════════════════════════════════════════════════════
    //  EXPORT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun exportToJson_producesValidJson_withCorrectSchemaVersion() = runTest {
        stubDaoQueries()

        val json = repository.exportToJson()

        assertTrue("Export JSON should not be blank", json.isNotBlank())

        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<FlowStateExport>(json)
        assertEquals(
            "Schema version must match current DB version",
            FlowStateExport.CURRENT_SCHEMA_VERSION,
            parsed.schemaVersion
        )
    }

    @Test
    fun exportToJson_includesAllEntityTypes() = runTest {
        stubDaoQueries()

        val json = repository.exportToJson()
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<FlowStateExport>(json)

        assertTrue("Export should contain tasks", parsed.tasks.isNotEmpty())
        assertTrue("Export should contain subtasks", parsed.subTasks.isNotEmpty())
        assertTrue("Export should contain ideas", parsed.ideas.isNotEmpty())
        assertTrue("Export should contain checklists", parsed.checkLists.isNotEmpty())
        assertTrue("Export should contain checklist items", parsed.checkListItems.isNotEmpty())
        assertTrue("Export should contain habits", parsed.habits.isNotEmpty())
        assertTrue("Export should contain habit entries", parsed.habitEntries.isNotEmpty())
        assertTrue("Export should contain numeric entries", parsed.habitNumericEntries.isNotEmpty())
        assertTrue("Export should contain categories", parsed.categories.isNotEmpty())
    }

    @Test
    fun exportToJson_preservesTaskDataCorrectly() = runTest {
        stubDaoQueries()

        val json = repository.exportToJson()
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<FlowStateExport>(json)

        val task = parsed.tasks.first()
        assertEquals(1, task.id)
        assertEquals("Buy groceries", task.title)
        assertEquals("Milk and bread", task.description)
        assertEquals(false, task.isDone)
        assertEquals(0, task.position)
        assertEquals(1, task.priority)
        assertEquals(1700000000000L, task.dueDate)
    }

    @Test
    fun exportToJson_preservesSubTaskDataCorrectly() = runTest {
        stubDaoQueries()

        val json = repository.exportToJson()
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<FlowStateExport>(json)

        val subTask = parsed.subTasks.first()
        assertEquals("sub-001", subTask.id)
        assertEquals(1, subTask.taskId)
        assertEquals("Get milk", subTask.title)
    }

    @Test
    fun exportToJson_preservesHabitNumericEntry_withCompositeKey() = runTest {
        stubDaoQueries()

        val json = repository.exportToJson()
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<FlowStateExport>(json)

        val numericEntry = parsed.habitNumericEntries.first()
        assertEquals(2, numericEntry.habitId)
        assertEquals(19700L, numericEntry.epochDay)
        assertEquals(3.5f, numericEntry.value, 0.001f)
    }

    @Test
    fun exportToJson_handlesEmptyDatabase() = runTest {
        coEvery { taskDao.getAllTasksOnce() } returns emptyList()
        coEvery { ideaDao.getAllIdeasOnce() } returns emptyList()
        coEvery { checkListDao.getAllListsOnce() } returns emptyList()
        coEvery { habitDao.getAllHabitsOnce() } returns emptyList()
        coEvery { habitDao.getAllEntriesOnce() } returns emptyList()
        coEvery { habitDao.getAllNumericEntriesOnce() } returns emptyList()
        coEvery { categoryDao.getAllCategoriesOnce() } returns emptyList()

        val json = repository.exportToJson()
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<FlowStateExport>(json)

        assertEquals(FlowStateExport.CURRENT_SCHEMA_VERSION, parsed.schemaVersion)
        assertTrue(parsed.tasks.isEmpty())
        assertTrue(parsed.subTasks.isEmpty())
        assertTrue(parsed.ideas.isEmpty())
        assertTrue(parsed.checkLists.isEmpty())
        assertTrue(parsed.checkListItems.isEmpty())
        assertTrue(parsed.habits.isEmpty())
        assertTrue(parsed.habitEntries.isEmpty())
        assertTrue(parsed.habitNumericEntries.isEmpty())
        assertTrue(parsed.categories.isEmpty())
    }

    @Test
    fun exportToJson_readsFromAllDaos() = runTest {
        stubDaoQueries()

        repository.exportToJson()

        coVerify(exactly = 1) { taskDao.getAllTasksOnce() }
        coVerify(exactly = 1) { ideaDao.getAllIdeasOnce() }
        coVerify(exactly = 1) { checkListDao.getAllListsOnce() }
        coVerify(exactly = 1) { habitDao.getAllHabitsOnce() }
        coVerify(exactly = 1) { habitDao.getAllEntriesOnce() }
        coVerify(exactly = 1) { habitDao.getAllNumericEntriesOnce() }
        coVerify(exactly = 1) { categoryDao.getAllCategoriesOnce() }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RESTORE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun restoreFromJson_withValidJson_returnsSuccess() = runTest {
        stubDaoQueries()

        val json = repository.exportToJson()
        val result = repository.restoreFromJson(json)

        assertTrue("Restore should succeed", result is RestoreResult.Success)
    }

    @Test
    fun restoreFromJson_withValidJson_upsertsAllEntityTypes() = runTest {
        stubDaoQueries()
        val json = repository.exportToJson()

        repository.restoreFromJson(json)

        coVerify { taskDao.upsertTaskEntity(any()) }
        coVerify { taskDao.insertSubTasks(any()) }
        coVerify { ideaDao.upsertIdea(any()) }
        coVerify { checkListDao.upsertListEntity(any()) }
        coVerify { checkListDao.insertListItems(any()) }
        coVerify { habitDao.insertHabit(any()) }
        coVerify { habitDao.insertEntry(any()) }
        coVerify { habitDao.upsertNumericEntry(any()) }
        // "Work" already exists in the stubbed DAO, so the remap reuses its id
        // and never calls upsertCategory. See the remap tests for the "new
        // category" path that does invoke upsertCategory.
        coVerify(exactly = 0) { categoryDao.upsertCategory(any()) }
    }

    @Test
    fun restoreFromJson_upsertsCorrectEntityCount() = runTest {
        stubDaoQueries()
        val json = repository.exportToJson()

        repository.restoreFromJson(json)

        // 1 task
        coVerify(exactly = 1) { taskDao.upsertTaskEntity(match { it.title == "Buy groceries" }) }
        // 1 subtask (batch)
        coVerify(exactly = 1) { taskDao.insertSubTasks(match { it.size == 1 }) }
        // 1 idea
        coVerify(exactly = 1) { ideaDao.upsertIdea(match { it.title == "App idea" }) }
        // 1 checklist
        coVerify(exactly = 1) { checkListDao.upsertListEntity(match { it.title == "Packing list" }) }
        // 1 checklist item (batch)
        coVerify(exactly = 1) { checkListDao.insertListItems(match { it.size == 1 }) }
        // 1 habit
        coVerify(exactly = 1) { habitDao.insertHabit(match { it.name == "Meditation" }) }
        // 1 habit entry
        coVerify(exactly = 1) { habitDao.insertEntry(any()) }
        // 1 numeric entry
        coVerify(exactly = 1) { habitDao.upsertNumericEntry(any()) }
    }

    @Test
    fun restoreFromJson_withWrongSchemaVersion_returnsSchemaMismatch() = runTest {
        val invalidSchemaJson = buildJsonWithSchemaVersion(99)

        val result = repository.restoreFromJson(invalidSchemaJson)

        assertTrue("Should return Error", result is RestoreResult.Error)
        assertEquals(
            RestoreErrorType.SCHEMA_MISMATCH,
            (result as RestoreResult.Error).type
        )
    }

    @Test
    fun restoreFromJson_withMalformedJson_returnsError() = runTest {
        val malformedJson = "{ this is not valid JSON }}}"

        val result = repository.restoreFromJson(malformedJson)

        assertTrue("Should return Error", result is RestoreResult.Error)
    }

    @Test
    fun restoreFromJson_withEmptyString_returnsError() = runTest {
        val result = repository.restoreFromJson("")

        assertTrue("Should return Error", result is RestoreResult.Error)
    }

    @Test
    fun restoreFromJson_withRandomGarbage_returnsError() = runTest {
        val result = repository.restoreFromJson("not json at all")

        assertTrue("Should return Error", result is RestoreResult.Error)
    }

    @Test
    fun restoreFromJson_doesNotUpsert_whenSchemaVersionMismatch() = runTest {
        val invalidSchemaJson = buildJsonWithSchemaVersion(99)

        repository.restoreFromJson(invalidSchemaJson)

        coVerify(exactly = 0) { taskDao.upsertTaskEntity(any()) }
        coVerify(exactly = 0) { ideaDao.upsertIdea(any()) }
        coVerify(exactly = 0) { checkListDao.upsertListEntity(any()) }
        coVerify(exactly = 0) { habitDao.insertHabit(any()) }
        coVerify(exactly = 0) { categoryDao.upsertCategory(any()) }
    }

    @Test
    fun restoreFromJson_withEmptyEntities_returnsSuccess() = runTest {
        val emptyExport = FlowStateExport(
            schemaVersion = FlowStateExport.CURRENT_SCHEMA_VERSION,
            exportTimestamp = System.currentTimeMillis(),
            tasks = emptyList(),
            subTasks = emptyList(),
            ideas = emptyList(),
            checkLists = emptyList(),
            checkListItems = emptyList(),
            habits = emptyList(),
            habitEntries = emptyList(),
            habitNumericEntries = emptyList()
        )
        val json = Json.encodeToString(FlowStateExport.serializer(), emptyExport)

        val result = repository.restoreFromJson(json)

        assertTrue(
            "Empty but valid backup should restore successfully",
            result is RestoreResult.Success
        )
        coVerify(exactly = 0) { taskDao.insertSubTasks(any()) }
        coVerify(exactly = 0) { checkListDao.insertListItems(any()) }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ROUND-TRIP TEST
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun roundTrip_exportThenImport_preservesAllDataExactly() = runTest {
        stubDaoQueries()

        // Step 1: Export
        val json = repository.exportToJson()

        // Step 2: Parse the exported JSON and verify every field
        val lenientJson = Json { ignoreUnknownKeys = true }
        val exported = lenientJson.decodeFromString<FlowStateExport>(json)

        // -- Tasks --
        assertEquals(1, exported.tasks.size)
        val task = exported.tasks[0]
        assertEquals(sampleTask.id, task.id)
        assertEquals(sampleTask.title, task.title)
        assertEquals(sampleTask.description, task.description)
        assertEquals(sampleTask.isDone, task.isDone)
        assertEquals(sampleTask.position, task.position)
        assertEquals(sampleTask.priority, task.priority)
        assertEquals(sampleTask.dueDate, task.dueDate)
        assertEquals(sampleTask.completedAt, task.completedAt)
        assertEquals(sampleTask.reminderTime, task.reminderTime)

        // -- SubTasks --
        assertEquals(1, exported.subTasks.size)
        val subTask = exported.subTasks[0]
        assertEquals(sampleSubTask.id, subTask.id)
        assertEquals(sampleSubTask.taskId, subTask.taskId)
        assertEquals(sampleSubTask.title, subTask.title)
        assertEquals(sampleSubTask.description, subTask.description)
        assertEquals(sampleSubTask.isDone, subTask.isDone)
        assertEquals(sampleSubTask.priority, subTask.priority)
        assertEquals(sampleSubTask.dueDate, subTask.dueDate)
        assertEquals(sampleSubTask.position, subTask.position)
        assertEquals(sampleSubTask.completedAt, subTask.completedAt)
        assertEquals(sampleSubTask.reminderTime, subTask.reminderTime)

        // -- Ideas --
        assertEquals(1, exported.ideas.size)
        val idea = exported.ideas[0]
        assertEquals(sampleIdea.id, idea.id)
        assertEquals(sampleIdea.title, idea.title)
        assertEquals(sampleIdea.content, idea.content)
        assertEquals(sampleIdea.createdAt, idea.createdAt)
        assertEquals(sampleIdea.color, idea.color)
        assertEquals(sampleIdea.position, idea.position)

        // -- CheckLists --
        assertEquals(1, exported.checkLists.size)
        val checklist = exported.checkLists[0]
        assertEquals(sampleCheckList.id, checklist.id)
        assertEquals(sampleCheckList.title, checklist.title)
        assertEquals(sampleCheckList.color, checklist.color)
        assertEquals(sampleCheckList.position, checklist.position)

        // -- CheckList Items --
        assertEquals(1, exported.checkListItems.size)
        val item = exported.checkListItems[0]
        assertEquals(sampleCheckListItem.id, item.id)
        assertEquals(sampleCheckListItem.listId, item.listId)
        assertEquals(sampleCheckListItem.text, item.text)
        assertEquals(sampleCheckListItem.isDone, item.isDone)
        assertEquals(sampleCheckListItem.position, item.position)

        // -- Habits --
        assertEquals(1, exported.habits.size)
        val habit = exported.habits[0]
        assertEquals(sampleHabit.id, habit.id)
        assertEquals(sampleHabit.name, habit.name)
        assertEquals(sampleHabit.iconName, habit.iconName)
        assertEquals(sampleHabit.colorArgb, habit.colorArgb)
        assertEquals(sampleHabit.createdAt, habit.createdAt)
        assertEquals(sampleHabit.habitType, habit.habitType)
        assertEquals(sampleHabit.unit, habit.unit)
        assertEquals(sampleHabit.targetValue, habit.targetValue)
        assertEquals(sampleHabit.step, habit.step, 0.001f)
        assertEquals(sampleHabit.position, habit.position)
        assertEquals(HabitConverters.ALL_DAYS, habit.scheduledDays)

        // -- Habit Entries --
        assertEquals(1, exported.habitEntries.size)
        val entry = exported.habitEntries[0]
        assertEquals(sampleHabitEntry.id, entry.id)
        assertEquals(sampleHabitEntry.habitId, entry.habitId)
        assertEquals(sampleHabitEntry.completedAt, entry.completedAt)

        // -- Habit Numeric Entries (composite key) --
        assertEquals(1, exported.habitNumericEntries.size)
        val numEntry = exported.habitNumericEntries[0]
        assertEquals(sampleNumericEntry.habitId, numEntry.habitId)
        assertEquals(sampleNumericEntry.epochDay, numEntry.epochDay)
        assertEquals(sampleNumericEntry.value, numEntry.value, 0.001f)

        // Step 3: Restore the exported JSON and verify success
        val restoreResult = repository.restoreFromJson(json)
        assertTrue(
            "Round-trip restore should succeed",
            restoreResult is RestoreResult.Success
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ADDITIVE RESTORE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun restoreFromJson_usesUpsertStrategy_doesNotDeleteExistingData() = runTest {
        stubDaoQueries()
        val json = repository.exportToJson()

        repository.restoreFromJson(json)

        // Verify that only upsert/insert methods are called — never delete
        coVerify(exactly = 0) { taskDao.deleteTaskEntity(any()) }
        coVerify(exactly = 0) { ideaDao.deleteIdea(any()) }
        coVerify(exactly = 0) { checkListDao.deleteListEntity(any()) }
        coVerify(exactly = 0) { habitDao.deleteHabit(any()) }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FORWARD COMPATIBILITY (ignore unknown keys)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun restoreFromJson_withExtraUnknownFields_stillSucceeds() = runTest {
        val jsonWithExtraField = """
        {
            "schemaVersion": ${FlowStateExport.CURRENT_SCHEMA_VERSION},
            "exportTimestamp": 1700000000000,
            "tasks": [],
            "subTasks": [],
            "ideas": [],
            "checkLists": [],
            "checkListItems": [],
            "habits": [],
            "habitEntries": [],
            "habitNumericEntries": [],
            "futureField": "this should be ignored"
        }
        """.trimIndent()

        val result = repository.restoreFromJson(jsonWithExtraField)

        assertTrue(
            "Restore should succeed even with unknown fields",
            result is RestoreResult.Success
        )
    }

    @Test
    fun restoreFromJson_withUnknownFieldsInTask_stillSucceeds() = runTest {
        val jsonWithExtraTaskField = """
        {
            "schemaVersion": ${FlowStateExport.CURRENT_SCHEMA_VERSION},
            "exportTimestamp": 1700000000000,
            "tasks": [
                {
                    "id": 1,
                    "title": "Task with future field",
                    "description": "",
                    "isDone": false,
                    "position": 0,
                    "priority": 0,
                    "dueDate": null,
                    "completedAt": null,
                    "reminderTime": null,
                    "futureColumn": 42
                }
            ],
            "subTasks": [],
            "ideas": [],
            "checkLists": [],
            "checkListItems": [],
            "habits": [],
            "habitEntries": [],
            "habitNumericEntries": []
        }
        """.trimIndent()

        val result = repository.restoreFromJson(jsonWithExtraTaskField)

        assertTrue(
            "Restore should succeed even with unknown fields in entities",
            result is RestoreResult.Success
        )
        coVerify(exactly = 1) { taskDao.upsertTaskEntity(any()) }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CATEGORY REMAP TESTS (cross-device restore safety)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun restoreFromJson_withOldBackupMissingCategoriesField_stillSucceeds() = runTest {
        // Simulates a backup produced by a pre-v18 app version: the JSON has
        // no "categories" array and no "categoryId" on tasks/ideas/checklists.
        // The lenient parser + default values must fill the gaps so the restore
        // completes without errors. Items end up in General (Category.GENERAL_ID).
        val oldBackupJson = """
        {
            "schemaVersion": ${FlowStateExport.CURRENT_SCHEMA_VERSION - 1},
            "exportTimestamp": 1700000000000,
            "tasks": [
                {
                    "id": 1, "title": "Old task", "description": "",
                    "isDone": false, "position": 0, "priority": 0,
                    "dueDate": null, "completedAt": null, "reminderTime": null
                }
            ],
            "subTasks": [],
            "ideas": [],
            "checkLists": [],
            "checkListItems": [],
            "habits": [],
            "habitEntries": [],
            "habitNumericEntries": []
        }
        """.trimIndent()

        val result = repository.restoreFromJson(oldBackupJson)

        assertTrue(
            "Old backup without categories must restore successfully",
            result is RestoreResult.Success
        )
        coVerify(exactly = 0) { categoryDao.upsertCategory(any()) }
        coVerify(exactly = 1) { taskDao.upsertTaskEntity(match { it.categoryId == Category.GENERAL_ID }) }
    }

    @Test
    fun restoreFromJson_remapsCategoryByIdWhenNameMatchesExisting() = runTest {
        // The destination DB already has a "Work" category with id = 5.
        // The backup has a "Work" category with id = 1. The restore must NOT
        // insert a duplicate; it must reuse id = 5 so tasks filed under
        // "Work" in the backup end up under the existing "Work" category.
        coEvery { categoryDao.getAllCategoriesOnce() } returns listOf(
            CategoryEntity(id = 5, name = "Work", position = 0)
        )
        coEvery { taskDao.getAllTasksOnce() } returns emptyList()
        coEvery { ideaDao.getAllIdeasOnce() } returns emptyList()
        coEvery { checkListDao.getAllListsOnce() } returns emptyList()
        coEvery { habitDao.getAllHabitsOnce() } returns emptyList()
        coEvery { habitDao.getAllEntriesOnce() } returns emptyList()
        coEvery { habitDao.getAllNumericEntriesOnce() } returns emptyList()

        val json = buildJsonWithCategoryAndTask(
            categoryId = 1,
            categoryName = "Work",
            taskTitle = "Restore me"
        )

        repository.restoreFromJson(json)

        // No new category should be inserted — "Work" already exists
        coVerify(exactly = 0) { categoryDao.upsertCategory(any()) }
        // The task must be restored with the REMAPPED categoryId (5, not 1)
        coVerify(exactly = 1) {
            taskDao.upsertTaskEntity(match { it.title == "Restore me" && it.categoryId == 5 })
        }
    }

    @Test
    fun restoreFromJson_insertsNewCategoryWhenNameDoesNotMatch() = runTest {
        // Destination DB has no categories at all. The backup has "Work" with
        // id = 1. The restore must insert "Work" as a new row (autoGenerate)
        // and the task must end up with the newly-assigned id (stubbed to 100L).
        coEvery { categoryDao.getAllCategoriesOnce() } returns emptyList()
        coEvery { taskDao.getAllTasksOnce() } returns emptyList()
        coEvery { ideaDao.getAllIdeasOnce() } returns emptyList()
        coEvery { checkListDao.getAllListsOnce() } returns emptyList()
        coEvery { habitDao.getAllHabitsOnce() } returns emptyList()
        coEvery { habitDao.getAllEntriesOnce() } returns emptyList()
        coEvery { habitDao.getAllNumericEntriesOnce() } returns emptyList()
        coEvery { categoryDao.upsertCategory(any()) } returns 100L

        val json = buildJsonWithCategoryAndTask(
            categoryId = 1,
            categoryName = "Work",
            taskTitle = "Restore me"
        )

        repository.restoreFromJson(json)

        // New category inserted with id = 0 (autoGenerate), not the backup id
        coVerify(exactly = 1) {
            categoryDao.upsertCategory(match { it.id == 0 && it.name == "Work" })
        }
        // Task remapped to the freshly-assigned id (100), not the backup id (1)
        coVerify(exactly = 1) {
            taskDao.upsertTaskEntity(match { it.categoryId == 100 })
        }
    }

    @Test
    fun restoreFromJson_remapsIdeaAndChecklistCategoryIds() = runTest {
        // The remap must also apply to ideas and checklists, not just tasks.
        coEvery { categoryDao.getAllCategoriesOnce() } returns listOf(
            CategoryEntity(id = 7, name = "Personal", position = 0)
        )
        coEvery { taskDao.getAllTasksOnce() } returns emptyList()
        coEvery { ideaDao.getAllIdeasOnce() } returns emptyList()
        coEvery { checkListDao.getAllListsOnce() } returns emptyList()
        coEvery { habitDao.getAllHabitsOnce() } returns emptyList()
        coEvery { habitDao.getAllEntriesOnce() } returns emptyList()
        coEvery { habitDao.getAllNumericEntriesOnce() } returns emptyList()

        val export = FlowStateExport(
            schemaVersion = FlowStateExport.CURRENT_SCHEMA_VERSION,
            exportTimestamp = 0,
            tasks = emptyList(),
            subTasks = emptyList(),
            ideas = listOf(
                com.markel.flowstate.core.data.backup.IdeaSchema(
                    id = 1, title = "Idea", content = "",
                    createdAt = 0L, color = 0L, position = 0, categoryId = 99
                )
            ),
            checkLists = listOf(
                com.markel.flowstate.core.data.backup.CheckListSchema(
                    id = 1, title = "List", color = 0L, position = 0, categoryId = 99
                )
            ),
            checkListItems = emptyList(),
            habits = emptyList(),
            habitEntries = emptyList(),
            habitNumericEntries = emptyList(),
            categories = listOf(
                com.markel.flowstate.core.data.backup.CategorySchema(
                    id = 99, name = "Personal", position = 0
                )
            )
        )
        val json = Json.encodeToString(FlowStateExport.serializer(), export)

        repository.restoreFromJson(json)

        // The backup id 99 must be remapped to the existing id 7
        coVerify(exactly = 1) { ideaDao.upsertIdea(match { it.categoryId == 7 }) }
        coVerify(exactly = 1) { checkListDao.upsertListEntity(match { it.categoryId == 7 }) }
    }

    @Test
    fun restoreFromJson_orphanCategoryId_fallsBackToGeneral() = runTest {
        // Edge case: the backup references a categoryId that is NOT in the
        // categories array (e.g. the category was deleted between export and
        // restore). The remap has no entry for it, so the task must end up
        // in General (categoryId = Category.GENERAL_ID) rather than pointing to a non-existent row.
        coEvery { categoryDao.getAllCategoriesOnce() } returns emptyList()
        coEvery { taskDao.getAllTasksOnce() } returns emptyList()
        coEvery { ideaDao.getAllIdeasOnce() } returns emptyList()
        coEvery { checkListDao.getAllListsOnce() } returns emptyList()
        coEvery { habitDao.getAllHabitsOnce() } returns emptyList()
        coEvery { habitDao.getAllEntriesOnce() } returns emptyList()
        coEvery { habitDao.getAllNumericEntriesOnce() } returns emptyList()

        val export = FlowStateExport(
            schemaVersion = FlowStateExport.CURRENT_SCHEMA_VERSION,
            exportTimestamp = 0,
            tasks = listOf(
                com.markel.flowstate.core.data.backup.TaskSchema(
                    id = 1, title = "Orphan", description = "",
                    isDone = false, position = 0, priority = 0,
                    dueDate = null, completedAt = null, reminderTime = null,
                    categoryId = 999 // NOT present in the categories array
                )
            ),
            subTasks = emptyList(),
            ideas = emptyList(),
            checkLists = emptyList(),
            checkListItems = emptyList(),
            habits = emptyList(),
            habitEntries = emptyList(),
            habitNumericEntries = emptyList(),
            categories = emptyList()
        )
        val json = Json.encodeToString(FlowStateExport.serializer(), export)

        val result = repository.restoreFromJson(json)

        assertTrue(result is RestoreResult.Success)
        // No category insert attempted (empty list)
        coVerify(exactly = 0) { categoryDao.upsertCategory(any()) }
        // Task restored with categoryId = Category.GENERAL_ID (General), NOT 999
        coVerify(exactly = 1) {
            taskDao.upsertTaskEntity(match { it.title == "Orphan" && it.categoryId == Category.GENERAL_ID })
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds a JSON string with the given [schemaVersion] and empty entity lists.
     * Useful for testing schema version validation without constructing full objects.
     */
    private fun buildJsonWithSchemaVersion(schemaVersion: Int): String {
        val export = FlowStateExport(
            schemaVersion = schemaVersion,
            exportTimestamp = System.currentTimeMillis(),
            tasks = emptyList(),
            subTasks = emptyList(),
            ideas = emptyList(),
            checkLists = emptyList(),
            checkListItems = emptyList(),
            habits = emptyList(),
            habitEntries = emptyList(),
            habitNumericEntries = emptyList()
        )
        return Json.encodeToString(FlowStateExport.serializer(), export)
    }


    /**
     * Builds a JSON backup containing one category and one task that points to
     * that category. Used by the remap tests to verify how the restore maps the
     * backup category id onto the destination DB.
     */
    private fun buildJsonWithCategoryAndTask(
        categoryId: Int,
        categoryName: String,
        taskTitle: String
    ): String {
        val export = FlowStateExport(
            schemaVersion = FlowStateExport.CURRENT_SCHEMA_VERSION,
            exportTimestamp = 0,
            tasks = listOf(
                com.markel.flowstate.core.data.backup.TaskSchema(
                    id = 1, title = taskTitle, description = "",
                    isDone = false, position = 0, priority = 0,
                    dueDate = null, completedAt = null, reminderTime = null,
                    categoryId = categoryId
                )
            ),
            subTasks = emptyList(),
            ideas = emptyList(),
            checkLists = emptyList(),
            checkListItems = emptyList(),
            habits = emptyList(),
            habitEntries = emptyList(),
            habitNumericEntries = emptyList(),
            categories = listOf(
                com.markel.flowstate.core.data.backup.CategorySchema(
                    id = categoryId, name = categoryName, position = 0
                )
            )
        )
        return Json.encodeToString(FlowStateExport.serializer(), export)
    }

}
